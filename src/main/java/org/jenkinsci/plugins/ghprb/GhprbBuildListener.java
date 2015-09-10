package org.jenkinsci.plugins.ghprb;

import java.io.IOException;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

/**
 * @author janinko
 */
@Extension
public class GhprbBuildListener extends RunListener<AbstractBuild<?, ?>> {

    @Override
    public void onStarted(AbstractBuild<?, ?> build, TaskListener listener) {
        GhprbTrigger trigger = Ghprb.extractTrigger(build);
        if (trigger != null) {
            trigger.getBuilds().onStarted(build, listener);
        }
    }

    @Override
    public void onCompleted(AbstractBuild<?, ?> build, TaskListener listener) {
        GhprbTrigger trigger = Ghprb.extractTrigger(build);
        if (trigger != null) {
            trigger.getBuilds().onCompleted(build, listener);
        }
    }

    @Override
    public Environment setUpEnvironment(@SuppressWarnings("rawtypes") AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        GhprbTrigger trigger = Ghprb.extractTrigger(build);
        if (trigger != null) {
            trigger.getBuilds().onEnvironmentSetup(build, launcher, listener);
        }

        return new hudson.model.Environment(){};
    }
}
