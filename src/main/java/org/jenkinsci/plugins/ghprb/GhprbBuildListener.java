package org.jenkinsci.plugins.ghprb;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

/**
 *
 * @author janinko
 */
@Extension
public class GhprbBuildListener extends RunListener<AbstractBuild>{

	@Override
	public void onStarted(AbstractBuild build, TaskListener listener) {
		GhprbTrigger trigger = GhprbTrigger.getTrigger(build.getProject());
		if(trigger == null) return;

		trigger.getGhprb().getBuilds().onStarted(build);
	}

	@Override
	public void onCompleted(AbstractBuild build, TaskListener listener) {
		GhprbTrigger trigger = GhprbTrigger.getTrigger(build.getProject());
		if(trigger == null) return;

		trigger.getGhprb().getBuilds().onCompleted(build);
	}
}
