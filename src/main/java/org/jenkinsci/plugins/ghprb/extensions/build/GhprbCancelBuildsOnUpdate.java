package org.jenkinsci.plugins.ghprb.extensions.build;

import java.util.logging.Level;
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
import hudson.model.Cause;
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
        this.overrideGlobal = overrideGlobal == null ? false : overrideGlobal;
    }
    
    public Boolean getOverrideGlobal() {
        return overrideGlobal == null ? false : overrideGlobal;
    }

    private void cancelCurrentBuilds(AbstractProject<?, ?> project,
                                     Integer prId) {
        if (getOverrideGlobal()) {
            return;
        }
        
        logger.log(Level.FINER, "New build scheduled for " + project.getName() + " on PR # " + prId + ", checking for queued items to cancel.");
        Queue queue = Jenkins.getInstance().getQueue();
        for (Queue.Item item : queue.getItems(project)) {
            GhprbCause qcause = null;
            for (Cause cause : item.getCauses()){
                if (cause instanceof GhprbCause) {
                    qcause = (GhprbCause) cause;
                }
            }
            if (qcause == null) {
                continue;
            }
            if (qcause.getPullID() == prId) {
                try {
                    logger.log(Level.FINER, "Cancelling queued build of " + project.getName() + " for PR # " + qcause.getPullID() + ", checking for queued items to cancel.");
                    queue.cancel(item);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Unable to cancel queued build", e);
                }
            }
        }

        logger.log(Level.FINER, "New build scheduled for " + project.getName() + " on PR # " + prId);
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
                try {
                    logger.log(Level.FINER, "Cancelling running build #" + run.getNumber() + " of " + project.getName() + " for PR # " + cause.getPullID());
                    run.addAction(this);
                    run.getExecutor().interrupt(Result.ABORTED);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error while trying to interrupt build!", e);
                }
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
