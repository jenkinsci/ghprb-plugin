package org.jenkinsci.plugins.ghprb;

import antlr.ANTLRException;
import com.coravy.hudson.plugins.github.GithubProjectProperty;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public final class GhprbTrigger extends Trigger<AbstractProject<?, ?>> {
	public final String adminlist;
	public String whitelist;
	public final String cron;
	
	private static Pattern githubUserRepoPattern = Pattern.compile("^http[s]?://([^/]*)/([^/]*)/([^/]*).*");
	private transient GhprbRepo repository;
	transient Map<Integer,GhprbPullRequest> pulls;
	transient boolean changed;
	transient HashSet<String> admins;
	transient HashSet<String> whitelisted;

	@DataBoundConstructor
    public GhprbTrigger(String adminlist, String whitelist, String cron) throws ANTLRException{
		super(cron);
		this.adminlist = adminlist;
		this.whitelist = whitelist;
		this.cron = cron;
	}

	@Override
	public void start(AbstractProject<?, ?> project, boolean newInstance) {
		String pname = project.getFullName();
		
		if(DESCRIPTOR.jobs.containsKey(pname)){
			pulls = DESCRIPTOR.jobs.get(pname);
		}else{
			pulls = new HashMap<Integer, GhprbPullRequest>();
			DESCRIPTOR.jobs.put(pname, pulls);
		}
		
		GithubProjectProperty ghpp = project.getProperty(GithubProjectProperty.class);
		if(ghpp.getProjectUrl() == null) {
			Logger.getLogger(GhprbTrigger.class.getName()).log(Level.WARNING, "A github project url is required.");
			return;
		}
		Matcher m = githubUserRepoPattern.matcher(ghpp.getProjectUrl().baseUrl());
		if(!m.matches()) {
			Logger.getLogger(GhprbTrigger.class.getName()).log(Level.WARNING, "Invalid github project url: " + ghpp.getProjectUrl().baseUrl());
			return;
		}
		String githubServer = m.group(1);
		String user = m.group(2);
		String repo = m.group(3);
		repository = new GhprbRepo(this, githubServer, user, repo);
		
		admins = new HashSet<String>(Arrays.asList(adminlist.split("\\s+")));
		whitelisted = new HashSet<String>(Arrays.asList(whitelist.split("\\s+")));
		
		super.start(project, newInstance);
	}

	@Override
	public void stop() {
		repository = null;
		super.stop();
	}
	
	public QueueTaskFuture<?> startJob(GhprbCause cause){
		StringParameterValue paramSha1;
		if(cause.isMerged()){
			paramSha1 = new StringParameterValue("sha1","origin/pr/" + cause.getPullID() + "/merge");
		}else{
			paramSha1 = new StringParameterValue("sha1",cause.getCommit());
		}
		return this.job.scheduleBuild2(0,cause,new ParametersAction(paramSha1));
	}

	@Override
	public void run() {
		changed = false;
		try {
			repository.check(pulls);
		} catch (IOException ex) {
			Logger.getLogger(GhprbTrigger.class.getName()).log(Level.SEVERE, null, ex);
		}
		if(changed){
			try {
				this.job.save();
			} catch (IOException ex) {
				Logger.getLogger(GhprbTrigger.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
		DESCRIPTOR.save();
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return DESCRIPTOR;
	}
	
	@Extension
	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
	
	public static final class DescriptorImpl extends TriggerDescriptor{
		private String username;
		private String password;
		private String accessToken;
		private String adminlist;
		private String publishedURL;
		private String whitelistPhrase;
		private String retestPhrase;
		private String cron;
		
		// map of jobs (by their fullName) abd their map of pull requests
		private Map<String, Map<Integer,GhprbPullRequest>> jobs;
		
		public DescriptorImpl(){
			load();
			if(jobs ==null){
				jobs = new HashMap<String, Map<Integer,GhprbPullRequest>>();
			}
		}

		@Override
		public boolean isApplicable(Item item) {
            return item instanceof AbstractProject;
		}

		@Override
		public String getDisplayName() {
			return "Github pull requests builder";
		}
	
		
		@Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			username = formData.getString("username");
			password = formData.getString("password");
			accessToken = formData.getString("accessToken");
			adminlist = formData.getString("adminlist");
			publishedURL = formData.getString("publishedURL");
			whitelistPhrase = formData.getString("whitelistPhrase");
			retestPhrase = formData.getString("retestPhrase");
			cron = formData.getString("cron");
            save();
            return super.configure(req,formData);
        }
		
		// Github username may only contain alphanumeric characters or dashes and cannot begin with a dash
		private static Pattern adminlistPattern = Pattern.compile("((\\p{Alnum}[\\p{Alnum}-]*)|\\s)*");
		public FormValidation doCheckAdminlist(@QueryParameter String value)
                throws IOException, ServletException {
			if(!adminlistPattern.matcher(value).matches()){
				return FormValidation.error("Github username may only contain alphanumeric characters or dashes and cannot begin with a dash. Separate them with whitespece.");
			}
            return FormValidation.ok();
        }
		
		
		public FormValidation doCheckCron(@QueryParameter String value){
            return (new TimerTrigger.DescriptorImpl().doCheckSpec(value));
        }

		public String getUsername() {
			return username;
		}

		public String getPassword() {
			return password;
		}

		public String getAccessToken() {
			return accessToken;
		}

		public String getAdminlist() {
			return adminlist;
		}

		public String getPublishedURL() {
			return publishedURL;
		}

		public String getWhitelistPhrase() {
			return whitelistPhrase;
		}

		public String getRetestPhrase() {
			return retestPhrase;
		}

		public String getCron() {
			return cron;
		}

		public static Pattern getAdminlistPattern() {
			return adminlistPattern;
		}
	}
}
