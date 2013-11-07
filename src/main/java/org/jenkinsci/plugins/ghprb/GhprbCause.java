package org.jenkinsci.plugins.ghprb;

import org.apache.commons.lang.StringUtils;

import hudson.model.Cause;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbCause extends Cause{
	private final String commit;
	private final int pullID;
	private final boolean merged;
	private final String targetBranch;
	private final String authorEmail;
	private final String title;
	private final String repoName;

	public GhprbCause(String commit, int pullID, boolean merged, String targetBranch, String authorEmail, String title, String repoName){
		this.commit = commit;
		this.pullID = pullID;
		this.merged = merged;
		this.targetBranch = targetBranch;
		this.authorEmail = authorEmail;
		this.title = title;
		this.repoName = repoName;
	}

	@Override
	public String getShortDescription() {
		return "GitHub pull request #" + pullID + " of commit " + commit + (merged? " automatically merged." : ".");
	}

	public String getCommit() {
		return commit;
	}
	
	public boolean isMerged() {
		return merged;
	}

	public int getPullID(){
		return pullID;
	}

	public String getTargetBranch() {
		return targetBranch;
	}

	public String getAuthorEmail() {
		return authorEmail;
	}

	public String getRepoName() {
		return repoName;
	}

	/**
	 * Returns the title of the cause, not null.
	 * @return
	 */
	public String getTitle() {
		return title != null ? title : "";
	}

	/**
	 * Returns at most the first 30 characters of the title, or 
	 * @return
	 */
	public String getAbbreviatedTitle() {
		return StringUtils.abbreviate(getTitle(), 30);
	}
	
	
}
