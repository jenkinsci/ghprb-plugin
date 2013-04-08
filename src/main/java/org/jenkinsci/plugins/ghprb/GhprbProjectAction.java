
package org.jenkinsci.plugins.ghprb;

import hudson.model.ProminentProjectAction;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author janinko
 */
public class GhprbProjectAction implements ProminentProjectAction{
	static final String URL = "ghprbhook";
	private GhprbGitHub gh;
	private GhprbRepository repo;

	public GhprbProjectAction(GhprbTrigger trigger){
		repo = trigger.getGhprb().getRepository();
		gh = trigger.getGhprb().getGitHub();
	}

	public String getIconFileName() {
		return null;
	}

	public String getDisplayName() {
		return null;
	}

	public String getUrlName() {
		return URL;
	}

	public void doIndex(StaplerRequest req) {
		Logger.getLogger(GhprbProjectAction.class.getName()).log(Level.INFO, "Not implemented yet.");
	}
}
