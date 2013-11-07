package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import java.io.IOException;
import java.util.List;
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

		if(pr.isMergeable()){
			sb.append("Merged build triggered. ");
		}else{
			sb.append("Build triggered. ");
		}

		GhprbCause cause = new GhprbCause(pr.getHead(), pr.getId(), pr.isMergeable(), pr.getTarget(), pr.getAuthorEmail(), pr.getTitle(), this.repo.getName());

		QueueTaskFuture<?> build = trigger.startJob(cause);
		if(build == null){
			logger.log(Level.SEVERE, "Job did not start");
		}
		return sb.toString();
	}

	private boolean cancelBuild(int id) {
		Boolean cancelled = false;
		Queue q = Queue.getInstance();
		for (Queue.Item build : q.getItems()) {
			if (!build.isBlocked()) {
				continue;
			}
			List<Cause> causes = build.getCauses();
			if (causes.size() > 1) {
				logger.log(Level.INFO, String.format("Build %s#%d has multiple causes, not stopping", build.task.getName(), build.id));
				continue;
			}
			if (causes.get(0) instanceof GhprbCause) {
				GhprbCause cause = (GhprbCause) causes.get(0);
				if (cause.getPullID() == id
				  && cause.getRepoName().equals(this.repo.getName())) {
					if (q.cancel(build)) {
						cancelled = true;
					} else {
						logger.log(Level.WARNING, String.format("Failed to cancel task %s#%d", build.task.getName(), build.id));
					}
				}
			}
		}
		return cancelled;
	}

	private GhprbCause getCause(AbstractBuild build){
		Cause cause = build.getCause(GhprbCause.class);
		if(cause == null || (!(cause instanceof GhprbCause))) return null;
		return (GhprbCause) cause;
	}

	public void onStarted(AbstractBuild build) {
		GhprbCause c = getCause(build);
		if(c == null) return;

		repo.createCommitStatus(build, GHCommitState.PENDING, (c.isMerged() ? "Merged build started." : "Build started."),c.getPullID());
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
		repo.createCommitStatus(build, state, (c.isMerged() ? "Merged build finished." : "Build finished."),c.getPullID() );

		String publishedURL = GhprbTrigger.getDscp().getPublishedURL();
		if (publishedURL != null && !publishedURL.isEmpty()) {
			String msg;
			if (state == GHCommitState.SUCCESS) {
				msg = GhprbTrigger.getDscp().getMsgSuccess();
			} else {
				msg = GhprbTrigger.getDscp().getMsgFailure();
			}
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
