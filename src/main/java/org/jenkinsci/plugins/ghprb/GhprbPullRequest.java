package org.jenkinsci.plugins.ghprb;

import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbPullRequest{
	private final int id;
	private final String author;
	private Date updated;
	private String head;
	private boolean mergeable;

	private boolean shouldRun = false;
	private boolean accepted = false;
	@Deprecated private boolean askedForApproval; // TODO: remove

	private transient GhprbRepo repo;

	GhprbPullRequest(GHPullRequest pr, GhprbRepo ghprbRepo) {
		id = pr.getNumber();
		updated = pr.getUpdatedAt();
		head = pr.getHead().getSha();
		author = pr.getUser().getLogin();

		repo = ghprbRepo;

		if(repo.isWhitelisted(author)){
			accepted = true;
			shouldRun = true;
		}else{
			Logger.getLogger(GhprbPullRequest.class.getName()).log(Level.INFO, "Author of #{0} {1} on {2} not in whitelist!", new Object[]{id, author, ghprbRepo.getName()});
			addComment("Can one of the admins verify this patch?");
		}

		Logger.getLogger(GhprbPullRequest.class.getName()).log(Level.INFO, "Created pull request #{0} on {1} by {2} updated at: {3} SHA: {4}", new Object[]{id, ghprbRepo.getName(), author, updated, head});
	}

	public void check(GHPullRequest pr, GhprbRepo ghprbRepo){
		repo = ghprbRepo;
		if(isUpdated(pr)){
			Logger.getLogger(GhprbPullRequest.class.getName()).log(Level.INFO, "Pull request builder: pr #{0} was updated on {1} at {2}", new Object[]{id, ghprbRepo.getName(), updated});

			int commentsChecked = checkComments(pr);
			boolean newCommit   = checkCommit(pr.getHead().getSha());

			if(!newCommit && commentsChecked == 0){
				Logger.getLogger(GhprbPullRequest.class.getName()).log(Level.INFO, "Pull request was updated on repo " + ghprbRepo.getName() + " but there aren't any new comments nor commits - that may mean that commit status was updated.");
			}
			updated = pr.getUpdatedAt();
		}

		if(shouldRun){
			checkMergeable(pr);
			build();
		}
	}

	private boolean isUpdated(GHPullRequest pr){
		boolean ret = false;
		ret = ret || updated.compareTo(pr.getUpdatedAt()) < 0;
		ret = ret || !pr.getHead().getSha().equals(head);

		return ret;
	}

	private void build() {
		shouldRun = false;

		StringBuilder sb = new StringBuilder();
		if(repo.cancelBuild(id)){
			sb.append("Previous build stopped.");
		}

		if(mergeable){
			sb.append(" Merged build triggered.");
		}else{
			sb.append(" Build triggered.");
		}

		repo.startJob(id,head, mergeable);
		repo.createCommitStatus(head, GHCommitState.PENDING, null, sb.toString(),id);

		Logger.getLogger(GhprbPullRequest.class.getName()).log(Level.INFO, sb.toString());
	}

	private void addComment(String comment) {
		repo.addComment(id,comment);
	}

	// returns false if no new commit
	private boolean checkCommit(String sha){
		if(head.equals(sha)) return false;

		if(Logger.getLogger(GhprbPullRequest.class.getName()).isLoggable(Level.FINE)){
			Logger.getLogger(GhprbPullRequest.class.getName()).log(Level.FINE, "New commit. Sha: " + head + " => " + sha);
		}

		head = sha;
		if(accepted){
			shouldRun = true;
		}
		return true;
	}

	private void checkComment(GHIssueComment comment) throws IOException {
		String sender = comment.getUser().getLogin();
		if (repo.isMe(sender)){
			return;
		}
		String body = comment.getBody();

		// add to whitelist
		if (repo.isWhitelistPhrase(body) && repo.isAdmin(sender)){
			if(!repo.isWhitelisted(author)) {
				repo.addWhitelist(author);
			}
			accepted = true;
			shouldRun = true;
		}

		// ok to test
		if(repo.isOktotestPhrase(body) && repo.isAdmin(sender)){
			accepted = true;
			shouldRun = true;
		}

		// test this please
		if (repo.isRetestPhrase(body)){
			if(repo.isAdmin(sender)){
				shouldRun = true;
			}else if(accepted && repo.isWhitelisted(sender) ){
				shouldRun = true;
			}
		}
	}

	private int checkComments(GHPullRequest pr) {
		int count = 0;
		try {
			for (GHIssueComment comment : pr.getComments()) {
				if (updated.compareTo(comment.getUpdatedAt()) < 0) {
					count++;
					try {
						checkComment(comment);
					} catch (IOException ex) {
						Logger.getLogger(GhprbPullRequest.class.getName()).log(Level.SEVERE, "Couldn't check comment #" + comment.getId(), ex);
					}
				}
			}
		} catch (IOException e) {
			Logger.getLogger(GhprbPullRequest.class.getName()).log(Level.SEVERE, "Couldn't obtain comments.", e);
		}
		return count;
	}

	private void checkMergeable(GHPullRequest pr) {
		try {
			mergeable = pr.getMergeable();
		} catch (IOException e) {
			mergeable = false;
			Logger.getLogger(GhprbPullRequest.class.getName()).log(Level.SEVERE, "Couldn't obtain mergeable status.", e);
		}
	}

	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof GhprbPullRequest)) return false;

		GhprbPullRequest o = (GhprbPullRequest) obj;
		return o.id == id;
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 89 * hash + this.id;
		return hash;
	}
}
