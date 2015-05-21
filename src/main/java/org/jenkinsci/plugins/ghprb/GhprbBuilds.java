package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.git.util.BuildData;

import org.jenkinsci.plugins.ghprb.extensions.GhprbCommentAppender;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;

import java.io.IOException;
import java.io.PrintStream;
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

        sb.append(" Build triggered.");

        if (pr.isMergeable()) {
            sb.append(" sha1 is merged.");
        } else {
            sb.append(" sha1 is original commit.");
        }

        GhprbCause cause = new GhprbCause(
                pr.getHead(), 
                pr.getId(), 
                pr.isMergeable(), 
                pr.getTarget(), 
                pr.getSource(), 
                pr.getAuthorEmail(), 
                pr.getTitle(), 
                pr.getUrl(), 
                triggerSender, 
                commentBody,
                pr.getCommitAuthor());

        QueueTaskFuture<?> build = trigger.startJob(cause, repo);
        if (build == null) {
            logger.log(Level.SEVERE, "Job did not start");
        }
        return sb.toString();
    }

    private boolean cancelBuild(int id) {
        return false;
    }

    private GhprbCause getCause(AbstractBuild<?, ?> build) {
        Cause cause = build.getCause(GhprbCause.class);
        if (cause == null || (!(cause instanceof GhprbCause))) {
            return null;
        }
        return (GhprbCause) cause;
    }

    public void onStarted(AbstractBuild<?, ?> build, PrintStream logger) {
        GhprbCause c = getCause(build);
        if (c == null) {
            return;
        }

        repo.createCommitStatus(build, GHCommitState.PENDING, 
                (c.isMerged() ? "Build started, sha1 is merged" : "Build started, sha1 is original commit."), c.getPullID(),
                trigger.getCommitStatusContext(), logger);
        try {
            build.setDescription("<a title=\"" + c.getTitle() + "\" href=\"" + c.getUrl() + 
                    "\">PR #" + c.getPullID() + "</a>: " + c.getAbbreviatedTitle());
        } catch (IOException ex) {
            logger.println("Can't update build description");
            ex.printStackTrace(logger);
        }
    }

    public void onCompleted(AbstractBuild<?, ?> build, TaskListener listener) {
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
        state = Ghprb.getState(build);
        repo.createCommitStatus(build, state, "Build finished.", c.getPullID(), trigger.getCommitStatusContext(), listener.getLogger());

        buildResultMessage(build, listener, state, c);
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
    
    private void buildResultMessage(AbstractBuild<?, ?> build, TaskListener listener, GHCommitState state, GhprbCause c) {
        StringBuilder msg = new StringBuilder();
        
        for (GhprbExtension ext : Ghprb.getJobExtensions(trigger, GhprbCommentAppender.class)){
            if (ext instanceof GhprbCommentAppender) {
                msg.append(((GhprbCommentAppender) ext).postBuildComment(build, listener));
            }
        }
        
        if (msg.length() > 0) {
            listener.getLogger().println(msg);
            repo.addComment(c.getPullID(), msg.toString(), build, listener);
        }
    }

}
