package org.jenkinsci.plugins.ghprb;

import antlr.ANTLRException;
import com.coravy.hudson.plugins.github.GithubProjectProperty;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.kohsuke.github.GHAuthorization;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public final class GhprbTrigger extends Trigger<AbstractProject<?, ?>> {
	private static final Logger logger = Logger.getLogger(GhprbTrigger.class.getName());
	private final String adminlist;
	private       String whitelist;
	private final String orgslist;
	private final String cron;
	private final String triggerPhrase;
	private final Boolean onlyTriggerPhrase;
	private final Boolean useGitHubHooks;
	private final Boolean permitAll;
	private Boolean autoCloseFailedPullRequests;

	transient private Ghprb ml;

	@DataBoundConstructor
	public GhprbTrigger(String adminlist, String whitelist, String orgslist, String cron, String triggerPhrase,
			Boolean onlyTriggerPhrase, Boolean useGitHubHooks, Boolean permitAll, Boolean autoCloseFailedPullRequests) throws ANTLRException{
		super(cron);
		this.adminlist = adminlist;
		this.whitelist = whitelist;
		this.orgslist = orgslist;
		this.cron = cron;
		this.triggerPhrase = triggerPhrase;
		this.onlyTriggerPhrase = onlyTriggerPhrase;
		this.useGitHubHooks = useGitHubHooks;
		this.permitAll = permitAll;
		this.autoCloseFailedPullRequests = autoCloseFailedPullRequests;
	}

	@Override
	public void start(AbstractProject<?, ?> project, boolean newInstance) {
		if (project.getProperty(GithubProjectProperty.class) == null) {
			logger.log(Level.INFO, "GitHub project not set up, cannot start trigger for job " + project.getName());
			return;
		}
		try{
			ml = Ghprb.getBuilder()
			     .setProject(project)
			     .setTrigger(this)
			     .setPulls(DESCRIPTOR.getPullRequests(project.getFullName()))
			     .build();
		}catch(IllegalStateException ex){
			logger.log(Level.SEVERE, "Can't start trigger", ex);
			return;
		}

		super.start(project, newInstance);
	}

	public Ghprb getGhprb(){
		return ml;
	}

	@Override
	public void stop() {
		if(ml != null){
			ml.stop();
			ml = null;
		}
		super.stop();
	}

	public QueueTaskFuture<?> startJob(GhprbCause cause){
		ArrayList<ParameterValue> values = getDefaultParameters();
		if(cause.isMerged()){
			values.add(new StringParameterValue("sha1","origin/pr/" + cause.getPullID() + "/merge"));
		}else{
			values.add(new StringParameterValue("sha1",cause.getCommit()));
		}
		values.add(new StringParameterValue("ghprbActualCommit",cause.getCommit()));
		values.add(new StringParameterValue("ghprbPullId",String.valueOf(cause.getPullID())));
		values.add(new StringParameterValue("ghprbTargetBranch",String.valueOf(cause.getTargetBranch())));
		// it's possible the GHUser doesn't have an associated email address
		values.add(new StringParameterValue("ghprbPullAuthorEmail",cause.getAuthorEmail() != null ? cause.getAuthorEmail() : ""));

		return this.job.scheduleBuild2(0,cause,new ParametersAction(values));
	}

	private ArrayList<ParameterValue> getDefaultParameters() {
		ArrayList<ParameterValue> values = new ArrayList<ParameterValue>();
		ParametersDefinitionProperty pdp = this.job.getProperty(ParametersDefinitionProperty.class);
		if (pdp != null) {
			for(ParameterDefinition pd :  pdp.getParameterDefinitions()) {
				if (pd.getName().equals("sha1"))
					continue;
				values.add(pd.getDefaultParameterValue());
			}
		}
		return values;
	}

	@Override
	public void run() {
		if (ml == null) return;
		ml.run();
		DESCRIPTOR.save();
	}

	public void addWhitelist(String author){
		whitelist = whitelist + " " + author;
		try {
			this.job.save();
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Failed to save new whitelist", ex);
		}
	}

	public String getAdminlist() {
		if(adminlist == null){
			return "";
		}
		return adminlist;
	}

	public String getWhitelist() {
		if(whitelist == null){
			return "";
		}
		return whitelist;
	}

	public String getOrgslist() {
		if(orgslist == null){
			return "";
		}
		return orgslist;
	}

	public String getCron() {
		return cron;
	}

	public String getTriggerPhrase() {
		if(triggerPhrase == null){
			return "";
		}
		return triggerPhrase;
	}

	public Boolean getOnlyTriggerPhrase() {
		return onlyTriggerPhrase != null && onlyTriggerPhrase;
	}

	public Boolean getUseGitHubHooks() {
		return useGitHubHooks != null && useGitHubHooks;
	}

	public Boolean getPermitAll() {
		return permitAll != null && permitAll;
	}

	public Boolean isAutoCloseFailedPullRequests() {
		if(autoCloseFailedPullRequests == null){
			Boolean autoClose = getDescriptor().getAutoCloseFailedPullRequests();
			return (autoClose != null && autoClose);
		}else{
			return autoCloseFailedPullRequests;
		}
	}

	public static GhprbTrigger getTrigger(AbstractProject p){
		Trigger trigger = p.getTrigger(GhprbTrigger.class);
		if(trigger == null || (!(trigger instanceof GhprbTrigger))) return null;
		return (GhprbTrigger) trigger;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return DESCRIPTOR;
	}

	public static DescriptorImpl getDscp(){
		return DESCRIPTOR;
	}

	@Extension
	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

	public static final class DescriptorImpl extends TriggerDescriptor{
		private String serverAPIUrl = "https://api.github.com";
		private String username;
		private String password;
		private String accessToken;
		private String adminlist;
		private String publishedURL;
		private String requestForTestingPhrase;
		private String whitelistPhrase = ".*add\\W+to\\W+whitelist.*";
		private String okToTestPhrase = ".*ok\\W+to\\W+test.*";
		private String retestPhrase = ".*test\\W+this\\W+please.*";
		private String cron = "*/5 * * * *";
		private Boolean useComments = false;
		private String unstableAs = GHCommitState.FAILURE.name();
		private Boolean autoCloseFailedPullRequests = false;
		private String msgSuccess = "Test PASSed.";
		private String msgFailure = "Test FAILed.";

		private transient GhprbGitHub gh;

		// map of jobs (by their fullName) abd their map of pull requests
		private Map<String, Map<Integer,GhprbPullRequest>> jobs;

		public DescriptorImpl(){
			load();
			if(jobs == null){
				jobs = new HashMap<String, Map<Integer,GhprbPullRequest>>();
			}
		}

		@Override
		public boolean isApplicable(Item item) {
			return item instanceof AbstractProject;
		}

		@Override
		public String getDisplayName() {
			return "GitHub pull requests builder";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			serverAPIUrl = formData.getString("serverAPIUrl");
			username = formData.getString("username");
			password = formData.getString("password");
			accessToken = formData.getString("accessToken");
			adminlist = formData.getString("adminlist");
			publishedURL = formData.getString("publishedURL");
			requestForTestingPhrase = formData.getString("requestForTestingPhrase");
			whitelistPhrase = formData.getString("whitelistPhrase");
			okToTestPhrase = formData.getString("okToTestPhrase");
			retestPhrase = formData.getString("retestPhrase");
			cron = formData.getString("cron");
			useComments = formData.getBoolean("useComments");
			unstableAs = formData.getString("unstableAs");
			autoCloseFailedPullRequests = formData.getBoolean("autoCloseFailedPullRequests");
			msgSuccess = formData.getString("msgSuccess");
			msgFailure = formData.getString("msgFailure");
			save();
			gh = new GhprbGitHub();
			return super.configure(req,formData);
		}

		// GitHub username may only contain alphanumeric characters or dashes and cannot begin with a dash
		private static final Pattern adminlistPattern = Pattern.compile("((\\p{Alnum}[\\p{Alnum}-]*)|\\s)*");
		public FormValidation doCheckAdminlist(@QueryParameter String value)
				throws ServletException {
			if(!adminlistPattern.matcher(value).matches()){
				return FormValidation.error("GitHub username may only contain alphanumeric characters or dashes and cannot begin with a dash. Separate them with whitespece.");
			}
			return FormValidation.ok();
		}

		public FormValidation doCheckCron(@QueryParameter String value){
			return (new TimerTrigger.DescriptorImpl().doCheckSpec(value));
		}

		public FormValidation doCheckServerAPIUrl(@QueryParameter String value){
			if("https://api.github.com".equals(value)) return FormValidation.ok();
			if(value.endsWith("/api/v3")) return FormValidation.ok();
			return FormValidation.warning("GitHub api url is \"https://api.github.com\". GitHub enterprise api url ends with \"/api/v3\"");
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

		public String getRequestForTestingPhrase() {
			return requestForTestingPhrase;
		}

		public String getWhitelistPhrase() {
			return whitelistPhrase;
		}

		public String getOkToTestPhrase() {
			return okToTestPhrase;
		}

		public String getRetestPhrase() {
			return retestPhrase;
		}

		public String getCron() {
			return cron;
		}

		public Boolean getUseComments() {
			return useComments;
		}

		public Boolean getAutoCloseFailedPullRequests() {
			return autoCloseFailedPullRequests;
		}

		public String getServerAPIUrl() {
			return serverAPIUrl;
		}

		public String getUnstableAs() {
			return unstableAs;
		}

		public String getMsgSuccess() {
			if(msgSuccess == null){
				return "Test PASSed.";
			}
			return msgSuccess;
		}

		public String getMsgFailure() {
			if(msgFailure == null){
				return "Test FAILed.";
			}
			return msgFailure;
		}

		public boolean isUseComments(){
			return (useComments != null && useComments);
		}

		public GhprbGitHub getGitHub(){
			if(gh == null){
				gh = new GhprbGitHub();
			}
			return gh;
		}

		private Map<Integer, GhprbPullRequest> getPullRequests(String projectName) {
			Map<Integer, GhprbPullRequest> ret;
			if(jobs.containsKey(projectName)){
				 ret = jobs.get(projectName);
			}else{
				ret = new HashMap<Integer, GhprbPullRequest>();
				jobs.put(projectName, ret);
			}
			return ret;
		}

		public FormValidation doCreateApiToken(
				@QueryParameter("username") final String username,
		        @QueryParameter("password") final String password){
			try{
				GitHub gh = GitHub.connectToEnterprise(this.serverAPIUrl, username, password);
				GHAuthorization token = gh.createToken(Arrays.asList(GHAuthorization.REPO_STATUS, GHAuthorization.REPO), "Jenkins GitHub Pull Request Builder", null);
				return FormValidation.ok("Access token created: " + token.getToken());
			}catch(IOException ex){
				return FormValidation.error("GitHub API token couldn't be created" + ex.getMessage());
			}
		}
	}
}
