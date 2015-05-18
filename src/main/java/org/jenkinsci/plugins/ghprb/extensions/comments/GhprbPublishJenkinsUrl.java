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
import org.jenkinsci.plugins.ghprb.extensions.GhprbProjectExtension;
import org.jenkinsci.plugins.ghprb.manager.GhprbBuildManager;
import org.jenkinsci.plugins.ghprb.manager.configuration.JobConfiguration;
import org.jenkinsci.plugins.ghprb.manager.factory.GhprbBuildManagerFactoryUtil;
import org.kohsuke.stapler.DataBoundConstructor;

public class GhprbPublishJenkinsUrl extends GhprbExtension implements GhprbCommentAppender, GhprbGlobalExtension {

    @DataBoundConstructor
    public GhprbPublishJenkinsUrl() {
        
    }

    public String postBuildComment(AbstractBuild<?, ?> build, TaskListener listener) {
        StringBuilder msg = new StringBuilder();

        msg.append("\nRefer to this link for build results (access rights to CI server needed): \n");
        msg.append(generateCustomizedMessage(build));
        
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

        sb.append(buildManager.calculateBuildUrl());

        if (build.getResult() != Result.SUCCESS) {
            sb.append(buildManager.getTestResults());
        }

        return sb.toString();
    }


    @Extension
    public static final class DescriptorImpl extends GhprbExtensionDescriptor implements GhprbGlobalExtension {

        @Override
        public String getDisplayName() {
            return "Add link to Jenkins";
        }
        
    }
}
