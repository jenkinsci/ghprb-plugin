package org.jenkinsci.plugins.ghprb;

import hudson.model.Cause;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbCause extends Cause{
	private String commit;
	private int pullID;
	private boolean merged;
	
	public GhprbCause(String commit, int pullID){
		this(commit, pullID, false);
	}
	public GhprbCause(String commit, int pullID, boolean merged){
		this.commit = commit;
		this.pullID = pullID;
		this.merged = merged;
	}

	@Override
	public String getShortDescription() {
		return "Github pull request #" + pullID + " of commit " + commit + (merged? " automatically merged." : ".");
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
}
