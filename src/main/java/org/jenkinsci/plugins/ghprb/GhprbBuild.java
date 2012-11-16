package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.github.GHCommitState;

/**
 *
 * @author jbrazdil
 */
public class GhprbBuild {
	private QueueTaskFuture<?> future;
	private AbstractBuild<?, ?> build;
	private GhprbRepo repo;
	private int pull;
	private boolean merge;
	private boolean finished;

	GhprbBuild(GhprbRepo repo, int pull, QueueTaskFuture<?> future, boolean merge) {
		this.repo = repo;
		this.pull = pull;
		this.future = future;
		this.build = null;
		this.merge = merge;
		this.finished = false;
	}

	public void check() {
		if (build == null && future.getStartCondition().isDone()) {
			try {
				build = (AbstractBuild<?, ?>) future.getStartCondition().get();
				repo.createCommitStatus(build, GHCommitState.PENDING, (merge ? "Merged build started." : "Build started."),pull);
			} catch (Exception ex) {
				Logger.getLogger(GhprbBuild.class.getName()).log(Level.SEVERE, null, ex);
			}
		} else if (build != null && future.isDone()) {
			finished = true;

			GHCommitState state;
			if (build.getResult() == Result.SUCCESS) {
				state = GHCommitState.SUCCESS;
			} else {
				state = GHCommitState.FAILURE;
			}
			repo.createCommitStatus(build, state, (merge ? "Merged build finished." : "Build finished."),pull );

			String publishedURL = GhprbTrigger.DESCRIPTOR.getPublishedURL();
			if (publishedURL != null && !publishedURL.isEmpty()) {
				repo.addComment(pull, "Build results will soon be (or already are) available at: " + publishedURL + build.getUrl());
			}
		}
	}

	public boolean isFinished(){
		return finished;
	}

	public boolean cancel() {
		if (build == null) {
			try {
				build = (AbstractBuild<?, ?>) future.waitForStart();
			} catch (Exception ex) {
				Logger.getLogger(GhprbBuild.class.getName()).log(Level.WARNING, null, ex);
				return false;
			}
		}
		if (build.getExecutor() == null) {
			return false;
		}

		build.getExecutor().interrupt();
		finished = true;
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 71 * hash + this.pull;
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final GhprbBuild other = (GhprbBuild) obj;
		if (this.pull != other.pull) {
			return false;
		}
		return true;
	}

	public int getPullID() {
		return pull;
	}

	public boolean isMerge() {
		return merge;
	}
}
