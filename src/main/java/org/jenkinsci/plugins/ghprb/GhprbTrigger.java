package org.jenkinsci.plugins.ghprb;

import antlr.ANTLRException;
import com.coravy.hudson.plugins.github.GithubProjectProperty;
import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.*;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.git.RevisionParameterAction;
import hudson.plugins.git.util.BuildData;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.github.GHAuthorization;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbTrigger extends Trigger<AbstractProject<?, ?>> {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    private static final Logger logger = Logger.getLogger(GhprbTrigger.class.getName());
    private final String adminlist;
    private final String orgslist;
    private final String cron;
    private final String triggerPhrase;
    private final Boolean onlyTriggerPhrase;
    private final Boolean useGitHubHooks;
    private final Boolean permitAll;
    private String whitelist;
    private Boolean autoCloseFailedPullRequests;
    private List<GhprbBranch> whiteListTargetBranches;
    private transient Ghprb helper;

    @DataBoundConstructor
    public GhprbTrigger(String adminlist,
                        String whitelist,
                        String orgslist,
                        String cron,
                        String triggerPhrase,
                        Boolean onlyTriggerPhrase,
                        Boolean useGitHubHooks,
                        Boolean permitAll,
                        Boolean autoCloseFailedPullRequests,
                        List<GhprbBranch> whiteListTargetBranches) throws ANTLRException {
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
        this.whiteListTargetBranches = whiteListTargetBranches;
    }

    public static GhprbTrigger extractTrigger(AbstractProject p) {
        Trigger trigger = p.getTrigger(GhprbTrigger.class);
        if (trigger == null || (!(trigger instanceof GhprbTrigger))) {
            return null;
        }
        return (GhprbTrigger) trigger;
    }

    public static DescriptorImpl getDscp() {
        return DESCRIPTOR;
    }

    @Override
    public void start(AbstractProject<?, ?> project, boolean newInstance) {
        if (project.getProperty(GithubProjectProperty.class) == null) {
            logger.log(Level.INFO, "GitHub project not set up, cannot start trigger for job {0}", project.getName());
            return;
        }
        try {
            helper = createGhprb(project);
        } catch (IllegalStateException ex) {
            logger.log(Level.SEVERE, "Can't start trigger", ex);
            return;
        }

        logger.log(Level.INFO, "Starting trigger");
        super.start(project, newInstance);
        helper.init();
    }

    Ghprb createGhprb(AbstractProject<?, ?> project) {
        return new Ghprb(project, this, getDescriptor().getPullRequests(project.getName()));
    }

    @Override
    public void stop() {
        if (helper != null) {
            helper.stop();
            helper = null;
        }
        super.stop();
    }

    @Override
    public void run() {
        // triggers are always triggered on the cron, but we just no-op if we are using GitHub hooks.
        if (getUseGitHubHooks()) {
            return;
        }
        helper.run();
        getDescriptor().save();
    }

    public QueueTaskFuture<?> startJob(GhprbCause cause, GhprbRepository repo) {
        ArrayList<ParameterValue> values = getDefaultParameters();
        final String commitSha = cause.isMerged() ? "origin/pr/" + cause.getPullID() + "/merge" : cause.getCommit();
        values.add(new StringParameterValue("sha1", commitSha));
        values.add(new StringParameterValue("ghprbActualCommit", cause.getCommit()));
        final StringParameterValue pullIdPv = new StringParameterValue("ghprbPullId", String.valueOf(cause.getPullID()));
        values.add(pullIdPv);
        values.add(new StringParameterValue("ghprbTargetBranch", String.valueOf(cause.getTargetBranch())));
        values.add(new StringParameterValue("ghprbSourceBranch", String.valueOf(cause.getSourceBranch())));
        // it's possible the GHUser doesn't have an associated email address
        values.add(new StringParameterValue("ghprbPullAuthorEmail", cause.getAuthorEmail() != null ? cause.getAuthorEmail() : ""));
        values.add(new StringParameterValue("ghprbPullDescription", String.valueOf(cause.getShortDescription())));
        values.add(new StringParameterValue("ghprbPullTitle", String.valueOf(cause.getTitle())));
        values.add(new StringParameterValue("ghprbPullLink", String.valueOf(cause.getUrl())));

        // add the previous pr BuildData as an action so that the correct change log is generated by the GitSCM plugin
        // note that this will be removed from the Actions list after the job is completed so that the old (and incorrect)
        // one isn't there
        return this.job.scheduleBuild2(job.getQuietPeriod(), cause, new ParametersAction(values), findPreviousBuildForPullId(pullIdPv), new RevisionParameterAction(commitSha));
    }

    /**
     * Find the previous BuildData for the given pull request number; this may return null
     */
    private BuildData findPreviousBuildForPullId(StringParameterValue pullIdPv) {
        // find the previous build for this particular pull request, it may not be the last build
        for (Run<?, ?> r : job.getBuilds()) {
            ParametersAction pa = r.getAction(ParametersAction.class);
            if (pa != null) {
                for (ParameterValue pv : pa.getParameters()) {
                    if (pv.equals(pullIdPv)) {
                        for (BuildData bd : r.getActions(BuildData.class)) {
                            return bd;
                        }
                    }
                }
            }
        }
        return null;
    }

    private ArrayList<ParameterValue> getDefaultParameters() {
        ArrayList<ParameterValue> values = new ArrayList<ParameterValue>();
        ParametersDefinitionProperty pdp = this.job.getProperty(ParametersDefinitionProperty.class);
        if (pdp != null) {
            for (ParameterDefinition pd : pdp.getParameterDefinitions()) {
                if (pd.getName().equals("sha1"))
                    continue;
                values.add(pd.getDefaultParameterValue());
            }
        }
        return values;
    }

    public void addWhitelist(String author) {
        whitelist = whitelist + " " + author;
        try {
            this.job.save();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to save new whitelist", ex);
        }
    }

    public String getAdminlist() {
        if (adminlist == null) {
            return "";
        }
        return adminlist;
    }

    public String getWhitelist() {
        if (whitelist == null) {
            return "";
        }
        return whitelist;
    }

    public String getOrgslist() {
        if (orgslist == null) {
            return "";
        }
        return orgslist;
    }

    public String getCron() {
        return cron;
    }

    public String getTriggerPhrase() {
        if (triggerPhrase == null) {
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
        if (autoCloseFailedPullRequests == null) {
            Boolean autoClose = getDescriptor().getAutoCloseFailedPullRequests();
            return (autoClose != null && autoClose);
        } else {
            return autoCloseFailedPullRequests;
        }
    }

    public List<GhprbBranch> getWhiteListTargetBranches() {
        if (whiteListTargetBranches == null) {
            return new ArrayList<GhprbBranch>();
        }
        return whiteListTargetBranches;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }


    @VisibleForTesting
    void setHelper(Ghprb helper) {
        this.helper = helper;
    }

    public GhprbBuilds getBuilds() {
        return helper.getBuilds();
    }

    public GhprbRepository getRepository() {
        return helper.getRepository();
    }

    public static final class DescriptorImpl extends TriggerDescriptor {
        // GitHub username may only contain alphanumeric characters or dashes and cannot begin with a dash
        private static final Pattern adminlistPattern = Pattern.compile("((\\p{Alnum}[\\p{Alnum}-]*)|\\s)*");
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
        // TODO what is this for? seems to be unused (compared to instance field of actual Trigger)
        private String cron = "H/5 * * * *";
        private Boolean useComments = false;
        private int logExcerptLines = 0;
        private String unstableAs = GHCommitState.FAILURE.name();
        private Boolean autoCloseFailedPullRequests = false;
        private String msgSuccess = "Test PASSed.";
        private String msgFailure = "Test FAILed.";
        private List<GhprbBranch> whiteListTargetBranches;
        private transient GhprbGitHub gh;
        // map of jobs (by their fullName) abd their map of pull requests
        private Map<String, ConcurrentMap<Integer, GhprbPullRequest>> jobs;

        public DescriptorImpl() {
            load();
            if (jobs == null) {
                jobs = new HashMap<String, ConcurrentMap<Integer, GhprbPullRequest>>();
            }
        }

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof AbstractProject;
        }

        @Override
        public String getDisplayName() {
            return "GitHub Pull Request Builder";
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
            logExcerptLines = formData.getInt("logExcerptLines");
            unstableAs = formData.getString("unstableAs");
            autoCloseFailedPullRequests = formData.getBoolean("autoCloseFailedPullRequests");
            msgSuccess = formData.getString("msgSuccess");
            msgFailure = formData.getString("msgFailure");
            save();
            gh = new GhprbGitHub();
            return super.configure(req, formData);
        }

        public FormValidation doCheckAdminlist(@QueryParameter String value) throws ServletException {
            if (!adminlistPattern.matcher(value).matches()) {
                return FormValidation.error("GitHub username may only contain alphanumeric characters or dashes and cannot begin with a dash. Separate them with whitespaces.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckServerAPIUrl(@QueryParameter String value) {
            if ("https://api.github.com".equals(value)) {
                return FormValidation.ok();
            }
            if (value.endsWith("/api/v3") || value.endsWith("/api/v3/")) {
                return FormValidation.ok();
            }
            return FormValidation.warning("GitHub API URI is \"https://api.github.com\". GitHub Enterprise API URL ends with \"/api/v3\"");
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

        public int getlogExcerptLines() {
            return logExcerptLines;
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
            if (msgSuccess == null) {
                return "Test PASSed.";
            }
            return msgSuccess;
        }

        public String getMsgFailure() {
            if (msgFailure == null) {
                return "Test FAILed.";
            }
            return msgFailure;
        }

        public boolean isUseComments() {
            return (useComments != null && useComments);
        }

        public GhprbGitHub getGitHub() {
            if (gh == null) {
                gh = new GhprbGitHub();
            }
            return gh;
        }

        public ConcurrentMap<Integer, GhprbPullRequest> getPullRequests(String projectName) {
            ConcurrentMap<Integer, GhprbPullRequest> ret;
            if (jobs.containsKey(projectName)) {
                Map<Integer, GhprbPullRequest> map = jobs.get(projectName);
                if (!(map instanceof ConcurrentMap)) {
                    map = new ConcurrentHashMap<Integer, GhprbPullRequest>(map);
                }
                jobs.put(projectName, (ConcurrentMap<Integer, GhprbPullRequest>) map);
                ret = (ConcurrentMap<Integer, GhprbPullRequest>) map;
            } else {
                ret = new ConcurrentHashMap<Integer, GhprbPullRequest>();
                jobs.put(projectName, ret);
            }
            return ret;
        }

        public FormValidation doCreateApiToken(@QueryParameter("username") final String username, @QueryParameter("password") final String password) {
            try {
                GitHub gh = GitHub.connectToEnterprise(this.serverAPIUrl, username, password);
                GHAuthorization token = gh.createToken(Arrays.asList(GHAuthorization.REPO_STATUS, GHAuthorization.REPO), "Jenkins GitHub Pull Request Builder", null);
                return FormValidation.ok("Access token created: " + token.getToken());
            } catch (IOException ex) {
                return FormValidation.error("GitHub API token couldn't be created: " + ex.getMessage());
            }
        }

        public List<GhprbBranch> getWhiteListTargetBranches() {
            return whiteListTargetBranches;
        }
    }
}
