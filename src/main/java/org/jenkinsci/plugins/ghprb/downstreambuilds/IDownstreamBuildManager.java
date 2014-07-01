package org.jenkinsci.plugins.ghprb.downstreambuilds;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public interface IDownstreamBuildManager {

	/**
	 * Calculate the build URL of a build
	 * 
	 * @return the build URL
	 */
	String calculateBuildUrl();

}