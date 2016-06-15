package org.jenkinsci.plugins.ghprb;

import antlr.ANTLRException;

import com.coravy.hudson.plugins.github.GithubProjectProperty;
import com.google.common.annotations.VisibleForTesting;

import hudson.Extension;
import hudson.Util;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Saveable;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.git.util.BuildData;
import hudson.triggers.TriggerDescriptor;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.ghprb.extensions.GhprbBuildStep;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommitStatus;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalDefault;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildLog;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildResultMessage;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildStatus;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbPublishJenkinsUrl;
import org.jenkinsci.plugins.ghprb.extensions.status.GhprbSimpleStatus;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GHEventPayload.IssueComment;
import org.kohsuke.github.GHEventPayload.PullRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Honza Brázdil jbrazdil@redhat.com
 */
public class GhprbTrigger extends GhprbTriggerBackwardsCompatible {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    private static final Logger logger = Logger.getLogger(GhprbTrigger.class.getName());
    private final String adminlist;
    private final Boolean allowMembersOfWhitelistedOrgsAsAdmin;
    private final String orgslist;
    private final String cron;
    private final String buildDescTemplate;
    private final Boolean onlyTriggerPhrase;
    private final Boolean useGitHubHooks;
    private final Boolean permitAll;
    private String whitelist;
    private Boolean autoCloseFailedPullRequests;
    private Boolean displayBuildErrorsOnDownstreamBuilds;
    private List<GhprbBranch> whiteListTargetBranches;
    private List<GhprbBranch> blackListTargetBranches;
    private String gitHubAuthId;
    private String triggerPhrase;
    private String skipBuildPhrase;
    

    private transient Ghprb helper;
    private transient GhprbRepository repository;
    private transient GhprbBuilds builds;
    private transient GhprbGitHub ghprbGitHub;
    
    private DescribableList<GhprbExtension, GhprbExtensionDescriptor> extensions = new DescribableList<GhprbExtension, GhprbExtensionDescriptor>(Saveable.NOOP);
    
    public DescribableList<GhprbExtension, GhprbExtensionDescriptor> getExtensions() {
        if (extensions == null) {
            extensions = new DescribableList<GhprbExtension, GhprbExtensionDescriptor>(Saveable.NOOP,Util.fixNull(extensions));
            extensions.add(new GhprbSimpleStatus());
        }
        return extensions;
    }
    
