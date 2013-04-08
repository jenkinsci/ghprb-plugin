
package org.jenkinsci.plugins.ghprb;

import hudson.model.ProminentProjectAction;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.github.GHEventPayload;
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
		System.out.println("Receiving...");
		String event = req.getHeader("X-Github-Event");
		String payload = req.getParameter("payload");
		if(payload == null){
			Logger.getLogger(GhprbProjectAction.class.getName()).log(Level.SEVERE, "Request doesn't contain payload.");
			return;
		}
		try{
			if("issue_comment".equals(event)){
				System.out.println("issue_comment");
				GHEventPayload.IssueComment issueComment = gh.get().parseEventPayload(new StringReader(payload), GHEventPayload.IssueComment.class);
				System.out.println(issueComment);
				repo.onIssueCommentHook(issueComment);
			}else if("pull_request".equals(event)) {
				System.out.println("pull_request");
				GHEventPayload.PullRequest pr = gh.get().parseEventPayload(new StringReader(payload), GHEventPayload.PullRequest.class);
				System.out.println(pr);
				repo.onPullRequestHook(pr);
			}else{
				Logger.getLogger(GhprbProjectAction.class.getName()).log(Level.WARNING, "Request not known");
			}
		}catch(IOException ex){
			Logger.getLogger(GhprbProjectAction.class.getName()).log(Level.SEVERE, "Failed to parse github hook payload.", ex);
		}
	}
}
