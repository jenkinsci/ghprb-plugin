package org.jenkinsci.plugins.ghprb.downstreambuilds;

import org.jenkinsci.plugins.ghprb.GhprbDefaultBuildManager;

import com.cloudbees.plugins.flow.FlowRun;

import hudson.model.AbstractBuild;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public class DownstreamBuildManagerFactoryUtil {

	/**
	 * Gets an instance of a library that is able to calculate build urls
	 * depending of build type.
	 * 
	 * If the class representing the build type is not present on the classloader
	 * then default implementation is returned.
	 * 
	 * @param build
	 * @return
	 */
	public static IDownstreamBuildManager getBuildManager(AbstractBuild build) {
		try {
			if (build instanceof FlowRun) {
				return new DownstreamBuildFlowManager(build);
			}
		}
		catch (NoClassDefFoundError ncdfe) {
		}

		return new GhprbDefaultBuildManager(build);
	}

}