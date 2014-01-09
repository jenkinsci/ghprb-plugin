package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.git.util.BuildData;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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
			sb.append("Previous build stopped.");
		}

		if(pr.isMergeable()){
			sb.append(" Merged build triggered.");
		}else{
			sb.append(" Build triggered.");
		}

		GhprbCause cause = new GhprbCause(pr.getHead(), pr.getId(), pr.isMergeable(), pr.getTarget(), pr.getSource(), pr.getAuthorEmail(), pr.getTitle());

		QueueTaskFuture<?> build = trigger.startJob(cause, repo);
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

		// remove the BuildData action that we may have added earlier to avoid
		// having two of them, and because the one we added isn't correct
		// @see GhprbTrigger
		BuildData fakeOne = null;
		for (BuildData data :build.getActions(BuildData.class)) {
			if (!data.getLastBuiltRevision().getSha1String().equals(c.getCommit())) {
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
		} else if (build.getResult() == Result.UNSTABLE){
			state = GHCommitState.valueOf(GhprbTrigger.getDscp().getUnstableAs());
		} else {
			state = GHCommitState.FAILURE;
		}
		repo.createCommitStatus(build, state, (c.isMerged() ? "Merged build finished." : "Build finished."),c.getPullID() );

		String publishedURL = GhprbTrigger.getDscp().getPublishedURL();
		if (publishedURL != null && !publishedURL.isEmpty()) {
			StringBuilder msg = new StringBuilder();

			if (state == GHCommitState.SUCCESS) {
				msg.append(GhprbTrigger.getDscp().getMsgSuccess());
			} else {
				msg.append(GhprbTrigger.getDscp().getMsgFailure());
			}
			msg.append("\nRefer to this link for build results: ");
			msg.append(publishedURL).append(build.getUrl());

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
					logger.log(Level.WARNING, "Can't add log excerpt to commit comments", ex);
				}
			}

			repo.addComment(c.getPullID(), msg.toString());
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
