package org.jenkinsci.plugins.ghprb;

import org.apache.commons.lang.StringUtils;

import hudson.model.Cause;

import java.net.URL;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbCause extends Cause{
	private final String commit;
	private final int pullID;
	private final boolean merged;
	private final String targetBranch;
	private final String sourceBranch;
	private final String authorEmail;
    private final String authorUsername;
	private final String title;
    private final URL url;

    public GhprbCause(String commit, int pullID, boolean merged, String targetBranch, String sourceBranch, String authorEmail, String authorUsername,
                      String title, URL url){
		this.commit = commit;
		this.pullID = pullID;
		this.merged = merged;
		this.targetBranch = targetBranch;
		this.sourceBranch = sourceBranch;
		this.authorEmail = authorEmail;
        this.authorUsername = authorUsername;
		this.title = title;
        this.url = url;
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
	
	public String getSourceBranch() {
		return sourceBranch;
	}

	public String getAuthorEmail() {
		return authorEmail;
	}

    public String getAuthorUsername() {
        return authorUsername;
    }

    public URL getUrl() {
        return url;
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
