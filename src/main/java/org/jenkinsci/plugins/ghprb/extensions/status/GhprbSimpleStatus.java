package org.jenkinsci.plugins.ghprb.extensions.status;

import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;

import java.io.IOException;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.ghprb.Ghprb;
import org.jenkinsci.plugins.ghprb.GhprbCause;
import org.jenkinsci.plugins.ghprb.GhprbPullRequest;
import org.jenkinsci.plugins.ghprb.GhprbTrigger;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommitStatus;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommitStatusException;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbProjectExtension;
import org.jenkinsci.plugins.ghprb.manager.GhprbBuildManager;
import org.jenkinsci.plugins.ghprb.manager.configuration.JobConfiguration;
import org.jenkinsci.plugins.ghprb.manager.factory.GhprbBuildManagerFactoryUtil;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.stapler.DataBoundConstructor;

public class GhprbSimpleStatus extends GhprbExtension implements GhprbCommitStatus, GhprbGlobalExtension, GhprbProjectExtension {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    
    private final String commitStatusContext;
    
    @DataBoundConstructor
    public GhprbSimpleStatus(String commitStatusContext) {
        this.commitStatusContext = commitStatusContext == null ? "" : commitStatusContext;
    }
    
    public String getCommitStatusContext() {
        return commitStatusContext == null ? "" : commitStatusContext;
    }

    public void onBuildTriggered(GhprbPullRequest pr, GHRepository ghRepository) throws GhprbCommitStatusException {
        StringBuilder sb = new StringBuilder();
        GHCommitState state = GHCommitState.PENDING;
        
        String context = getCommitStatusContext();
        if (StringUtils.isEmpty(context)) {
            context = null;
        }

        sb.append("Build triggered.");

        if (pr.isMergeable()) {
            sb.append(" sha1 is merged.");
        } else {
            sb.append(" sha1 is original commit.");
        }
        String message = sb.toString();
        try {
            ghRepository.createCommitStatus(pr.getHead(), state, null, message, context);
        } catch (IOException e) {
            throw new GhprbCommitStatusException(e, state, message, pr.getId());
        }
    }

    public void onBuildStart(AbstractBuild<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException {
        GhprbCause c = Ghprb.getCause(build);
        String message = (c.isMerged() ? "Build started, sha1 is merged" : "Build started, sha1 is original commit.");
        createCommitStatus(build, listener, message, repo, GHCommitState.PENDING);
    }

    public void onBuildComplete(AbstractBuild<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException {
        GhprbTrigger trigger = Ghprb.extractTrigger(build);
        if (trigger == null) {
            listener.getLogger().println("Unable to get pull request builder trigger!!");
        }
        GHCommitState state = Ghprb.getState(build);
        GhprbCause cause = Ghprb.getCause(build);

        JobConfiguration jobConfiguration =
            JobConfiguration.builder()
                .printStackTrace(trigger.isDisplayBuildErrorsOnDownstreamBuilds())
                .build();

        GhprbBuildManager buildManager =
            GhprbBuildManagerFactoryUtil.getBuildManager(build, jobConfiguration);
        
        StringBuilder replyMessage = new StringBuilder();
        
        if (cause.isMerged()) {
            replyMessage.append("Merged build finished. ");
        } else {
            replyMessage.append("Build finished. ");
        }
        
        replyMessage.append(buildManager.getOneLineTestResults());
        
        createCommitStatus(build, listener, replyMessage.toString(), repo, state);
    }

    private void createCommitStatus(AbstractBuild<?, ?> build, TaskListener listener, String message, GHRepository repo, GHCommitState state) throws GhprbCommitStatusException {
        GhprbCause cause = Ghprb.getCause(build);
        
        String sha1 = cause.getCommit();
        String url = Jenkins.getInstance().getRootUrl() + build.getUrl();
        String context = Util.fixEmpty(commitStatusContext);
        
        listener.getLogger().println(String.format("Setting status of %s to %s with url %s and message: '%s'", sha1, state, url, message));
        if (context != null) {
            listener.getLogger().println(String.format("Using conext: " + context));
        }
        try {
            repo.createCommitStatus(sha1, state, url, message, context);
        } catch (IOException e) {
            throw new GhprbCommitStatusException(e, state, message, cause.getPullID());
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }
    
    public static final class DescriptorImpl extends GhprbExtensionDescriptor implements GhprbGlobalExtension, GhprbProjectExtension {
        
        @Override
        public String getDisplayName() {
            return "Update commit status during build";
        }
        
        public String getCommitContextDefault(String commitStatusContext){
            String context = commitStatusContext;
            if (StringUtils.isEmpty(commitStatusContext)) {
                for(GhprbExtension extension : GhprbTrigger.getDscp().getExtensions()) {
                    if (extension instanceof GhprbSimpleStatus) {
                        context = ((GhprbSimpleStatus) extension).getCommitStatusContext();
                        break;
                    }
                }
            }
            return context;
        }
    }


}
