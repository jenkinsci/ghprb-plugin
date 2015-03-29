package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.git.util.BuildData;

import org.apache.commons.io.FileUtils;

import org.jenkinsci.plugins.ghprb.manager.GhprbBuildManager;
import org.jenkinsci.plugins.ghprb.manager.configuration.JobConfiguration;
import org.jenkinsci.plugins.ghprb.manager.factory.GhprbBuildManagerFactoryUtil;

import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author janinko
 */
public class GhprbBuilds {
    private static final Logger logger = Logger.getLogger(GhprbBuilds.class.getName());
    private final GhprbTrigger trigger;
    private final GhprbRepository repo;

    public GhprbBuilds(GhprbTrigger trigger, GhprbRepository repo) {
        this.trigger = trigger;
        this.repo = repo;
    }

    public String build(GhprbPullRequest pr, GHUser triggerSender, String commentBody) {
        StringBuilder sb = new StringBuilder();
        if (cancelBuild(pr.getId())) {
            sb.append("Previous build stopped.");
        }

        if (pr.isMergeable()) {
            sb.append(" Merge build triggered.");
        } else {
            sb.append(" Build triggered.");
        }

        GhprbCause cause = new GhprbCause(pr.getHead(), pr.getId(),
                pr.isMergeable(), pr.getTarget(), pr.getSource(),
                pr.getAuthorEmail(), pr.getTitle(), pr.getUrl(),
                triggerSender, commentBody, pr.getCommitAuthor());

        QueueTaskFuture<?> build = trigger.startJob(cause, repo);
        if (build == null) {
            logger.log(Level.SEVERE, "Job did not start");
        }
        return sb.toString();
    }

    private boolean cancelBuild(int id) {
        return false;
    }

    private GhprbCause getCause(AbstractBuild<?,?> build) {
        Cause cause = build.getCause(GhprbCause.class);
        if (cause == null || (!(cause instanceof GhprbCause))) return null;
        return (GhprbCause) cause;
    }

    public void onStarted(AbstractBuild<?,?> build, PrintStream logger) {
        GhprbCause c = getCause(build);
        if (c == null) {
            return;
        }

        if ( !trigger.getSkipCommitStatus() ) {
            repo.createCommitStatus(build, GHCommitState.PENDING, (c.isMerged() ? "Merged build started." : "Build started."), c.getPullID(), trigger.getCommitStatusContext(), logger);
        }
        try {
            build.setDescription("<a title=\"" + c.getTitle() + "\" href=\"" + c.getUrl() + "\">PR #" + c.getPullID() + "</a>: " + c.getAbbreviatedTitle());
        } catch (IOException ex) {
            logger.println("Can't update build description");
            ex.printStackTrace(logger);
        }
    }

