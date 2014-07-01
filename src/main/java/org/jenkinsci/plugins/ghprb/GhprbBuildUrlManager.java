package org.jenkinsci.plugins.ghprb;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public interface GhprbBuildUrlManager {

	/**
	 * Calculate the build URL of a build
	 * 
	 * @return the build URL
	 */
	String calculateBuildUrl();

}