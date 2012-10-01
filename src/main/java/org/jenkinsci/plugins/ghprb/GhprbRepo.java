package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
	private GhprbTrigger trigger;
	private GitHub gh;
	private GHRepository repo;
	private Pattern retestPhrasePattern;
	private Pattern whitelistPhrasePattern;
	private String reponame;
	private HashMap<Integer, QueueTaskFuture<?>> queuedBuilds;
	private HashMap<Integer, QueueTaskFuture<?>> runningBuilds;
	
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
		queuedBuilds = new HashMap<Integer, QueueTaskFuture<?>>();
		runningBuilds = new HashMap<Integer, QueueTaskFuture<?>>();
	}
	
	public void check(Map<Integer,GhprbPullRequest> pulls) throws IOException{
		if(repo == null) repo = gh.getRepository(reponame);
		List<GHPullRequest> prs = repo.getPullRequests(GHIssueState.OPEN);
		Set<Integer> closedPulls = new HashSet<Integer>(pulls.keySet());

		for(GHPullRequest pr : prs){
			Integer id = pr.getNumber();
			GhprbPullRequest pull;
			if(pulls.containsKey(id)){
				pull = pulls.get(id);
			}else{
				pull = new GhprbPullRequest(pr);
				pulls.put(id, pull);
			}
			pull.check(pr,this);
			closedPulls.remove(id);
		}
		
		removeClosed(closedPulls, pulls);
		checkBuilds();
	}
	
	private void removeClosed(Set<Integer> closedPulls, Map<Integer,GhprbPullRequest> pulls) throws IOException {
		if(closedPulls.isEmpty()) return;
		
		for(Integer id : closedPulls){
			GHPullRequest pr = repo.getPullRequest(id);
			pulls.remove(id);
		}
	}
	
	private void checkBuilds() throws IOException{
		Iterator<Entry<Integer, QueueTaskFuture<?>>> it;
		it = queuedBuilds.entrySet().iterator();
		while(it.hasNext()){
			Entry<Integer, QueueTaskFuture<?>> e =it.next();
			if(e.getValue().getStartCondition().isDone()){
				it.remove();
				AbstractBuild<?,?> build;
				try {
					build = (AbstractBuild<?, ?>) e.getValue().getStartCondition().get();
				} catch (Exception ex) {
					Logger.getLogger(GhprbRepo.class.getName()).log(Level.SEVERE, null, ex);
					continue;
				}
				createCommitStatus(build, GHCommitState.PENDING, "Build started");
				runningBuilds.put(e.getKey(), e.getValue());
			}
		}
		
		it = runningBuilds.entrySet().iterator();
		while(it.hasNext()){
			Entry<Integer, QueueTaskFuture<?>> e =it.next();
			if(e.getValue().isDone()){
				it.remove();
				AbstractBuild<?,?> build;
				try {
					build = (AbstractBuild<?, ?>) e.getValue().get();
				} catch (Exception ex) {
					Logger.getLogger(GhprbRepo.class.getName()).log(Level.SEVERE, null, ex);
					continue;
				}
				GHCommitState state;
				if(build.getResult() == Result.SUCCESS){
					state = GHCommitState.SUCCESS;
				}else{
					state = GHCommitState.FAILURE;
				}
				createCommitStatus(build, state, "Build finished");
				String publishedURL = trigger.getDescriptor().getPublishedURL();
				if (publishedURL != null && !publishedURL.isEmpty()) {
					addComment(e.getKey(), "Build results will soon be (or already are) available at: " + publishedURL + build.getUrl());
				}
			}
		}
	}

	public void createCommitStatus(AbstractBuild<?,?> build, GHCommitState state, String message) throws IOException{
		String sha1 = build.getCause(GhprbCause.class).getCommit();
		createCommitStatus(sha1, state, Jenkins.getInstance().getRootUrl() + build.getUrl(), message);
	}

	public void createCommitStatus(String sha1, GHCommitState state, String url, String message) throws IOException{
		System.out.println("Setting status of " + sha1 + " to " + state + " with url "+ url + " and mesage: "+ message);
		repo.createCommitStatus(sha1, state, url, message);
	}

	public boolean cancelBuild(int id) {
		if(queuedBuilds.containsKey(id)){
			try {
				Run<?,?> build = (Run) queuedBuilds.get(id).waitForStart();
				if(build.getExecutor() == null) return false;
				build.getExecutor().interrupt();
				queuedBuilds.remove(id);
				return true;
			} catch (Exception ex) {
				Logger.getLogger(GhprbRepo.class.getName()).log(Level.WARNING, null, ex);
				return false;
			}
		}else if(runningBuilds.containsKey(id)){
			try {
				Run<?,?> build = (Run) runningBuilds.get(id).waitForStart();
				if(build.getExecutor() == null) return false;
				build.getExecutor().interrupt();
				runningBuilds.remove(id);
				return true;
			} catch (Exception ex) {
				Logger.getLogger(GhprbRepo.class.getName()).log(Level.WARNING, null, ex);
				return false;
			}
		}else{
			return false;
		}
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
	
	public void addComment(int id, String comment) throws IOException{
		repo.getPullRequest(id).comment(comment);
	}

	public void addWhitelist(String author) {
		trigger.whitelist = trigger.whitelist + " " + author;
		trigger.whitelisted.add(author);
		trigger.changed = true;
	}
	
	public boolean isMe(String username){
		return trigger.getDescriptor().getUsername().equals(username);
	}
	
	public void startJob(int id, String commit){
		QueueTaskFuture<?> build = trigger.startJob(new GhprbCause(commit, id));
		if(build == null){
			System.out.println("WUUUT?!!");
			return;
		}
		queuedBuilds.put(id,build);
	}
	
}
