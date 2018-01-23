package org.jenkinsci.plugins.ghprb;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import javax.annotation.Nonnull;

/**
 * @author janinko
 */
@Extension
public class GhprbBuildListener extends RunListener<Run<?, ?>> {

    @Override
    public void onStarted(Run<?, ?> build, TaskListener listener) {
        GhprbTrigger trigger = Ghprb.extractTrigger(build);
        if (trigger != null && trigger.getBuilds() != null) {
            trigger.getBuilds().onStarted(build, listener);
        }
    }

    @Override
    public void onCompleted(Run<?, ?> build, @Nonnull TaskListener listener) {
        GhprbTrigger trigger = Ghprb.extractTrigger(build);
        if (trigger != null && trigger.getBuilds() != null) {
            trigger.getBuilds().onCompleted(build, listener);
        }
    }
}
