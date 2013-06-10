package org.jenkinsci.plugins.ghprb;

import hudson.model.ProminentProjectAction;
import java.io.IOException;
import java.io.StringReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author janinko
 */
@Deprecated
public class GhprbProjectAction implements ProminentProjectAction{
	private static final Logger logger = Logger.getLogger(GhprbProjectAction.class.getName());
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
		String event = req.getHeader("X-GitHub-Event");
		String payload = req.getParameter("payload");
		if(payload == null){
			logger.log(Level.SEVERE, "Request doesn't contain payload.");
			return;
		}

		logger.log(Level.INFO, "Got payload event: {0}", event);
		try{
			if("issue_comment".equals(event)){
				GHEventPayload.IssueComment issueComment = gh.get().parseEventPayload(new StringReader(payload), GHEventPayload.IssueComment.class);
				repo.onIssueCommentHook(issueComment);
			}else if("pull_request".equals(event)) {
				GHEventPayload.PullRequest pr = gh.get().parseEventPayload(new StringReader(payload), GHEventPayload.PullRequest.class);
				repo.onPullRequestHook(pr);
			}else{
				logger.log(Level.WARNING, "Request not known");
			}
		}catch(IOException ex){
			logger.log(Level.SEVERE, "Failed to parse github hook payload.", ex);
		}
	}
}