    public void onCompleted(AbstractBuild<?,?> build, TaskListener listener) {
        GhprbCause c = getCause(build);
        if (c == null) {
            return;
        }

        // remove the BuildData action that we may have added earlier to avoid
        // having two of them, and because the one we added isn't correct
        // @see GhprbTrigger
        BuildData fakeOne = null;
        for (BuildData data : build.getActions(BuildData.class)) {
            if (data.getLastBuiltRevision() != null && !data.getLastBuiltRevision().getSha1String().equals(c.getCommit())) {
                fakeOne = data;
                break;
            }
        }
        if (fakeOne != null) {
            build.getActions().remove(fakeOne);
        }

        GHCommitState state;
        if (build.getResult() == Result.SUCCESS) {
            state = GHCommitState.SUCCESS;
        } else if (build.getResult() == Result.UNSTABLE) {
            state = GHCommitState.valueOf(GhprbTrigger.getDscp().getUnstableAs());
        } else {
            state = GHCommitState.FAILURE;
        }

        if ( !trigger.getSkipCommitStatus() ) {
            repo.createCommitStatus(build, state, (c.isMerged() ? "Merged build finished." : "Build finished."), c.getPullID(), trigger.getCommitStatusContext(), listener.getLogger());
        }

        StringBuilder msg = new StringBuilder();

        String publishedURL = GhprbTrigger.getDscp().getPublishedURL();
        if (publishedURL != null && !publishedURL.isEmpty()) {
            String commentFilePath = trigger.getCommentFilePath();

            if (commentFilePath != null && !commentFilePath.isEmpty()) {
                try {
                    String scriptFilePathResolved = Ghprb.replaceMacros(build, commentFilePath);

                    String content = FileUtils.readFileToString(new File(scriptFilePathResolved));
                    msg.append("Build comment file: \n--------------\n");
                    msg.append(content);
                    msg.append("\n--------------\n");
                } catch (IOException e) {
                    msg.append("\n!!! Couldn't read commit file !!!\n");
                    listener.getLogger().println("Couldn't read comment file");
                    e.printStackTrace(listener.getLogger());
                }
            }

            msg.append("\nRefer to this link for build results (access rights to CI server needed): \n");
            msg.append(generateCustomizedMessage(build));

            int numLines = GhprbTrigger.getDscp().getlogExcerptLines();
            if (state != GHCommitState.SUCCESS && numLines > 0) {
                // on failure, append an excerpt of the build log
                try {
                    // wrap log in "code" markdown
                    msg.append("\n\n**Build Log**\n*last ").append(numLines).append(" lines*\n");
                    msg.append("\n ```\n");
                    List<String> log = build.getLog(numLines);
                    for (String line : log) {
                        msg.append(line).append('\n');
                    }
                    msg.append("```\n");
                } catch (IOException ex) {
                    listener.getLogger().println("Can't add log excerpt to commit comments");
                    ex.printStackTrace(listener.getLogger());
                }
            }


            String buildMessage = null;
            if (state == GHCommitState.SUCCESS) {
                if (trigger.getMsgSuccess() != null && !trigger.getMsgSuccess().isEmpty()) {
                    buildMessage = trigger.getMsgSuccess();
                } else if (GhprbTrigger.getDscp().getMsgSuccess(build) != null && !GhprbTrigger.getDscp().getMsgSuccess(build).isEmpty()) {
                    buildMessage = GhprbTrigger.getDscp().getMsgSuccess(build);
                }
            } else if (state == GHCommitState.FAILURE) {
                if (trigger.getMsgFailure() != null && !trigger.getMsgFailure().isEmpty()) {
                    buildMessage = trigger.getMsgFailure();
                } else if (GhprbTrigger.getDscp().getMsgFailure(build) != null && !GhprbTrigger.getDscp().getMsgFailure(build).isEmpty()) {
                    buildMessage = GhprbTrigger.getDscp().getMsgFailure(build);
                }
            }
            // Only Append the build's custom message if it has been set.
            if (buildMessage != null && !buildMessage.isEmpty()) {
                // When the msg is not empty, append a newline first, to seperate it from the rest of the String
                if (!"".equals(msg.toString())) {
                    msg.append("\n");
                }
                msg.append(buildMessage);
            }

            if (msg.length() > 0) {
                listener.getLogger().println(msg);
                repo.addComment(c.getPullID(), msg.toString(), build, listener);
            }

            // close failed pull request automatically
            if (state == GHCommitState.FAILURE && trigger.isAutoCloseFailedPullRequests()) {

                try {
                    GHPullRequest pr = repo.getPullRequest(c.getPullID());

                    if (pr.getState().equals(GHIssueState.OPEN)) {
                        repo.closePullRequest(c.getPullID());
                    }
                } catch (IOException ex) {
                    listener.getLogger().println("Can't close pull request");
                    ex.printStackTrace(listener.getLogger());
                }
            }
        }
    }

    private String generateCustomizedMessage(AbstractBuild<?, ?> build) {
        JobConfiguration jobConfiguration =
            JobConfiguration.builder()
                .printStackTrace(trigger.isDisplayBuildErrorsOnDownstreamBuilds())
                .build();

        GhprbBuildManager buildManager =
            GhprbBuildManagerFactoryUtil.getBuildManager(build, jobConfiguration);

        StringBuilder sb = new StringBuilder();

        sb.append(buildManager.calculateBuildUrl());

        if (build.getResult() != Result.SUCCESS) {
            sb.append(
                buildManager.getTestResults());
        }

        return sb.toString();
    }
}
