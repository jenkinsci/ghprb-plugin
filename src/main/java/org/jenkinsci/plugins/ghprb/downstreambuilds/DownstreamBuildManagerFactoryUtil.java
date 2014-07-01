package org.jenkinsci.plugins.ghprb.downstreambuilds;

import org.jenkinsci.plugins.ghprb.GhprbDefaultBuildManager;

import com.cloudbees.plugins.flow.FlowRun;

import hudson.model.AbstractBuild;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public class DownstreamBuildManagerFactoryUtil {

	public static IDownstreamBuildManager getBuildManager(AbstractBuild build) {
		if (build instanceof FlowRun) {
			return new DownstreamBuildFlowManager(build);
		}

		return new GhprbDefaultBuildManager(build);
	}

}