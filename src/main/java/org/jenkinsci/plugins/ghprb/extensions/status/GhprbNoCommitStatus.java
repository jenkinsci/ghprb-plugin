package org.jenkinsci.plugins.ghprb.extensions.status;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.Action;
import java.util.List;

import org.jenkinsci.plugins.ghprb.extensions.*;

import org.kohsuke.github.GHRepository;
import org.kohsuke.stapler.DataBoundConstructor;

public class GhprbNoCommitStatus extends GhprbExtension implements GhprbCommitStatus, GhprbProjectExtension  {
    

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    @DataBoundConstructor
    public GhprbNoCommitStatus() {
        
    }
    
    public void onBuildStart(Run<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException {
        
    }

    public void onBuildComplete(Run<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException {
        
    }

    public void onEnvironmentSetup(Run<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException {
        
    }

    public List<Action> onBuildTriggered(Job<?, ?> project, String commitSha, boolean isMergeable, int prId, GHRepository ghRepository) throws GhprbCommitStatusException {
        return null;
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
