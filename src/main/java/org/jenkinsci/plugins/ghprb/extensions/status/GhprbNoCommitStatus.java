package org.jenkinsci.plugins.ghprb.extensions.status;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;

import org.jenkinsci.plugins.ghprb.GhprbPullRequest;
import org.jenkinsci.plugins.ghprb.GhprbTrigger;
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
    
    public void onBuildTriggered(GhprbTrigger trigger, GhprbPullRequest pr, GHRepository ghRepository) throws GhprbCommitStatusException {
        
    }

    public void onBuildStart(AbstractBuild<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException {
        
    }

    public void onBuildComplete(AbstractBuild<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException {
        
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
