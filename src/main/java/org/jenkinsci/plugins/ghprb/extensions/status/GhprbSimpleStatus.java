package org.jenkinsci.plugins.ghprb.extensions.status;

import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.ghprb.Ghprb;
import org.jenkinsci.plugins.ghprb.GhprbCause;
import org.jenkinsci.plugins.ghprb.GhprbTrigger;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommitStatus;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommitStatusException;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalDefault;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbProjectExtension;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildResultMessage;
import org.jenkinsci.plugins.ghprb.manager.GhprbBuildManager;
import org.jenkinsci.plugins.ghprb.manager.configuration.JobConfiguration;
import org.jenkinsci.plugins.ghprb.manager.factory.GhprbBuildManagerFactoryUtil;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.stapler.DataBoundConstructor;

public class GhprbSimpleStatus extends GhprbExtension implements GhprbCommitStatus, GhprbGlobalExtension,
                               GhprbProjectExtension, GhprbGlobalDefault {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private final String commitStatusContext;
    private final String triggeredStatus;
    private final String startedStatus;
    private final String statusUrl;
    private final Boolean addTestResults;
    private final List<GhprbBuildResultMessage> completedStatus;

    public GhprbSimpleStatus() {
        this(null);
    }

    public GhprbSimpleStatus(String commitStatusContext) {
        this(commitStatusContext, null, null, null, false, new ArrayList<GhprbBuildResultMessage>(0));
    }

    @DataBoundConstructor
    public GhprbSimpleStatus(String commitStatusContext,
                             String statusUrl,
                             String triggeredStatus,
                             String startedStatus,
                             Boolean addTestResults,
                             List<GhprbBuildResultMessage> completedStatus) {
        this.statusUrl = statusUrl;
        this.commitStatusContext = commitStatusContext == null ? "" : commitStatusContext;
        this.triggeredStatus = triggeredStatus;
        this.startedStatus = startedStatus;
        this.addTestResults = addTestResults;
        this.completedStatus = completedStatus;
    }

    public String getStatusUrl() {
        return statusUrl == null ? "" : statusUrl;
    }

    public String getCommitStatusContext() {
        return commitStatusContext == null ? "" : commitStatusContext;
    }

    public String getStartedStatus() {
        return startedStatus == null ? "" : startedStatus;
    }

    public String getTriggeredStatus() {
        return triggeredStatus == null ? "" : triggeredStatus;
    }

    public Boolean getAddTestResults() {
        return addTestResults == null ? false : addTestResults;
    }

    public List<GhprbBuildResultMessage> getCompletedStatus() {
        return completedStatus == null ? new ArrayList<GhprbBuildResultMessage>(0) : completedStatus;
    }

    public boolean addIfMissing() {
        return true;
    }

    public void onBuildTriggered(AbstractProject<?, ?> project,
                                 String commitSha,
                                 boolean isMergeable,
                                 int prId,
                                 GHRepository ghRepository) throws GhprbCommitStatusException {
        StringBuilder sb = new StringBuilder();
        GHCommitState state = GHCommitState.PENDING;
        String triggeredStatus = getDescriptor().getTriggeredStatusDefault(this);

        // check if we even need to update
        if (StringUtils.equals(triggeredStatus, "--none--")) {
            return;
        }

        String statusUrl = getDescriptor().getStatusUrlDefault(this);
        String commitStatusContext = getDescriptor().getCommitStatusContextDefault(this);

        String context = Util.fixEmpty(commitStatusContext);
        context = Ghprb.replaceMacros(project, context);

        if (!StringUtils.isEmpty(triggeredStatus)) {
            sb.append(Ghprb.replaceMacros(project, triggeredStatus));
        } else {
            sb.append("Build triggered.");
            if (isMergeable) {
                sb.append(" sha1 is merged.");
            } else {
                sb.append(" sha1 is original commit.");
            }
        }

        String url = Ghprb.replaceMacros(project, statusUrl);
        if (StringUtils.equals(statusUrl, "--none--")) {
            url = "";
        }

        String message = sb.toString();
        try {
            ghRepository.createCommitStatus(commitSha, state, url, message, context);
        } catch (IOException e) {
            throw new GhprbCommitStatusException(e, state, message, prId);
        }
    }

    public void onEnvironmentSetup(AbstractBuild<?, ?> build,
                                   TaskListener listener,
                                   GHRepository repo) throws GhprbCommitStatusException {
        // no need to create a commit here -- the onBuildStart() event will fire
        // soon and will respect's the user's settings for startedStatus.
    }

    public void onBuildStart(AbstractBuild<?, ?> build,
                             TaskListener listener,
                             GHRepository repo) throws GhprbCommitStatusException {
        String startedStatus = getDescriptor().getStartedStatusDefault(this);

        // check if we even need to update
        if (StringUtils.equals(startedStatus, "--none--")) {
            return;
        }

        GhprbCause c = Ghprb.getCause(build);
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isEmpty(startedStatus)) {
            sb.append("Build started");
            sb.append(c.isMerged() ? " sha1 is merged." : " sha1 is original commit.");
        } else {
            sb.append(Ghprb.replaceMacros(build, listener, startedStatus));
        }
        createCommitStatus(build, listener, sb.toString(), repo, GHCommitState.PENDING);
    }

    public void onBuildComplete(AbstractBuild<?, ?> build,
                                TaskListener listener,
                                GHRepository repo) throws GhprbCommitStatusException {
        List<GhprbBuildResultMessage> completedStatus = getDescriptor().getCompletedStatusDefault(this);

        GHCommitState state = Ghprb.getState(build);

        StringBuilder sb = new StringBuilder();

        if (completedStatus == null || completedStatus.isEmpty()) {
            sb.append("Build finished.");
        } else {
            for (GhprbBuildResultMessage buildStatus : completedStatus) {
                sb.append(buildStatus.postBuildComment(build, listener));
            }
            if (StringUtils.equals(sb.toString(), "--none--")) {
                return;
            }
        }

        sb.append(" ");
        GhprbTrigger trigger = Ghprb.extractTrigger(build);
        if (trigger == null) {
            listener.getLogger().println("Unable to get pull request builder trigger!!");
        } else {
            JobConfiguration jobConfiguration =
                            JobConfiguration.builder()
                                            .printStackTrace(trigger.getDisplayBuildErrorsOnDownstreamBuilds()).build();

            GhprbBuildManager buildManager = GhprbBuildManagerFactoryUtil.getBuildManager(build, jobConfiguration);
            if (getAddTestResults()) {
                sb.append(buildManager.getOneLineTestResults());
            }
        }

        createCommitStatus(build, listener, sb.toString(), repo, state);
    }

    private void createCommitStatus(AbstractBuild<?, ?> build,
                                    TaskListener listener,
                                    String message,
                                    GHRepository repo,
                                    GHCommitState state) throws GhprbCommitStatusException {

        Map<String, String> envVars = Ghprb.getEnvVars(build, listener);

        String sha1 = envVars.get("ghprbActualCommit");
        Integer pullId = Integer.parseInt(envVars.get("ghprbPullId"));

        String url = envVars.get("BUILD_URL");
        if (StringUtils.isEmpty(url)) {
            url = envVars.get("JOB_URL");
        }
        if (StringUtils.isEmpty(url)) {
            url = Jenkins.getInstance().getRootUrl() + build.getUrl();
        }

        if (StringUtils.equals(statusUrl, "--none--")) {
            url = "";
        } else if (!StringUtils.isEmpty(statusUrl)) {
            url = Ghprb.replaceMacros(build, listener, statusUrl);
        }

        String context = Util.fixEmpty(commitStatusContext);
        context = Ghprb.replaceMacros(build, listener, context);

        listener.getLogger().println(String.format("Setting status of %s to %s with url %s and message: '%s'",
                                                   sha1,
                                                   state,
                                                   url,
                                                   message));
        if (context != null) {
            listener.getLogger().println(String.format("Using context: " + context));
        }
        try {
            repo.createCommitStatus(sha1, state, url, message, context);
        } catch (IOException e) {
            throw new GhprbCommitStatusException(e, state, message, pullId);
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public static final class DescriptorImpl extends GhprbExtensionDescriptor
                                             implements GhprbGlobalExtension, GhprbProjectExtension {

        @Override
        public String getDisplayName() {
            return "Update commit status during build";
        }

        public String getTriggeredStatusDefault(GhprbSimpleStatus local) {
            return Ghprb.getDefaultValue(local, GhprbSimpleStatus.class, "getTriggeredStatus");
        }

        public String getStatusUrlDefault(GhprbSimpleStatus local) {
            return Ghprb.getDefaultValue(local, GhprbSimpleStatus.class, "getStatusUrl");
        }

        public String getStartedStatusDefault(GhprbSimpleStatus local) {
            return Ghprb.getDefaultValue(local, GhprbSimpleStatus.class, "getStartedStatus");
        }

        public Boolean getAddTestResultsDefault(GhprbSimpleStatus local) {
            return Ghprb.getDefaultValue(local, GhprbSimpleStatus.class, "getAddTestResults");
        }

        public List<GhprbBuildResultMessage> getCompletedStatusDefault(GhprbSimpleStatus local) {
            return Ghprb.getDefaultValue(local, GhprbSimpleStatus.class, "getCompletedStatus");
        }

        public String getCommitStatusContextDefault(GhprbSimpleStatus local) {
            return Ghprb.getDefaultValue(local, GhprbSimpleStatus.class, "getCommitStatusContext");
        }

        public boolean addIfMissing() {
            return false;
        }

    }
}
