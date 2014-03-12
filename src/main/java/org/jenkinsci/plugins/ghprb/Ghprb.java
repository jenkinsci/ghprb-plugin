package org.jenkinsci.plugins.ghprb;

import com.coravy.hudson.plugins.github.GithubProjectProperty;
import hudson.model.AbstractProject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import org.kohsuke.github.GHUser;

/**
 * @author janinko
 */
public class Ghprb {
	private static final Logger logger = Logger.getLogger(Ghprb.class.getName());
	private static final Pattern githubUserRepoPattern = Pattern.compile("^(http[s]?://[^/]*)/([^/]*)/([^/]*).*");

	private HashSet<String>       admins;
	private HashSet<String>       whitelisted;
	private HashSet<String>       organisations;
	private String                triggerPhrase;
	private GhprbTrigger          trigger;
	private GhprbRepository       repository;
	private GhprbBuilds           builds;
	private AbstractProject<?, ?> project;
	private String                githubServer;

	private boolean checked = false;
	
	private final Pattern retestPhrasePattern;
	private final Pattern whitelistPhrasePattern;
	private final Pattern oktotestPhrasePattern;

	private Ghprb(){
		retestPhrasePattern = Pattern.compile(GhprbTrigger.getDscp().getRetestPhrase());
		whitelistPhrasePattern = Pattern.compile(GhprbTrigger.getDscp().getWhitelistPhrase());
		oktotestPhrasePattern = Pattern.compile(GhprbTrigger.getDscp().getOkToTestPhrase());
	}
	
	public static Builder getBuilder(){
		return new Builder();
	}

	public void addWhitelist(String author){
		logger.log(Level.INFO, "Adding {0} to whitelist", author);
		whitelisted.add(author);
		trigger.addWhitelist(author);
	}

	public GhprbBuilds getBuilds() {
		return builds;
	}

	public GhprbRepository getRepository() {
		return repository;
	}

	public GhprbGitHub getGitHub() {
		return trigger.getDescriptor().getGitHub();
	}

	void run() {
		if(trigger.getUseGitHubHooks() && checked){
			return;
		}
		checked = true;
		repository.check();
	}

	void stop() {
		repository = null;
		builds = null;
	}


	/*          INFO METHODS                */

	public String getHookUrl(){
		return Jenkins.getInstance().getRootUrl() + GhprbRootAction.URL + "/";
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

	public boolean isTriggerPhrase(String comment){
		return !triggerPhrase.equals("") && comment.contains(triggerPhrase);
	}

	public boolean ifOnlyTriggerPhrase() {
		return trigger.getOnlyTriggerPhrase();
	}

	public boolean isWhitelisted(GHUser user){
		return trigger.getPermitAll()
			|| whitelisted.contains(user.getLogin())
		    || admins.contains(user.getLogin())
		    || isInWhitelistedOrganisation(user);
	}

	public boolean isAdmin(String username){
		return admins.contains(username);
	}

	private boolean isInWhitelistedOrganisation(GHUser user) {
		for(String organisation : organisations){
			if(getGitHub().isUserMemberOfOrganization(organisation,user)){
				return true;
			}
		}
		return false;
	}

	String getGitHubServer() {
		return githubServer;
	}
	
	List<GhprbBranch> getWhiteListTargetBranches() {
		return trigger.getWhiteListTargetBranches();
	}


	/*               BUILDER                */

	public static class Builder{
		private Ghprb gml = new Ghprb();
		private String user;
		private String repo;
		private Map<Integer, GhprbPullRequest> pulls;

		public Builder setTrigger(GhprbTrigger trigger) {
			if(gml == null) return this;

			gml.trigger = trigger;
			gml.admins = new HashSet<String>(Arrays.asList(trigger.getAdminlist().split("\\s+")));
			gml.admins.remove("");
			gml.whitelisted = new HashSet<String>(Arrays.asList(trigger.getWhitelist().split("\\s+")));
			gml.whitelisted.remove("");
			gml.organisations = new HashSet<String>(Arrays.asList(trigger.getOrgslist().split("\\s+")));
			gml.organisations.remove("");
			gml.triggerPhrase = trigger.getTriggerPhrase();

			return this;
		}

		public Builder setPulls(Map<Integer, GhprbPullRequest> pulls) {
			if(gml == null) return this;
			this.pulls = pulls;
			return this;
		}

		public Builder setProject(AbstractProject<?, ?> project) {
			if(gml == null) return this;

			gml.project = project;
			GithubProjectProperty ghpp = project.getProperty(GithubProjectProperty.class);
			if(ghpp == null || ghpp.getProjectUrl() == null) {
				logger.log(Level.WARNING, "A github project url is required.");
				gml = null;
				return this;
			}
			String baseUrl = ghpp.getProjectUrl().baseUrl();
			Matcher m = githubUserRepoPattern.matcher(baseUrl);
			if(!m.matches()) {
				logger.log(Level.WARNING, "Invalid github project url: {0}", baseUrl);
				gml = null;
				return this;
			}
			gml.githubServer = m.group(1);
			user = m.group(2);
			repo = m.group(3);
			return this;
		}

		public Ghprb build(){
			if(gml == null || pulls == null || gml.trigger == null || gml.project == null){
				throw new IllegalStateException();
			}
			gml.repository = new GhprbRepository(user, repo, gml,pulls);
			gml.repository.init();
			if(gml.trigger.getUseGitHubHooks()){
				gml.repository.createHook();
			}
			gml.builds = new GhprbBuilds(gml.trigger,gml.repository);
			return gml;
		}
	}

}
