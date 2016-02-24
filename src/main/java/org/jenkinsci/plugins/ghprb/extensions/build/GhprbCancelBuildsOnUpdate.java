package org.jenkinsci.plugins.ghprb.extensions.build;

import java.util.logging.Logger;

import org.jenkinsci.plugins.ghprb.Ghprb;
import org.jenkinsci.plugins.ghprb.GhprbCause;
import org.jenkinsci.plugins.ghprb.extensions.GhprbBuildStep;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalDefault;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbProjectExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.util.RunList;
import jenkins.model.Jenkins;

public class GhprbCancelBuildsOnUpdate extends GhprbExtension implements GhprbBuildStep, GhprbGlobalExtension, GhprbProjectExtension, GhprbGlobalDefault {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    private static final Logger logger = Logger.getLogger(GhprbCancelBuildsOnUpdate.class.getName());
    
    private final Boolean overrideGlobal;

    @DataBoundConstructor
    public GhprbCancelBuildsOnUpdate(Boolean overrideGlobal) {
        this.overrideGlobal = overrideGlobal;
    }
    
    public Boolean getOverrideGlobal() {
        return overrideGlobal == null ? false : overrideGlobal;
    }

    private void cancelCurrentBuilds(AbstractProject<?, ?> project,
                                     Integer prId) {
        if (overrideGlobal) {
            return;
        }
        
        Queue queue = Jenkins.getInstance().getQueue();
        for (Queue.Item item : queue.getItems(project)) {
            GhprbCause cause = Ghprb.getCause(item);
            if (cause == null) {
                continue;
            }
            if (cause.getPullID() == prId) {
                queue.cancel(item);
            }
        }

        RunList<?> runs = project.getBuilds();
        for (Run<?, ?> run : runs) {
            if (!run.isBuilding() && !run.hasntStartedYet()) {
                break;
            }
            GhprbCause cause = Ghprb.getCause(run);
            if (cause == null) {
                continue;
            }
            if (cause.getPullID() == prId) {
                run.addAction(this);
                run.getExecutor().interrupt(Result.ABORTED);
            }
        }

    }

    public void onScheduleBuild(AbstractProject<?, ?> project,
                             GhprbCause cause) {

        if (project == null || cause == null) {
            return;
        }
        if (project.isBuilding() || project.isInQueue()) {
            cancelCurrentBuilds(project, cause.getPullID());
        }
    }

    public String getIconFileName() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getDisplayName() {
        return "Cancel Build on Pull Request Update";
    }

    public String getUrlName() {
        return null;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public static final class DescriptorImpl extends GhprbExtensionDescriptor
                                             implements GhprbGlobalExtension, GhprbProjectExtension {

        @Override
        public String getDisplayName() {
            return "Cancel build on update";
        }
    }


}
