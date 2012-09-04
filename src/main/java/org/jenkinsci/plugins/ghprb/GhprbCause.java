package org.jenkinsci.plugins.ghprb;

import hudson.model.Cause;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbCause extends Cause{
	private String commit;
	private int pullID;
	
	public GhprbCause(String commit, int pullID){
		this.commit = commit;
		this.pullID = pullID;
	}

	@Override
	public String getShortDescription() {
		return "Github pull request #" + pullID + " of commit " + commit;
	}

	String getCommit() {
		return commit;
	}
	
}
