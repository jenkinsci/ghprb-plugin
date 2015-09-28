package org.jenkinsci.plugins.ghprb;

import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.git.util.BuildData;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommentAppender;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommitStatus;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommitStatusException;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
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

    public void build(GhprbPullRequest pr, GHUser triggerSender, String commentBody) {

        GhprbCause cause = new GhprbCause(pr.getHead(), 
                pr.getId(), 
                pr.isMergeable(), 
                pr.getTarget(), 
                pr.getSource(), 
                pr.getAuthorEmail(), 
                pr.getTitle(), 
                pr.getUrl(), 
                triggerSender, 
                commentBody,
                pr.getCommitAuthor(), 
                pr.getPullRequestAuthor(), 
                pr.getDescription(), 
                pr.getAuthorRepoGitUrl());

        for (GhprbExtension ext : Ghprb.getJobExtensions(trigger, GhprbCommitStatus.class)) {
            if (ext instanceof GhprbCommitStatus) {
                try {
                    ((GhprbCommitStatus) ext).onBuildTriggered(trigger.getActualProject(), pr.getHead(), pr.isMergeable(), pr.getId(), repo.getGitHubRepo());
                } catch (GhprbCommitStatusException e) {
                    repo.commentOnFailure(null, null, e);
                }
            }
        }
        QueueTaskFuture<?> build = trigger.startJob(cause, repo);
        if (build == null) {
            logger.log(Level.SEVERE, "Job did not start");
        }
    }
    
    public void onStarted(AbstractBuild<?, ?> build, TaskListener listener) {
        PrintStream logger = listener.getLogger();
        GhprbCause c = Ghprb.getCause(build);
        if (c == null) {
            return;
        }

        GhprbTrigger trigger = Ghprb.extractTrigger(build);

        try {
            GHPullRequest pr = trigger.getRepository().getPullRequest(c.getPullID());
            int counter = 0;
            // If the PR is being resolved by GitHub then getMergeable will return null
            Boolean isMergeable = pr.getMergeable();
            Boolean isMerged = pr.isMerged();
            // Not sure if isMerged can return null, but adding if just in case
            if (isMerged == null) {
                isMerged = false;
            }
            while (isMergeable == null && !isMerged && counter++ < 60) {
                Thread.sleep(1000);
                isMergeable = pr.getMergeable();
                isMerged = pr.isMerged();
                if (isMerged == null) {
                    isMerged = false;
                }
            }

            if (isMerged) {
                logger.println("PR has already been merged, builds using the merged sha1 will fail!!!");
            } else if (isMergeable == null) {
                logger.println("PR merge status couldn't be retrieved, maybe GitHub hasn't settled yet");
            } else if (isMergeable != c.isMerged()) {
                logger.println("!!! PR mergeability status has changed !!!  ");
                if (isMergeable) {
                    logger.println("PR now has NO merge conflicts");
                } else if (!isMergeable) {
                    logger.println("PR now has merge conflicts!");
                }
            }

        } catch (Exception e) {
            logger.print("Unable to query GitHub for status of PullRequest");
            e.printStackTrace(logger);
        }

        for (GhprbExtension ext : Ghprb.getJobExtensions(trigger, GhprbCommitStatus.class)) {
            if (ext instanceof GhprbCommitStatus) {
                try {
                    ((GhprbCommitStatus) ext).onBuildStart(build, listener, repo.getGitHubRepo());
                } catch (GhprbCommitStatusException e) {
                    repo.commentOnFailure(build, listener, e);
                }
            }
        }

        try {
            String template = trigger.getBuildDescTemplate();
            if (StringUtils.isEmpty(template)) {
                template = "<a title=\"$title\" href=\"$url\">PR #$pullId</a>: $abbrTitle";
            }
            Map<String, String> vars = getVariables(c);
            template = Util.replaceMacro(template, vars);
            template = Ghprb.replaceMacros(build, listener, template);
            build.setDescription(template);
        } catch (IOException ex) {
            logger.print("Can't update build description");
            ex.printStackTrace(logger);
        }
    }

    public Map<String, String> getVariables(GhprbCause c) {
        Map<String, String> vars = new HashMap<String, String>();
        vars.put("title", c.getTitle());
        if (c.getUrl() != null) {
            vars.put("url", c.getUrl().toString());
        } else {
            vars.put("url", "");
        }
        vars.put("pullId", Integer.toString(c.getPullID()));
        vars.put("abbrTitle", c.getAbbreviatedTitle());
        return vars;
    }

    public void onCompleted(AbstractBuild<?, ?> build, TaskListener listener) {
        GhprbCause c = Ghprb.getCause(build);
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

        for (GhprbExtension ext : Ghprb.getJobExtensions(trigger, GhprbCommitStatus.class)) {
            if (ext instanceof GhprbCommitStatus) {
                try {
                    ((GhprbCommitStatus) ext).onBuildComplete(build, listener, repo.getGitHubRepo());
                } catch (GhprbCommitStatusException e) {
                    repo.commentOnFailure(build, listener, e);
                }
            }
        }

        GHCommitState state;
        state = Ghprb.getState(build);

        commentOnBuildResult(build, listener, state, c);
        // close failed pull request automatically
        if (state == GHCommitState.FAILURE && trigger.isAutoCloseFailedPullRequests()) {
            closeFailedRequest(listener, c);
        }
    }

    private void closeFailedRequest(TaskListener listener, GhprbCause c) {
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

    private void commentOnBuildResult(AbstractBuild<?, ?> build, TaskListener listener, GHCommitState state, GhprbCause c) {
        StringBuilder msg = new StringBuilder();

        for (GhprbExtension ext : Ghprb.getJobExtensions(trigger, GhprbCommentAppender.class)) {
            if (ext instanceof GhprbCommentAppender) {
                msg.append(((GhprbCommentAppender) ext).postBuildComment(build, listener));
            }
        }

        if (msg.length() > 0) {
            listener.getLogger().println(msg);
            repo.addComment(c.getPullID(), msg.toString(), build, listener);
        }
    }

    public void onEnvironmentSetup(@SuppressWarnings("rawtypes") AbstractBuild build, Launcher launcher, BuildListener listener) {
        GhprbCause c = Ghprb.getCause(build);
        if (c == null) {
            return;
        }

        logger.log(Level.FINE, "Job: " + build.getFullDisplayName() + " Attempting to send GitHub commit status");

        for (GhprbExtension ext : Ghprb.getJobExtensions(trigger, GhprbCommitStatus.class)) {
            if (ext instanceof GhprbCommitStatus) {
                try {
                    ((GhprbCommitStatus) ext).onEnvironmentSetup(build, listener, repo.getGitHubRepo());
                } catch (GhprbCommitStatusException e) {
                    repo.commentOnFailure(build, listener, e);
                }
            }
        }
    }

}
