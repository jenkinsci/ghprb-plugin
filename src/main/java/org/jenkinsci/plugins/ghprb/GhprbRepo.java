package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractBuild;
import hudson.model.queue.QueueTaskFuture;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbRepo {
	private final GhprbTrigger trigger;
	private final Pattern retestPhrasePattern;
	private final Pattern whitelistPhrasePattern;
	private final Pattern oktotestPhrasePattern;
	private final String reponame;

	private final HashSet<GhprbBuild> builds;

	private GitHub gh;
	private GHRepository repo;

	public GhprbRepo(GhprbTrigger trigger, String githubServer, String user, String repository){
		this.trigger = trigger;
		reponame = user + "/" + repository;

		String accessToken = trigger.getDescriptor().getAccessToken();
		if(accessToken != null && !accessToken.isEmpty()) {
			try {
				gh = GitHub.connectUsingOAuth(githubServer, accessToken);
			} catch(IOException e) {
				Logger.getLogger(GhprbRepo.class.getName()).log(Level.SEVERE, "can't connect using oauth", e);
			}
		} else {
			//TODO: when (if) the github api supports sending in a server w/o a token set it here...
			//gh = GitHub.connect(githubServer, trigger.getDescriptor().getUsername(), null, trigger.getDescriptor().getPassword());
			gh = GitHub.connect(trigger.getDescriptor().getUsername(), null, trigger.getDescriptor().getPassword());
		}

		retestPhrasePattern = Pattern.compile(trigger.getDescriptor().getRetestPhrase());
		whitelistPhrasePattern = Pattern.compile(trigger.getDescriptor().getWhitelistPhrase());
		oktotestPhrasePattern = Pattern.compile(trigger.getDescriptor().getOkToTestPhrase());
		builds = new HashSet<GhprbBuild>();
	}
	
	public void check(Map<Integer,GhprbPullRequest> pulls){
		if(repo == null) try {
				repo = gh.getRepository(reponame); //TODO: potential NPE
				if(repo == null){
					Logger.getLogger(GhprbRepo.class.getName()).log(Level.SEVERE, "Could not retrieve repo named {0} (Do you have properly set 'GitHub project' field in job configuration?)", reponame);
				}
		} catch (IOException ex) {
			Logger.getLogger(GhprbRepo.class.getName()).log(Level.SEVERE, "Could not retrieve repo named " + reponame + " (Do you have properly set 'GitHub project' field in job configuration?)", ex);
		}
		List<GHPullRequest> prs;
		try {
			prs = repo.getPullRequests(GHIssueState.OPEN);
		} catch (IOException ex) {
			Logger.getLogger(GhprbRepo.class.getName()).log(Level.SEVERE, "Could not retrieve pull requests.", ex);
			return;
		}
		Set<Integer> closedPulls = new HashSet<Integer>(pulls.keySet());

		for(GHPullRequest pr : prs){
			Integer id = pr.getNumber();
			GhprbPullRequest pull;
			if(pulls.containsKey(id)){
				pull = pulls.get(id);
			}else{
				pull = new GhprbPullRequest(pr, this);
				pulls.put(id, pull);
			}
			pull.check(pr,this);
			closedPulls.remove(id);
		}
		
		removeClosed(closedPulls, pulls);
		checkBuilds();
	}
	
	private void removeClosed(Set<Integer> closedPulls, Map<Integer,GhprbPullRequest> pulls) {
		if(closedPulls.isEmpty()) return;
		
		for(Integer id : closedPulls){
			pulls.remove(id);
		}
	}
	
	private void checkBuilds(){
		Iterator<GhprbBuild> it = builds.iterator();
		while(it.hasNext()){
			GhprbBuild build = it.next();
			build.check();
			if(build.isFinished()){
				it.remove();
			}
		}
	}

	public void createCommitStatus(AbstractBuild<?,?> build, GHCommitState state, String message){
		String sha1 = build.getCause(GhprbCause.class).getCommit();
		createCommitStatus(sha1, state, Jenkins.getInstance().getRootUrl() + build.getUrl(), message);
	}

	public void createCommitStatus(String sha1, GHCommitState state, String url, String message) {
		Logger.getLogger(GhprbRepo.class.getName()).log(Level.INFO, "Setting status of {0} to {1} with url {2} and mesage: {3}", new Object[]{sha1, state, url, message});
		try {
			repo.createCommitStatus(sha1, state, url, message);
		} catch (IOException ex) {
			Logger.getLogger(GhprbRepo.class.getName()).log(Level.SEVERE, "Could not update commit status of the Pull Request on Github.", ex);
		}
	}

	public boolean cancelBuild(int id) {
		Iterator<GhprbBuild> it = builds.iterator();
		while(it.hasNext()){
			GhprbBuild build  = it.next();
			if (build.getPullID() == id) {
				if (build.cancel()) {
					it.remove();
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean isWhitelisted(String username){
		return trigger.whitelisted.contains(username) || trigger.admins.contains(username);
	}
	
	public boolean isAdmin(String username){
		return trigger.admins.contains(username);
	}
	
	public boolean isRetestPhrase(String comment){
		return retestPhrasePattern.matcher(comment).matches();
	}
	
	public boolean isWhitelistPhrase(String comment){
		return whitelistPhrasePattern.matcher(comment).matches();
	}
	
	public boolean isOktotestPhrase(String comment){
		return oktotestPhrasePattern.matcher(comment).matches();
	}

	public void addComment(int id, String comment) {
		try {
			repo.getPullRequest(id).comment(comment);
		} catch (IOException ex) {
			Logger.getLogger(GhprbRepo.class.getName()).log(Level.SEVERE, "Couldn't add comment to pullrequest #" + id + ": '" + comment + "'", ex);
		}
	}

	public void addWhitelist(String author) {
		Logger.getLogger(GhprbRepo.class.getName()).log(Level.INFO, "Adding " + author + " to whitelist.");
		trigger.whitelist = trigger.whitelist + " " + author;
		trigger.whitelisted.add(author);
		trigger.changed = true;
	}

	public boolean isMe(String username){
		return trigger.getDescriptor().getUsername().equals(username);
	}

	public void startJob(int id, String commit, boolean merged){
		QueueTaskFuture<?> build = trigger.startJob(new GhprbCause(commit, id, merged));
		if(build == null){
			Logger.getLogger(GhprbRepo.class.getName()).log(Level.SEVERE, "Job didn't started");
			return;
		}
		builds.add(new GhprbBuild(this, id, build, true));
	}
}
