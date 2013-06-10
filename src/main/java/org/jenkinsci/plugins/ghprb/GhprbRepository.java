package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractBuild;
import java.io.IOException;
import java.net.URL;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload.IssueComment;
import org.kohsuke.github.GHEventPayload.PullRequest;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbRepository {
	private static final Logger logger = Logger.getLogger(GhprbRepository.class.getName());
	private final String reponame;

	private Map<Integer,GhprbPullRequest> pulls;

	private GHRepository repo;
	private Ghprb ml;

	public GhprbRepository(String user,
	                 String repository,
	                 Ghprb helper,
	                 Map<Integer,GhprbPullRequest> pulls){
		reponame = user + "/" + repository;
		this.ml = helper;
		this.pulls = pulls;
	}

	public void init(){
		checkState();
		for(GhprbPullRequest pull : pulls.values()){
			pull.init(ml,this);
		}
	}

	private boolean checkState(){
		if(repo == null){
			try {
				repo = ml.getGitHub().get().getRepository(reponame);
			} catch (IOException ex) {
				logger.log(Level.SEVERE, "Could not retrieve repo named " + reponame + " (Do you have properly set 'GitHub project' field in job configuration?)", ex);
				return false;
			}
		}
		return true;
	}

	public void check(){
		if(!checkState()) return;

		List<GHPullRequest> prs;
		try {
			prs = repo.getPullRequests(GHIssueState.OPEN);
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Could not retrieve pull requests.", ex);
			return;
		}
		Set<Integer> closedPulls = new HashSet<Integer>(pulls.keySet());

		for(GHPullRequest pr : prs){
			if(pr.getHead() == null) try {
				pr = repo.getPullRequest(pr.getNumber());
			} catch (IOException ex) {
				Logger.getLogger(GhprbRepository.class.getName()).log(Level.SEVERE, "Could not retrieve pr " + pr.getNumber(), ex);
				return;
			}
			check(pr);
			closedPulls.remove(pr.getNumber());
		}

		removeClosed(closedPulls, pulls);
	}

	private void check(GHPullRequest pr){
			Integer id = pr.getNumber();
			GhprbPullRequest pull;
			if(pulls.containsKey(id)){
				pull = pulls.get(id);
			}else{
				pull = new GhprbPullRequest(pr, ml, this);
				pulls.put(id, pull);
			}
			pull.check(pr);
	}

	private void removeClosed(Set<Integer> closedPulls, Map<Integer,GhprbPullRequest> pulls) {
		if(closedPulls.isEmpty()) return;

		for(Integer id : closedPulls){
			pulls.remove(id);
		}
	}

	public void createCommitStatus(AbstractBuild<?,?> build, GHCommitState state, String message, int id){
		String sha1 = build.getCause(GhprbCause.class).getCommit();
		createCommitStatus(sha1, state, Jenkins.getInstance().getRootUrl() + build.getUrl(), message, id);
	}

	public void createCommitStatus(String sha1, GHCommitState state, String url, String message, int id) {
		logger.log(Level.INFO, "Setting status of {0} to {1} with url {2} and message: {3}", new Object[]{sha1, state, url, message});
		try {
			repo.createCommitStatus(sha1, state, url, message);
		} catch (IOException ex) {
			if(GhprbTrigger.getDscp().getUseComments()){
				logger.log(Level.INFO, "Could not update commit status of the Pull Request on GitHub. Trying to send comment.", ex);
				addComment(id, message);
			}else{
				logger.log(Level.SEVERE, "Could not update commit status of the Pull Request on GitHub.", ex);
			}
		}
	}

	public String getName() {
		return reponame;
	}

	public void addComment(int id, String comment) {
		try {
			repo.getPullRequest(id).comment(comment);
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Couldn't add comment to pull request #" + id + ": '" + comment + "'", ex);
		}
	}

	public void closePullRequest(int id) {
		try {
			repo.getPullRequest(id).close();
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Couldn't close the pull request #" + id + ": '", ex);
		}
	}

	public String getRepoUrl(){
		return ml.getGitHubServer()+"/"+reponame;
	}


	private static final EnumSet<GHEvent> EVENTS = EnumSet.of(GHEvent.ISSUE_COMMENT, GHEvent.PULL_REQUEST);
	private boolean hookExist() throws IOException{
		for(GHHook h : repo.getHooks()){
			if(!"web".equals(h.getName())) continue;
			//System.out.println("  "+h.getEvents());
			//if(!EVENTS.equals(h.getEvents())) continue;
			if(!ml.getHookUrl().equals(h.getConfig().get("url"))) continue;
			return true;
		}
		return false;
	}

	public boolean createHook(){
		try{
			if(hookExist()) return true;
			repo.createWebHook(new URL(ml.getHookUrl()),EVENTS);
			return true;
		}catch(IOException ex){
			logger.log(
					Level.SEVERE,
					"Couldn't create web hook for repository "+
					reponame+
					". Does the user (from global configuration) have admin rights to the repository?",
					ex);
			return false;
		}
	}

	public GHPullRequest getPullRequest(int id) throws IOException{
		return repo.getPullRequest(id);
	}

	void onIssueCommentHook(IssueComment issueComment) {
		int id = issueComment.getIssue().getNumber();
		if(logger.isLoggable(Level.FINER)){
			logger.log(
					Level.FINER,
					"Comment on issue #{0}: '{1}'",
					new Object[]{id,issueComment.getComment().getBody()});
		}
		if(!"created".equals(issueComment.getAction())) return;
		GhprbPullRequest pull = pulls.get(id);
		if(pull == null){
			if(logger.isLoggable(Level.FINER)){
				logger.log(Level.FINER, "Pull request #{0} desn't exist", id);
			}
			return;
		}
		pull.check(issueComment.getComment());
		GhprbTrigger.getDscp().save();
	}

	void onPullRequestHook(PullRequest pr) {
		if("opened".equals(pr.getAction()) || "reopened".equals(pr.getAction())){
			GhprbPullRequest pull = pulls.get(pr.getNumber());
			if(pull == null){
				pull = new GhprbPullRequest(pr.getPullRequest(), ml, this);
				pulls.put(pr.getNumber(), pull);
			}
			pull.check(pr.getPullRequest());
		}else if("synchronize".equals(pr.getAction())){
			GhprbPullRequest pull = pulls.get(pr.getNumber());
			if(pull == null){
				logger.log(Level.SEVERE, "Pull Request #{0} doesn't exist", pr.getNumber());
				return;
			}
			pull.check(pr.getPullRequest());
		}else if("closed".equals(pr.getAction())){
			pulls.remove(pr.getNumber());
		}else{
			logger.log(Level.WARNING, "Unknown Pull Request hook action: {0}", pr.getAction());
		}
		GhprbTrigger.getDscp().save();
	}
}
