package org.jenkinsci.plugins.ghprb.extensions.build;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.util.RunList;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.ghprb.Ghprb;
import org.jenkinsci.plugins.ghprb.GhprbCause;
import org.jenkinsci.plugins.ghprb.extensions.GhprbBuildStep;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalDefault;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbProjectExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.logging.Level;
import java.util.logging.Logger;

public class GhprbCancelBuildsOnUpdate extends GhprbExtension implements
        GhprbBuildStep, GhprbGlobalExtension, GhprbProjectExtension, GhprbGlobalDefault {

    private static final Logger LOGGER = Logger.getLogger(GhprbCancelBuildsOnUpdate.class.getName());

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private final Boolean overrideGlobal;

    @DataBoundConstructor
    public GhprbCancelBuildsOnUpdate(Boolean overrideGlobal) {
        this.overrideGlobal = overrideGlobal == null ? Boolean.valueOf(false) : overrideGlobal;
    }

    public Boolean getOverrideGlobal() {
        return overrideGlobal == null ? Boolean.valueOf(false) : overrideGlobal;
    }

    private void cancelCurrentBuilds(Job<?, ?> project,
                                     Integer prId) {
        if (getOverrideGlobal()) {
            return;
        }

        LOGGER.log(
                Level.FINER,
                "New build scheduled for " + project.getName() + " on PR # " + prId
                        + ", checking for queued items to cancel."
        );

        if (project instanceof AbstractProject<?, ?>) {
            Queue queue = Jenkins.getInstance().getQueue();
            for (Queue.Item queueItem : queue.getItems((AbstractProject<?, ?>) project)) {
                GhprbCause qcause = null;

                for (Cause cause : queueItem.getCauses()) {
                    if (cause instanceof GhprbCause) {
                        qcause = (GhprbCause) cause;
                    }
                }

                if (qcause != null && qcause.getPullID() == prId) {
                    try {
                        LOGGER.log(
                                Level.FINER,
                                "Cancelling queued build of " + project.getName() + " for PR # "
                                        + qcause.getPullID() + ", checking for queued items to cancel."
                        );
                        queue.cancel(queueItem);
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Unable to cancel queued build", e);
                    }
                }
            }
        }

        LOGGER.log(Level.FINER, "New build scheduled for " + project.getName() + " on PR # " + prId);
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
                    LOGGER.log(
                            Level.FINER,
                            "Cancelling running build #" + run.getNumber() + " of "
                                    + project.getName() + " for PR # " + cause.getPullID()
                    );
                    run.addAction(this);
                    run.getExecutor().interrupt(Result.ABORTED);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error while trying to interrupt build!", e);
                }
            }
        }

    }

    public void onScheduleBuild(Job<?, ?> project, GhprbCause cause) {
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
