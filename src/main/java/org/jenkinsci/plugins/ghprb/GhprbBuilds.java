package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.github.GHCommitState;

/**
 * @author janinko
 */
public class GhprbBuilds {
	private GhprbTrigger trigger;
	private GhprbRepository repo;

	public GhprbBuilds(GhprbTrigger trigger, GhprbRepository repo){
		this.trigger = trigger;
		this.repo = repo;
	}

	public String build(GhprbPullRequest pr) {
		StringBuilder sb = new StringBuilder();
		if(cancelBuild(pr.getId())){
			sb.append("Previous build stopped.");
		}

		if(pr.isMergeable()){
			sb.append(" Merged build triggered.");
		}else{
			sb.append(" Build triggered.");
		}

		startJob(pr.getId(),pr.getHead(), pr.isMergeable());
		return sb.toString();
	}

	private void startJob(int id, String commit, boolean merged) {
		QueueTaskFuture<?> build = trigger.startJob(new GhprbCause(commit, id, merged));
		if(build == null){
			Logger.getLogger(GhprbRepository.class.getName()).log(Level.SEVERE, "Job didn't started");
		}
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
		
		repo.createCommitStatus(build, GHCommitState.PENDING, (c.isMerged() ? "Merged build started." : "Build started."),c.getPullID());
		try {
			build.setDescription("<a href=\"" + repo.getRepoUrl()+"/pull/"+c.getPullID()+"\">Pull request #"+c.getPullID()+"</a>");
		} catch (IOException ex) {
			Logger.getLogger(GhprbBuilds.class.getName()).log(Level.SEVERE, "Can't update build description", ex);
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
		if (state == GHCommitState.FAILURE && trigger.getDescriptor().getAutoCloseFailedPullRequests()) {
			repo.closePullRequest(c.getPullID());
		}
	}
}
