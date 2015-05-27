package org.jenkinsci.plugins.ghprb.extensions.comments;

import hudson.Extension;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;

import org.jenkinsci.plugins.ghprb.Ghprb;
import org.jenkinsci.plugins.ghprb.GhprbTrigger;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommentAppender;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalExtension;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildStatus.DescriptorImpl;
import org.jenkinsci.plugins.ghprb.manager.GhprbBuildManager;
import org.jenkinsci.plugins.ghprb.manager.configuration.JobConfiguration;
import org.jenkinsci.plugins.ghprb.manager.factory.GhprbBuildManagerFactoryUtil;
import org.kohsuke.stapler.DataBoundConstructor;

public class GhprbPublishJenkinsUrl extends GhprbExtension implements GhprbCommentAppender, GhprbGlobalExtension {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    
    private final String publishedURL;
    
    @DataBoundConstructor
    public GhprbPublishJenkinsUrl(String publishedURL) {
        this.publishedURL = publishedURL;
    }
    
    public String getPublishedURL() {
        return publishedURL;
    }

    public String postBuildComment(AbstractBuild<?, ?> build, TaskListener listener) {
        StringBuilder msg = new StringBuilder();

        msg.append("\nRefer to this link for build results (access rights to CI server needed): \n");
        msg.append(generateCustomizedMessage(build));
        msg.append("\n");
        
        return msg.toString();
    }
    

    private String generateCustomizedMessage(AbstractBuild<?, ?> build) {
        GhprbTrigger trigger = Ghprb.extractTrigger(build);
        if (trigger == null) {
            return "";
        }
        JobConfiguration jobConfiguration = JobConfiguration.builder()
                .printStackTrace(trigger.isDisplayBuildErrorsOnDownstreamBuilds()).build();

        GhprbBuildManager buildManager = GhprbBuildManagerFactoryUtil.getBuildManager(build, jobConfiguration);

        StringBuilder sb = new StringBuilder();

        sb.append(buildManager.calculateBuildUrl(publishedURL));

        if (build.getResult() != Result.SUCCESS) {
            sb.append(buildManager.getTestResults());
        }

        return sb.toString();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public static final class DescriptorImpl extends GhprbExtensionDescriptor implements GhprbGlobalExtension {

        @Override
        public String getDisplayName() {
            return "Add link to Jenkins";
        }
        
    }
}