    private void setExtensions(List<GhprbExtension> extensions) {
        DescribableList<GhprbExtension, GhprbExtensionDescriptor> rawList = new DescribableList<GhprbExtension, GhprbExtensionDescriptor>(
                Saveable.NOOP,Util.fixNull(extensions));
        
        // Filter out items that we only want one of, like the status updater.
        this.extensions = Ghprb.onlyOneEntry(rawList, 
                                              GhprbCommitStatus.class
                                            );
        
        // Make sure we have at least one of the types we need one of.
        for (GhprbExtension ext : getDescriptor().getExtensions()) {
            if (ext instanceof GhprbGlobalDefault) {
                Ghprb.addIfMissing(this.extensions, Ghprb.getGlobal(ext.getClass()), ext.getClass());
            }
        }
    }

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
            Boolean displayBuildErrorsOnDownstreamBuilds,
            String commentFilePath,
            String skipBuildPhrase,
            List<GhprbBranch> whiteListTargetBranches,
            List<GhprbBranch> blackListTargetBranches,
            Boolean allowMembersOfWhitelistedOrgsAsAdmin, 
            String msgSuccess, 
            String msgFailure, 
            String commitStatusContext,
            String gitHubAuthId,
            String buildDescTemplate,
            List<GhprbExtension> extensions
            ) throws ANTLRException {
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
        this.displayBuildErrorsOnDownstreamBuilds = displayBuildErrorsOnDownstreamBuilds;
        this.skipBuildPhrase = skipBuildPhrase;
        this.whiteListTargetBranches = whiteListTargetBranches;
        this.blackListTargetBranches = blackListTargetBranches;
        this.gitHubAuthId = gitHubAuthId;
        this.allowMembersOfWhitelistedOrgsAsAdmin = allowMembersOfWhitelistedOrgsAsAdmin;
        this.buildDescTemplate = buildDescTemplate;
        setExtensions(extensions);
        configVersion = latestVersion;
    }

    @Override
    public Object readResolve() {
        convertPropertiesToExtensions();
        checkGitHubApiAuth();
        return this;
    }
    


    @SuppressWarnings("deprecation")
    private void checkGitHubApiAuth() {
        if (gitHubApiAuth != null) {
            gitHubAuthId = gitHubApiAuth.getId();
            gitHubApiAuth = null;
        }
    }
    
    public static DescriptorImpl getDscp() {
        return DESCRIPTOR;
    }

    @SuppressWarnings("deprecation")
    private void initState() throws IOException {

        final GithubProjectProperty ghpp = super.job.getProperty(GithubProjectProperty.class);
        if (ghpp == null || ghpp.getProjectUrl() == null) {
            throw new IllegalStateException("A GitHub project url is required.");
        }
        String baseUrl = ghpp.getProjectUrl().baseUrl();
        Matcher m = Ghprb.githubUserRepoPattern.matcher(baseUrl);
        if (!m.matches()) {
            throw new IllegalStateException(String.format("Invalid GitHub project url: %s", baseUrl));
        }
        final String reponame = m.group(2);

        this.repository = new GhprbRepository(reponame, this);
        this.repository.load();
        
        Map<Integer, GhprbPullRequest> pulls = this.pullRequests;
        this.pullRequests = null;

        try {
            Map<Integer, GhprbPullRequest> prs = getDescriptor().getPullRequests(super.job.getFullName());
            if (prs != null) {
                prs = new ConcurrentHashMap<Integer, GhprbPullRequest>(prs);
                if (pulls == null) {
                    pulls = prs;
                } else {
                    pulls.putAll(prs);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unable to transfer map of pull requests", e);
        }
        
        if (pulls != null) {
            this.repository.addPullRequests(pulls);
            this.repository.save();
        }
        this.builds = new GhprbBuilds(this, repository);

        this.repository.init();
        this.ghprbGitHub = new GhprbGitHub(this);
    }
    

    @Override
    public void start(AbstractProject<?, ?> project, boolean newInstance) {
        // We should always start the trigger, and handle cases where we don't run in the run function.
        super.start(project, newInstance);
        
        String name = project.getFullName();
        
        if (project.isDisabled()) {
            logger.log(Level.FINE, "Project is disabled, not starting trigger for job " + name);
            return;
        }
        if (project.getProperty(GithubProjectProperty.class) == null) {
            logger.log(Level.INFO, "GitHub project property is missing the URL, cannot start ghprb trigger for job " + name);
            return;
        }
        try {
            initState();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Can't start ghprb trigger", ex);
            return;
        }

        logger.log(Level.INFO, "Starting the ghprb trigger for the {0} job; newInstance is {1}", 
                new String[] { name, String.valueOf(newInstance) });

        helper = new Ghprb(this);

        if (getUseGitHubHooks()) {
            if (GhprbTrigger.getDscp().getManageWebhooks()) {
                this.repository.createHook();
            }
            DESCRIPTOR.addRepoTrigger(getRepository().getName(), super.job);
        }
    }
    

    @Override
    public void stop() {
        String name = super.job != null ? super.job.getFullName(): "NOT STARTED";
        logger.log(Level.INFO, "Stopping the ghprb trigger for project {0}", name);
        if (this.repository != null) {
            String repo = this.repository.getName();
            if (!StringUtils.isEmpty(repo)) {
                DESCRIPTOR.removeRepoTrigger(repo, super.job);
            }
        }
        super.stop();
    }

    @Override
    public void run() {
        // triggers are always triggered on the cron, but we just no-op if we are using GitHub hooks.
        if (getUseGitHubHooks()) {
            logger.log(Level.FINE, "Use webHooks is set, so not running trigger");
            return;
        }

        if (!isActive()) {
            return;
        }

        logger.log(Level.FINE, "Running trigger for {0}", super.job.getFullName());
        
        this.repository.check();
    }
    

    public QueueTaskFuture<?> scheduleBuild(GhprbCause cause, GhprbRepository repo) {
        
        try {
        for (GhprbExtension ext : Ghprb.getJobExtensions(this, GhprbBuildStep.class)) {
            if (ext instanceof GhprbBuildStep) {
                ((GhprbBuildStep)ext).onScheduleBuild(super.job, cause);
            }
        }
        } catch(Exception e) {
            logger.log(Level.SEVERE, "Unable to execute extentions for scheduleBuild", e);
        }
        

        ArrayList<ParameterValue> values = getDefaultParameters();
        
        final String commitSha = cause.isMerged() ? "origin/pr/" + cause.getPullID() + "/merge" : cause.getCommit();
        values.add(new StringParameterValue("sha1", commitSha));
        values.add(new StringParameterValue("ghprbActualCommit", cause.getCommit()));
        String triggerAuthor = "";
        String triggerAuthorEmail = "";
        String triggerAuthorLogin = "";
        
        GhprbPullRequest pr = getRepository().getPullRequest(cause.getPullID());
        String lastBuildId = pr.getLastBuildId();
        BuildData buildData = null;
        if (!(job instanceof MatrixProject) && !StringUtils.isEmpty(lastBuildId)) {
            AbstractBuild<?, ?> lastBuild = job.getBuild(lastBuildId);
            if (lastBuild != null) {
                buildData = lastBuild.getAction(BuildData.class);
            }
        }

        try {
            triggerAuthor = getString(cause.getTriggerSender().getName(), "");
        } catch (Exception e) {}
        try {
            triggerAuthorEmail = getString(cause.getTriggerSender().getEmail(), "");
        } catch (Exception e) {}
        try {
            triggerAuthorLogin = getString(cause.getTriggerSender().getLogin(), "");
        } catch (Exception e) {}

        setCommitAuthor(cause, values);
        values.add(new StringParameterValue("ghprbAuthorRepoGitUrl", getString(cause.getAuthorRepoGitUrl(), "")));

        values.add(new StringParameterValue("ghprbTriggerAuthor", triggerAuthor));
        values.add(new StringParameterValue("ghprbTriggerAuthorEmail", triggerAuthorEmail));
        values.add(new StringParameterValue("ghprbTriggerAuthorLogin", triggerAuthorLogin));
        values.add(new StringParameterValue("ghprbTriggerAuthorLoginMention", !triggerAuthorLogin.isEmpty() ? "@"
                + triggerAuthorLogin : ""));
        final StringParameterValue pullIdPv = new StringParameterValue("ghprbPullId", String.valueOf(cause.getPullID()));
        values.add(pullIdPv);
        values.add(new StringParameterValue("ghprbTargetBranch", String.valueOf(cause.getTargetBranch())));
        values.add(new StringParameterValue("ghprbSourceBranch", String.valueOf(cause.getSourceBranch())));
        values.add(new StringParameterValue("GIT_BRANCH", String.valueOf(cause.getSourceBranch())));
        
        // it's possible the GHUser doesn't have an associated email address
        values.add(new StringParameterValue("ghprbPullAuthorEmail", getString(cause.getAuthorEmail(), "")));
        values.add(new StringParameterValue("ghprbPullAuthorLogin", String.valueOf(cause.getPullRequestAuthor().getLogin())));
        values.add(new StringParameterValue("ghprbPullAuthorLoginMention", "@" + cause.getPullRequestAuthor().getLogin()));
        
        values.add(new StringParameterValue("ghprbPullDescription", escapeText(String.valueOf(cause.getShortDescription()))));
        values.add(new StringParameterValue("ghprbPullTitle", String.valueOf(cause.getTitle())));
        values.add(new StringParameterValue("ghprbPullLink", String.valueOf(cause.getUrl())));
        values.add(new StringParameterValue("ghprbPullLongDescription", escapeText(String.valueOf(cause.getDescription()))));
        
        values.add(new StringParameterValue("ghprbCommentBody", escapeText(String.valueOf(cause.getCommentBody()))));
        
        values.add(new StringParameterValue("ghprbGhRepository", getString(cause.getRepositoryName(), "")));
        values.add(new StringParameterValue("ghprbCredentialsId", getString(cause.getCredentialsId(), "")));


        // add the previous pr BuildData as an action so that the correct change log is generated by the GitSCM plugin
        // note that this will be removed from the Actions list after the job is completed so that the old (and incorrect)
        // one isn't there
        return this.job.scheduleBuild2(job.getQuietPeriod(), cause, new ParametersAction(values), buildData);
    }

    private void setCommitAuthor(GhprbCause cause, ArrayList<ParameterValue> values) {
        String authorName = "";
        String authorEmail = "";
        if (cause.getCommitAuthor() != null) {
            authorName = getString(cause.getCommitAuthor().getName(), "");
            authorEmail = getString(cause.getCommitAuthor().getEmail(), "");
        }

        values.add(new StringParameterValue("ghprbActualCommitAuthor", authorName));
        values.add(new StringParameterValue("ghprbActualCommitAuthorEmail", authorEmail));
    }

    private String escapeText(String text) {
        return text.replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"");
    }
    
    private ArrayList<ParameterValue> getDefaultParameters() {
        ArrayList<ParameterValue> values = new ArrayList<ParameterValue>();
        ParametersDefinitionProperty pdp = this.job.getProperty(ParametersDefinitionProperty.class);
        if (pdp != null) {
            for (ParameterDefinition pd : pdp.getParameterDefinitions()) {
                values.add(pd.getDefaultParameterValue());
            }
        }
        return values;
    }

    private String getString(String actual, String d) {
        return actual == null ? d : actual;
    }

    public String getGitHubAuthId() {
        return gitHubAuthId == null ? "" : gitHubAuthId;
    }
    
    public GhprbGitHubAuth getGitHubApiAuth() {
        if (gitHubAuthId == null) {
            for (GhprbGitHubAuth auth: getDescriptor().getGithubAuth()){
                gitHubAuthId = auth.getId();
                getDescriptor().save();
                return auth;
            }
        }
        return getDescriptor().getGitHubAuth(gitHubAuthId);
    }
    

    public GitHub getGitHub() throws IOException {
        GhprbGitHubAuth auth = getGitHubApiAuth();
        return auth.getConnection(getActualProject());
    }
    
    public AbstractProject<?, ?> getActualProject() {
        return super.job;
    }
    public void addWhitelist(String author) {
        whitelist = whitelist + " " + author;
        try {
            this.job.save();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to save new whitelist", ex);
        }
    }
    
    public String getBuildDescTemplate() {
        return buildDescTemplate == null ? "" : buildDescTemplate;
    }

    public String getAdminlist() {
        if (adminlist == null) {
            return "";
        }
        return adminlist;
    }

    public Boolean getAllowMembersOfWhitelistedOrgsAsAdmin() {
        return allowMembersOfWhitelistedOrgsAsAdmin != null && allowMembersOfWhitelistedOrgsAsAdmin;
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

    public String getSkipBuildPhrase() {
        if (StringUtils.isEmpty(skipBuildPhrase)) {
            // if it's empty grab the global value
            return getDescriptor().getSkipBuildPhrase();
        }
        return skipBuildPhrase;
    }

    public Boolean getOnlyTriggerPhrase() {
        return onlyTriggerPhrase != null && onlyTriggerPhrase;
    }

    public Boolean getUseGitHubHooks() {
        return useGitHubHooks != null && useGitHubHooks;
    }
    
    public Ghprb getHelper() {
        if (helper == null) {
            helper = new Ghprb(this);
        }
        return helper;
    }

    public Boolean getPermitAll() {
        return permitAll != null && permitAll;
    }

    public Boolean getAutoCloseFailedPullRequests() {
        if (autoCloseFailedPullRequests == null) {
            Boolean autoClose = getDescriptor().getAutoCloseFailedPullRequests();
            return (autoClose != null && autoClose);
        }
        return autoCloseFailedPullRequests;
    }

    public Boolean getDisplayBuildErrorsOnDownstreamBuilds() {
        if (displayBuildErrorsOnDownstreamBuilds == null) {
            Boolean displayErrors = getDescriptor().getDisplayBuildErrorsOnDownstreamBuilds();
            return (displayErrors != null && displayErrors);
        }
        return displayBuildErrorsOnDownstreamBuilds;
    }

    private List<GhprbBranch> normalizeTargetBranches(List<GhprbBranch> branches) {
        if (branches == null || 
                (branches.size() == 1 && branches.get(0).getBranch().equals(""))) {
            return new ArrayList<GhprbBranch>();
        } else {
            return branches;
        }
    }

    public List<GhprbBranch> getWhiteListTargetBranches() {
        return normalizeTargetBranches(whiteListTargetBranches);
    }

    public List<GhprbBranch> getBlackListTargetBranches() {
        return normalizeTargetBranches(blackListTargetBranches);
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
        if (this.builds == null && this.isActive()) {
            this.builds = new GhprbBuilds(this, getRepository());
        }
        return this.builds;
    }
    
    public GhprbGitHub getGhprbGitHub() {
        if (this.ghprbGitHub == null && this.isActive()) {
            this.ghprbGitHub = new GhprbGitHub(this);
        }
        return this.ghprbGitHub;
    }

    public boolean isActive() {
        String name = super.job != null ? super.job.getFullName() : "NOT STARTED";
        boolean isActive = true;
        if (super.job == null) {
            logger.log(Level.FINE, "Project was never set, start was never run");
            isActive = false;
        } else if (super.job.isDisabled()) {
            logger.log(Level.FINE, "Project is disabled, ignoring trigger run call for job {0}", name);
            isActive = false;
        } else if (getRepository() == null) {
            logger.log(Level.SEVERE, "The ghprb trigger for {0} wasn''t properly started - repository is null", name);
            isActive = false;
        }
        
        return isActive;
    }
    
    public GhprbRepository getRepository() {
        if (this.repository == null && super.job != null && !super.job.isDisabled()) {
            try {
                this.initState();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Unable to init trigger state!", e);
            }
        }
        return this.repository;
    }
    
    public String getProjectName() {
        String projectName = super.job == null ? "NOT_STARTED" : super.job.getFullName();
        return projectName;
    }
    

    public boolean matchSignature(String body, String signature) {
        if (!isActive()) {
            return false;
        }
        GhprbGitHubAuth auth = getGitHubApiAuth();
        return auth == null ? false : auth.checkSignature(body, signature);
    }
    
    public void handleComment(IssueComment issueComment) throws IOException {
        GhprbRepository repo = getRepository();
        
        logger.log(Level.INFO, "Checking comment on PR #{0} for job {1}", new Object[] {issueComment.getIssue().getNumber(), getProjectName()});

        repo.onIssueCommentHook(issueComment);
    }

    public void handlePR(PullRequest pr) throws IOException {
        GhprbRepository repo = getRepository();

        logger.log(Level.INFO, "Checking PR #{0} for job {1}", new Object[] {pr.getNumber(), getProjectName()});

        repo.onPullRequestHook(pr);
    }


    public static final class DescriptorImpl extends TriggerDescriptor {
        // GitHub username may only contain alphanumeric characters or dashes and cannot begin with a dash
        private static final Pattern adminlistPattern = Pattern.compile("((\\p{Alnum}[\\p{Alnum}-]*)|\\s)*");
        
        private Integer configVersion;

        /**
         * These settings only really affect testing. When Jenkins calls configure() then the formdata will 
         * be used to replace all of these fields. Leaving them here is useful for
         * testing, but must not be confused with a default. They also should not be used as the default 
         * value in the global.jelly file as this value is dynamic and will not be
         * retained once configure() is called.
         */
        private String whitelistPhrase = ".*add\\W+to\\W+whitelist.*";
        private String okToTestPhrase = ".*ok\\W+to\\W+test.*";
        private String retestPhrase = ".*test\\W+this\\W+please.*";
        private String skipBuildPhrase = ".*\\[skip\\W+ci\\].*";
        private String cron = "H/5 * * * *";
        private Boolean useComments = false;
        private Boolean useDetailedComments = false;
        private Boolean manageWebhooks = true;
        private GHCommitState unstableAs = GHCommitState.FAILURE;
        private List<GhprbBranch> whiteListTargetBranches;
        private List<GhprbBranch> blackListTargetBranches;
        private Boolean autoCloseFailedPullRequests = false;
        private Boolean displayBuildErrorsOnDownstreamBuilds = false;
        
        private List<GhprbGitHubAuth> githubAuth;
        
        public GhprbGitHubAuth getGitHubAuth(String gitHubAuthId) {
            
            if (gitHubAuthId == null) {
                return getGithubAuth().get(0);
            }
            
            GhprbGitHubAuth firstAuth = null;
            for (GhprbGitHubAuth auth : getGithubAuth()) {
                if (firstAuth == null) {
                    firstAuth = auth;
                }
                if (auth.getId().equals(gitHubAuthId)) {
                    return auth;
                }
            }
            return firstAuth;
        }
        
        public List<GhprbGitHubAuth> getGithubAuth() {
            if (githubAuth == null || githubAuth.size() == 0) {
                githubAuth = new ArrayList<GhprbGitHubAuth>(1);
                githubAuth.add(new GhprbGitHubAuth(null, null, null, "Anonymous connection", null, null));
            }
            return githubAuth;
        }
        
        public List<GhprbGitHubAuth> getDefaultAuth(List<GhprbGitHubAuth> githubAuth) {
            if (githubAuth != null && githubAuth.size() > 0) {
                return githubAuth;
            }
            return getGithubAuth();
        }
        
        private String adminlist;
        
        private String requestForTestingPhrase;

        // map of jobs (by their fullName) and their map of pull requests
        private transient Map<String, Map<Integer, GhprbPullRequest>> jobs;
        
        /**
         *  map of jobs (by the repo name);  No need to keep the projects from shutdown to startup.
         *  New triggers will register here, and ones that are stopping will remove themselves.
         */
        private transient Map<String, Set<AbstractProject<?, ?>>> repoJobs;
        
        public List<GhprbExtensionDescriptor> getExtensionDescriptors() {
            return GhprbExtensionDescriptor.allProject();
        }
        
        public List<GhprbExtensionDescriptor> getGlobalExtensionDescriptors() {
            return GhprbExtensionDescriptor.allGlobal();
        }
        
        private DescribableList<GhprbExtension, GhprbExtensionDescriptor> extensions;
        
        public DescribableList<GhprbExtension, GhprbExtensionDescriptor> getExtensions() {
            if (extensions == null) {
                extensions = new DescribableList<GhprbExtension, GhprbExtensionDescriptor>(Saveable.NOOP);
            }
            return extensions;
        }

        public DescriptorImpl() {
            load();
            readBackFromLegacy();
            if (repoJobs == null) {
                repoJobs = new ConcurrentHashMap<String, Set<AbstractProject<?, ?>>>();
            }
            saveAfterPause();
        }
        
        private void saveAfterPause() {
            new java.util.Timer().schedule( 
                                           new java.util.TimerTask() {
                                               @Override
                                               public void run() {
                                                   save();
                                               }
                                           }, 
                                           5000 
                                   );
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
            adminlist = formData.getString("adminlist");
            requestForTestingPhrase = formData.getString("requestForTestingPhrase");
            whitelistPhrase = formData.getString("whitelistPhrase");
            okToTestPhrase = formData.getString("okToTestPhrase");
            retestPhrase = formData.getString("retestPhrase");
            skipBuildPhrase = formData.getString("skipBuildPhrase");
            cron = formData.getString("cron");
            useComments = formData.getBoolean("useComments");
            useDetailedComments = formData.getBoolean("useDetailedComments");
            manageWebhooks = formData.getBoolean("manageWebhooks");
            unstableAs = GHCommitState.valueOf(formData.getString("unstableAs"));
            autoCloseFailedPullRequests = formData.getBoolean("autoCloseFailedPullRequests");
            displayBuildErrorsOnDownstreamBuilds = formData.getBoolean("displayBuildErrorsOnDownstreamBuilds");
            
            githubAuth = req.bindJSONToList(GhprbGitHubAuth.class, formData.get("githubAuth"));
            
            extensions = new DescribableList<GhprbExtension, GhprbExtensionDescriptor>(Saveable.NOOP);

            try {
                extensions.rebuildHetero(req, formData, getGlobalExtensionDescriptors(), "extensions");
                // Now make sure we have at least one of the types we need one of.
                Ghprb.addIfMissing(this.extensions, new GhprbSimpleStatus(), GhprbCommitStatus.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            readBackFromLegacy();

            saveAfterPause();
            return super.configure(req, formData);
        }
        

        public FormValidation doCheckAdminlist(@QueryParameter String value) throws ServletException {
            if (!adminlistPattern.matcher(value).matches()) {
                return FormValidation.error("GitHub username may only contain alphanumeric characters or dashes "
                        + "and cannot begin with a dash. Separate them with whitespaces.");
            }
            return FormValidation.ok();
        }
        
        public ListBoxModel doFillUnstableAsItems() {
            ListBoxModel items = new ListBoxModel();
            GHCommitState[] results = new GHCommitState[] {GHCommitState.SUCCESS,GHCommitState.ERROR,GHCommitState.FAILURE};
            for (GHCommitState nextResult : results) {
                String text = StringUtils.capitalize(nextResult.toString().toLowerCase());
                items.add(text, nextResult.toString());
                if (unstableAs.toString().equals(nextResult)) {
                    items.get(items.size()-1).selected = true;
                } 
            }

            return items;
        }
        
        public String getAdminlist() {
            return adminlist;
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

        public String getSkipBuildPhrase() {
            return skipBuildPhrase;
        }

        public String getCron() {
            return cron;
        }

        public Boolean getUseComments() {
            return useComments;
        }

        public Boolean getUseDetailedComments() {
            return useDetailedComments;
        }

        public Boolean getManageWebhooks() {
            return manageWebhooks;
        }

        public Boolean getAutoCloseFailedPullRequests() {
            return autoCloseFailedPullRequests;
        }

        public Boolean getDisplayBuildErrorsOnDownstreamBuilds() {
            return displayBuildErrorsOnDownstreamBuilds;
        }

        public GHCommitState getUnstableAs() {
            return unstableAs;
        }
        
        public Integer getConfigVersion() {
            return configVersion;
        }

        public boolean isUseComments() {
            return (useComments != null && useComments);
        }

        public boolean isUseDetailedComments() {
            return (useDetailedComments != null && useDetailedComments);
        }
        
        public ListBoxModel doFillGitHubAuthIdItems(@QueryParameter("gitHubAuthId") String gitHubAuthId) {
            ListBoxModel model = new ListBoxModel();
            for (GhprbGitHubAuth auth : getGithubAuth()) {
                String description = Util.fixNull(auth.getDescription());
                int length = description.length();
                length = length > 50 ? 50 : length;
                Option next = new Option(auth.getServerAPIUrl() + " : " + description.substring(0, length), auth.getId());
                if (!StringUtils.isEmpty(gitHubAuthId) && gitHubAuthId.equals(auth.getId())) {
                    next.selected = true;
                }
                model.add(next);
            }
            return model;
        }
        

        public Map<Integer, GhprbPullRequest> getPullRequests(String projectName) {
            Map<Integer, GhprbPullRequest> ret = null;
            if (jobs != null && jobs.containsKey(projectName)) {
                ret = jobs.get(projectName);
                jobs.remove(projectName);
            }
            return ret;
        }
        
        private void addRepoTrigger(String repo, AbstractProject<?, ?> project) {
            if (project == null || StringUtils.isEmpty(repo)) {
                return;
            }
            logger.log(Level.FINE, "Adding [{0}] to webhooks repo [{1}]", new Object[]{project.getFullName(), repo});
            
            synchronized (this) {
                Set<AbstractProject<?, ?>> projects = repoJobs.get(repo);
                if (projects == null) {
                    logger.log(Level.FINE, "No other projects found, creating new repo set");
                    projects = Collections.newSetFromMap(new WeakHashMap<AbstractProject<?, ?>, Boolean>());
                    repoJobs.put(repo, projects);
                } else {
                    logger.log(Level.FINE, "Adding project to current repo set, length: {0}", new Object[]{projects.size()});
                }
                
                projects.add(project);
            }
        }
        
        private void removeRepoTrigger(String repo, AbstractProject<?, ?> project) {
            Set<AbstractProject<?, ?>> projects = repoJobs.get(repo);
            if (project != null && projects != null) {
                logger.log(Level.FINE, "Removing [{0}] from webhooks repo [{1}]", new Object[]{repo, project.getFullName()});
                projects.remove(project);
            }
        }
        
        public Set<AbstractProject<?, ?>> getRepoTriggers(String repo) {
            if (repoJobs == null) {
                repoJobs = new ConcurrentHashMap<String, Set<AbstractProject<?, ?>>>(5);
            }
            logger.log(Level.FINE, "Retrieving triggers for repo [{0}]", new Object[]{repo});
            
            Set<AbstractProject<?, ?>> projects = repoJobs.get(repo);
            if (projects != null) {
                for (AbstractProject<?, ?> project : projects) {
                    logger.log(Level.FINE, "Found project [{0}] for webhook repo [{1}]", new Object[]{project.getFullName(), repo});
                }
            } else {
                projects = Collections.newSetFromMap(new WeakHashMap<AbstractProject<?, ?>, Boolean>(0));
            }
            
            return projects;
        }

        public List<GhprbBranch> getWhiteListTargetBranches() {
            return whiteListTargetBranches;
        }
        
        public List<GhprbBranch> getBlackListTargetBranches() {
            return blackListTargetBranches;
        }

        @Deprecated
        private transient String publishedURL;
        @Deprecated
        private transient Integer logExcerptLines;
        @Deprecated
        private transient String msgSuccess;
        @Deprecated
        private transient String msgFailure;
        @Deprecated
        private transient String commitStatusContext;
        @Deprecated
        private transient String accessToken;
        @Deprecated
        private transient String username;
        @Deprecated
        private transient String password;
        @Deprecated
        private transient String serverAPIUrl;
        
        public void readBackFromLegacy() {
            if (configVersion == null) {
                configVersion = 0;
            }
            
            if (logExcerptLines != null && logExcerptLines > 0) {
                addIfMissing(new GhprbBuildLog(logExcerptLines));
                logExcerptLines = null;
            }
            if (!StringUtils.isEmpty(publishedURL)) {
                addIfMissing(new GhprbPublishJenkinsUrl(publishedURL));
                publishedURL = null;
            }
            if (!StringUtils.isEmpty(msgFailure) || !StringUtils.isEmpty(msgSuccess)) {
                List<GhprbBuildResultMessage> messages = new ArrayList<GhprbBuildResultMessage>(2);
                if (!StringUtils.isEmpty(msgFailure)) {
                    messages.add(new GhprbBuildResultMessage(GHCommitState.FAILURE, msgFailure));
                    msgFailure = null;
                }
                if (!StringUtils.isEmpty(msgSuccess)) {
                    messages.add(new GhprbBuildResultMessage(GHCommitState.SUCCESS, msgSuccess));
                    msgSuccess = null;
                }
                addIfMissing(new GhprbBuildStatus(messages));
            }
            
            if (configVersion < 1) {
                GhprbSimpleStatus status = new GhprbSimpleStatus(commitStatusContext);
                addIfMissing(status);
                commitStatusContext = null;
            }
            
            if (!StringUtils.isEmpty(accessToken)) {
                try {
                    GhprbGitHubAuth auth = new GhprbGitHubAuth(serverAPIUrl, null, Ghprb.createCredentials(serverAPIUrl, accessToken), "Pre credentials Token", null, null);
                    if (githubAuth == null) {
                        githubAuth = new ArrayList<GhprbGitHubAuth>(1);
                    }
                    githubAuth.add(auth);
                    accessToken = null;
                    serverAPIUrl = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            if (!StringUtils.isEmpty(username) || !StringUtils.isEmpty(password)) {
                try {
                    GhprbGitHubAuth auth = new GhprbGitHubAuth(serverAPIUrl, null, Ghprb.createCredentials(serverAPIUrl, username, password), "Pre credentials username and password", null, null);
                    if (githubAuth == null) {
                        githubAuth = new ArrayList<GhprbGitHubAuth>(1);
                    }
                    githubAuth.add(auth);
                    username = null;
                    password = null;
                    serverAPIUrl = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            configVersion = 1;
        }
        

        private void addIfMissing(GhprbExtension ext) {
            if (getExtensions().get(ext.getClass()) == null) {
                getExtensions().add(ext);
            }
        }
        
    }

}
