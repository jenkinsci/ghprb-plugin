package org.jenkinsci.plugins.ghprb;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
@Extension
public class GhprbRootAction implements UnprotectedRootAction {
	private static final Logger logger = Logger.getLogger(GhprbRootAction.class.getName());
	static final String URL = "ghprbhook";
	
	public String getIconFileName() {
		return null;
	}

	public String getDisplayName() {
		return null;
	}

	public String getUrlName() {
		return URL;
	}

	public void doIndex(StaplerRequest req, StaplerResponse resp) {
		String event = req.getHeader("X-GitHub-Event");
		String payload = req.getParameter("payload");
		if(payload == null){
			logger.log(Level.SEVERE, "Request doesn't contain payload.");
			return;
		}

		GhprbGitHub gh = GhprbTrigger.getDscp().getGitHub();

		logger.log(Level.INFO, "Got payload event: {0}", event);
		try{
			if("issue_comment".equals(event)){
				GHEventPayload.IssueComment issueComment = gh.get().parseEventPayload(new StringReader(payload), GHEventPayload.IssueComment.class);
				for(GhprbRepository repo : getRepos(issueComment.getRepository())){
					repo.onIssueCommentHook(issueComment);
				}
			}else if("pull_request".equals(event)) {
				GHEventPayload.PullRequest pr = gh.get().parseEventPayload(new StringReader(payload), GHEventPayload.PullRequest.class);
				for(GhprbRepository repo : getRepos(pr.getPullRequest().getRepository())){
					repo.onPullRequestHook(pr);
				}
			}else{
				logger.log(Level.WARNING, "Request not known");
			}
		}catch(IOException ex){
			logger.log(Level.SEVERE, "Failed to parse github hook payload.", ex);
		}
	}

	private Set<GhprbRepository> getRepos(GHRepository repo) throws IOException{
		try{
			return getRepos(repo.getOwner().getLogin() + "/" + repo.getName());
		}catch(Exception ex){
			logger.log(Level.WARNING, "Can't get a valid owner for repo");
			// this normally happens due to missing "login" field in the owner of the repo
			// when the repo is inside of an organisation account. The only field which doesn't
			// rely on the owner.login (which would throw a null pointer exception) is the "html_url"
			// field. So we try to parse the owner out of that here until github fixes his api
			String repoUrl = repo.getUrl();
			if(repoUrl.endsWith("/")) // strip off trailing slash if any
				repoUrl = repoUrl.substring(0, repoUrl.length()-2);
			int slashIndex = repoUrl.lastIndexOf('/');
			String owner = repoUrl.substring(slashIndex+1);
			logger.log(Level.INFO, "Parsed {0} from {1}", new Object[]{owner, repoUrl});
			return getRepos(owner + "/" + repo.getName());
		}
	}

	private Set<GhprbRepository> getRepos(String repo){
		HashSet<GhprbRepository> ret = new HashSet<GhprbRepository>();

		// We need this to get access to list of repositories
		Authentication old = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);

		try{
			for(AbstractProject<?,?> job : Jenkins.getInstance().getAllItems(AbstractProject.class)){
				GhprbTrigger trigger = job.getTrigger(GhprbTrigger.class);
				if (trigger == null || trigger.getGhprb() == null) continue;
				GhprbRepository r = trigger.getGhprb().getRepository();
				if(repo.equals(r.getName())){
					ret.add(r);
				}
			}
		}finally{
			SecurityContextHolder.getContext().setAuthentication(old);
		}
		return ret;
	}
}
