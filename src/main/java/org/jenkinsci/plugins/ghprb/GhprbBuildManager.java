package org.jenkinsci.plugins.ghprb;

import java.util.Iterator;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public interface GhprbBuildManager {

	/**
	 * Calculate the build URL of a build
	 * 
	 * @return the build URL
	 */
	String calculateBuildUrl();

	/**
	 * Returns downstream builds as an iterator
	 * 
	 * @return the iterator
	 */
	Iterator downstreamProjects();

	/**
	 * Print tests result of a build
	 * 
	 * @param printStackTraces wether to print or not the stacktraces associated to each test
	 * @return the tests result
	 */
	String getTestResults(boolean printStackTraces);

}