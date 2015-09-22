package org.jenkinsci.plugins.ghprb.extensions.status;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;

import org.jenkinsci.plugins.ghprb.extensions.GhprbCommitStatus;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommitStatusException;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.GhprbProjectExtension;
import org.kohsuke.github.GHRepository;
import org.kohsuke.stapler.DataBoundConstructor;

public class GhprbNoCommitStatus extends GhprbExtension implements GhprbCommitStatus, GhprbProjectExtension  {
    

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    @DataBoundConstructor
    public GhprbNoCommitStatus() {
        
    }
    
    public void onBuildStart(AbstractBuild<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException {
        
    }

    public void onBuildComplete(AbstractBuild<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException {
        
    }

    public void onEnvironmentSetup(AbstractBuild<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException {
        
    }

    public void onBuildTriggered(AbstractProject<?, ?> project, String commitSha, boolean isMergeable, int prId, GHRepository ghRepository) throws GhprbCommitStatusException {
        
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }
    
    public static final class DescriptorImpl extends GhprbExtensionDescriptor implements GhprbProjectExtension {

        @Override
        public String getDisplayName() {
            return "Do not update commit status";
        }
        
    }


}
