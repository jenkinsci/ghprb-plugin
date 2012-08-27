/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.janinko.ghprb;

import hudson.model.Cause;

/**
 *
 * @author jbrazdil
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
