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
	private static final Logger logger = Logger.getLogger(GhprbPullRequest.class.getName());
	private final int id;
	private final String author;
	private String title;
	private Date updated;
	private String head;
	private boolean mergeable;
	private String reponame;
	private String target;
	private String authorEmail;

	private boolean shouldRun = false;
	private boolean accepted = false;
	private boolean triggered = false;
	@Deprecated private transient boolean askedForApproval; // TODO: remove

	private transient Ghprb ml;
	private transient GhprbRepository repo;

	GhprbPullRequest(GHPullRequest pr, Ghprb helper, GhprbRepository repo) {
		id = pr.getNumber();
		updated = pr.getUpdatedAt();
		head = pr.getHead().getSha();
		author = pr.getUser().getLogin();
		title = pr.getTitle();
		reponame = repo.getName();
		target = pr.getBase().getRef();
		obtainAuthorEmail(pr);

		this.ml = helper;
		this.repo = repo;

		if(helper.isWhitelisted(author)){
			accepted = true;
			shouldRun = true;
		}else{
			logger.log(Level.INFO, "Author of #{0} {1} on {2} not in whitelist!", new Object[]{id, author, reponame});
			repo.addComment(id, GhprbTrigger.getDscp().getRequestForTestingPhrase());
		}

		logger.log(Level.INFO, "Created pull request #{0} on {1} by {2} ({3}) updated at: {4} SHA: {5}", new Object[]{id, reponame, author, authorEmail, updated, head});
	}

	public void init(Ghprb helper, GhprbRepository repo) {
		this.ml = helper;
		this.repo = repo;
		if(reponame == null) reponame = repo.getName(); // If this instance was created before v1.8, it can be null.
	}

	public void check(GHPullRequest pr){
		if(target == null) target = pr.getBase().getRef(); // If this instance was created before target was introduced (before v1.8), it can be null.
		if(authorEmail == null) {
			// If this instance was create before authorEmail was introduced (before v1.10), it can be null.
			obtainAuthorEmail(pr); 
		}

		if(isUpdated(pr)){
			logger.log(Level.INFO, "Pull request builder: pr #{0} was updated on {1} at {2} by {3} ({4})", new Object[]{id, reponame, updated, author, authorEmail});

			// the title could have been updated since the original PR was opened
			title = pr.getTitle();
			int commentsChecked = checkComments(pr);
			boolean newCommit = checkCommit(pr.getHead().getSha());

			if(!newCommit && commentsChecked == 0){
				logger.log(Level.INFO, "Pull request was updated on repo {0} but there aren't any new comments nor commits - that may mean that commit status was updated.", reponame);
			}
			updated = pr.getUpdatedAt();
		}

		if(shouldRun){
			checkMergeable(pr);
			build();
		}
	}

	public void check(GHIssueComment comment) {
		try {
			checkComment(comment);
			updated = comment.getUpdatedAt();
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Couldn't check comment #" + comment.getId(), ex);
			return;
		}
		if(ml.ifOnlyTriggerPhrase() && !triggered){
			shouldRun = false;
		}
		if (shouldRun) {
			build();
		}
	}

	private boolean isUpdated(GHPullRequest pr){
		boolean ret = false;
		ret = ret || updated.compareTo(pr.getUpdatedAt()) < 0;
		ret = ret || !pr.getHead().getSha().equals(head);

		return ret;
	}

	private void build(){
		shouldRun = false;
		String message = ml.getBuilds().build(this);

		repo.createCommitStatus(head, GHCommitState.PENDING, null, message,id);

		logger.log(Level.INFO, message);
	}

	// returns false if no new commit
	private boolean checkCommit(String sha){
		if(head.equals(sha)) return false;

		if(logger.isLoggable(Level.FINE)){
			logger.log(Level.FINE, "New commit. Sha: {0} => {1}", new Object[]{head, sha});
		}

		head = sha;
		if(accepted){
			shouldRun = true;
		}
		return true;
	}

	private void checkComment(GHIssueComment comment) throws IOException {
		String sender = comment.getUser().getLogin();
		String body = comment.getBody();

		// add to whitelist
		if (ml.isWhitelistPhrase(body) && ml.isAdmin(sender)){
			if(!ml.isWhitelisted(author)) {
				ml.addWhitelist(author);
			}
			accepted = true;
			shouldRun = true;
		}

		// ok to test
		if(ml.isOktotestPhrase(body) && ml.isAdmin(sender)){
			accepted = true;
			shouldRun = true;
		}

		// test this please
		if (ml.isRetestPhrase(body)){
			if(ml.isAdmin(sender)){
				shouldRun = true;
			}else if(accepted && ml.isWhitelisted(sender) ){
				shouldRun = true;
			}
		}

		// trigger phrase
		if (ml.isTriggerPhrase(body) && ml.isWhitelisted(sender)){
			triggered = true;
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
						logger.log(Level.SEVERE, "Couldn't check comment #" + comment.getId(), ex);
					}
				}
			}
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Couldn't obtain comments.", e);
		}
		return count;
	}

	private void checkMergeable(GHPullRequest pr) {
		try {
			int r=5;
			while(pr.getMergeable() == null && r-->0){
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ex) {
					break;
				}
				pr = repo.getPullRequest(id);
			}
			mergeable = pr.getMergeable() != null && pr.getMergeable();
		} catch (IOException e) {
			mergeable = false;
			logger.log(Level.SEVERE, "Couldn't obtain mergeable status.", e);
		}
	}
	
	private void obtainAuthorEmail(GHPullRequest pr) {
		try {
			authorEmail = pr.getUser().getEmail();
		} catch (IOException e) {
			logger.log(Level.WARNING, "Couldn't obtain author email.", e);
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

	public int getId() {
		return id;
	}

	public String getHead() {
		return head;
	}

	public boolean isMergeable() {
		return mergeable;
	}

	public String getTarget(){
		return target;
	}
	
	public String getAuthorEmail() {
		return authorEmail;
	}
	
	public String getTitle() {
		return title;
	}
}
