package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;

/**
 * @author janinko
 */
public class GhprbBuilds {
	private static final Logger logger = Logger.getLogger(GhprbBuilds.class.getName());
	private GhprbTrigger trigger;
	private GhprbRepository repo;

	public GhprbBuilds(GhprbTrigger trigger, GhprbRepository repo){
		this.trigger = trigger;
		this.repo = repo;
	}

	public String build(GhprbPullRequest pr) {
		StringBuilder sb = new StringBuilder();
		if(cancelBuild(pr.getId())){
			sb.append("Previous build stopped. ");
		}

		sb.append(trigger.getMsgStarted());

		GhprbCause cause = new GhprbCause(pr.getHead(), pr.getId(), pr.isMergeable(), pr.getTarget(), pr.getAuthorEmail(), pr.getTitle());

		QueueTaskFuture<?> build = trigger.startJob(cause);
		if(build == null){
			logger.log(Level.SEVERE, "Job did not start");
		}
		return sb.toString();
	}

	private boolean cancelBuild(int id) {
		return false;
	}

	private GhprbCause getCause(AbstractBuild build){
		Cause cause = build.getCause(GhprbCause.class);
		if(cause == null || (!(cause instanceof GhprbCause))) return null;
		return (GhprbCause) cause;
	}

	public void onStarted(AbstractBuild build) {
		GhprbCause c = getCause(build);
		if(c == null) return;

		repo.createCommitStatus(build, GHCommitState.PENDING, trigger.getMsgStarted(), c.getPullID());
		try {
			build.setDescription("<a title=\"" + c.getTitle() + "\" href=\"" + repo.getRepoUrl()+"/pull/"+c.getPullID()+"\">PR #"+c.getPullID()+"</a>: " + c.getAbbreviatedTitle());
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Can't update build description", ex);
		}
	}

	public void onCompleted(AbstractBuild build) {
		GhprbCause c = getCause(build);
		if(c == null) return;

		GHCommitState state;
		if (build.getResult() == Result.SUCCESS) {
			state = GHCommitState.SUCCESS;
		} else if (build.getResult() == Result.UNSTABLE){
			state = GHCommitState.valueOf(GhprbTrigger.getDscp().getUnstableAs());
		} else {
			state = GHCommitState.FAILURE;
		}
		String msg;
		if (state == GHCommitState.SUCCESS) {
			msg = trigger.getMsgSuccess();
		} else {
			msg = trigger.getMsgFailure();
		}
		repo.createCommitStatus(build, state, msg, c.getPullID());

		String publishedURL = GhprbTrigger.getDscp().getPublishedURL();
		if (publishedURL != null && !publishedURL.isEmpty()) {
			repo.addComment(c.getPullID(), msg + "\nRefer to this link for build results: " + publishedURL + build.getUrl());
		}

		// close failed pull request automatically
		if (state == GHCommitState.FAILURE && trigger.isAutoCloseFailedPullRequests()) {

			try {
				GHPullRequest pr = repo.getPullRequest(c.getPullID());

				if (pr.getState().equals(GHIssueState.OPEN)) {
					repo.closePullRequest(c.getPullID());
				}
			} catch (IOException ex) {
				logger.log(Level.SEVERE, "Can't close pull request", ex);
			}
		}
	}
}
