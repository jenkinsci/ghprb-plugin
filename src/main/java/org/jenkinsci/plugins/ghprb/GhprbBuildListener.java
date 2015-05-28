package org.jenkinsci.plugins.ghprb;

import com.google.common.base.Optional;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

/**
 * @author janinko
 */
@Extension
public class GhprbBuildListener extends RunListener<AbstractBuild<?, ?>> {

    @Override
    public void onStarted(AbstractBuild<?, ?> build, TaskListener listener) {
        final Optional<GhprbTrigger> trigger = findTrigger(build);
        if (trigger.isPresent()) {
            trigger.get().getBuilds().onStarted(build, listener.getLogger());
        }
    }

    @Override
    public void onCompleted(AbstractBuild<?, ?> build, TaskListener listener) {
        final Optional<GhprbTrigger> trigger = findTrigger(build);
        if (trigger.isPresent()) {
            trigger.get().getBuilds().onCompleted(build, listener);
        }
    }

    private static Optional<GhprbTrigger> findTrigger(AbstractBuild<?, ?> build) {
        return Optional.fromNullable(Ghprb.extractTrigger(build));
    }
}
