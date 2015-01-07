package org.jenkinsci.plugins.ghprb;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import javax.annotation.Nonnull;

/**
 * @author janinko
 */
@Extension
public class GhprbBuildListener extends RunListener<AbstractBuild<?, ?>> {

    @Override
    public void onStarted(AbstractBuild<?, ?> build, TaskListener listener) {
        GhprbTrigger trigger = build.getProject().getTrigger(GhprbTrigger.class);
        if (trigger != null) {
            trigger.getBuilds().onStarted(build, listener.getLogger());
        }
    }

    @Override
    public void onCompleted(AbstractBuild<?, ?> build, @Nonnull TaskListener listener) {
        GhprbTrigger trigger = build.getProject().getTrigger(GhprbTrigger.class);
        if (trigger != null) {
            trigger.getBuilds().onCompleted(build, listener);
        }
    }
}
