package org.jenkinsci.plugins.ghprb;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbPullRequest{
	private int id;
	private Date updated;
	private String head;
	private String author;
	private boolean mergeable;
	@Deprecated private transient String target; // TODO: remove
	
	private boolean shouldRun = true;
	private boolean askedForApproval = false;
	
	private transient GhprbRepo repo;

	GhprbPullRequest(GHPullRequest pr) {
		id = pr.getNumber();
		updated = new Date(0);
		head = pr.getHead().getSha();
		author = pr.getUser().getLogin();
		Logger.getLogger(GhprbPullRequest.class.getName()).log(Level.INFO, "Created pull request #{0} by {1} udpdated at: {2} sha: {3}", new Object[]{id, author, updated, head});
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
	
	public void check(GHPullRequest pr, GhprbRepo ghprbRepo) throws IOException {
		repo = ghprbRepo;
		if(isUpdated(pr)){
			Logger.getLogger(GhprbPullRequest.class.getName()).log(Level.INFO, "Pull request builder: pr #{0} Updated {1}", new Object[]{id, pr.getUpdatedAt()});

			int commentsChecked = checkComments(pr.getComments());

			if(!head.equals(pr.getHead().getSha())){
				head = pr.getHead().getSha();
				shouldRun = true;
			}else if(commentsChecked == 0){
				Logger.getLogger(GhprbPullRequest.class.getName()).log(Level.WARNING, "Pull request was updated, but it seems nothing changed.");
			}
			updated = pr.getUpdatedAt();
		}
		if(shouldRun){
			mergeable = pr.getMergeable();
			build();
		}
	}

	private boolean isUpdated(GHPullRequest pr){
		boolean ret = false;
		ret = ret || updated.compareTo(pr.getUpdatedAt()) < 0;
		ret = ret || pr.getHead().getSha().equals(head);

		return ret;
	}

	private void build() {
		shouldRun = false;
		if(!repo.isWhitelisted(author)){
			Logger.getLogger(GhprbPullRequest.class.getName()).log(Level.INFO, "Author of #{0} {1} not in whitelist!", new Object[]{id, author});
			if(!askedForApproval){
				addComment("Can one of the admins verify this patch?");
				askedForApproval = true;
			}
			return;
		}
		StringBuilder sb = new StringBuilder();
		if(repo.cancelBuild(id)){
			sb.append("Previous build stopped. ");
		}

		if(mergeable){
			sb.append("Merged build triggered.");
		}else{
			sb.append("Build triggered.");
		}

		repo.startJob(id,head, mergeable);
		repo.createCommitStatus(head, GHCommitState.PENDING, null, sb.toString());

		Logger.getLogger(GhprbPullRequest.class.getName()).log(Level.INFO, sb.toString());
	}
	
	private void addComment(String comment) {
		repo.addComment(id,comment);
	}

	private void checkComment(GHIssueComment comment) throws IOException {
		if (repo.isMe(comment.getUser().getLogin())){
			return;
		}
		if (   repo.isWhitelistPhrase(comment.getBody())
		   &&  repo.isAdmin(comment.getUser().getLogin())
		   && !repo.isWhitelisted(author)) {
			repo.addWhitelist(author);
			shouldRun = true;
		}
		if (repo.isRetestPhrase(comment.getBody()) && repo.isWhitelisted(comment.getUser().getLogin())) {
			shouldRun = true;
		}
	}

	private int checkComments(List<GHIssueComment> comments) {
		int count = 0;
		try {
			for (GHIssueComment comment : comments) {
				if (updated.compareTo(comment.getUpdatedAt()) < 0) {
					count++;
					try {
						checkComment(comment);
					} catch (IOException ex) {
						Logger.getLogger(GhprbPullRequest.class.getName()).log(Level.SEVERE, "Couldn't check comment #" + comment.getId(), ex);
					}
				}
			}
		} catch (NoSuchElementException e) { // TODO: WA for: https://github.com/kohsuke/github-api/issues/20
			Logger.getLogger(GhprbPullRequest.class.getName()).log(Level.SEVERE, "You probably don't have current version of github-api.", e);
		}
		return count;
	}
	
	
	
}
