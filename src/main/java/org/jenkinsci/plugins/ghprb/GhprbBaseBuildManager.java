package org.jenkinsci.plugins.ghprb;


import hudson.model.AbstractBuild;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public abstract class GhprbBaseBuildManager implements GhprbBuildManager {

	public GhprbBaseBuildManager(AbstractBuild build) {
		this.build = build;
	}

	/**
	 * Calculate the build URL of a build of default type. This will be overriden
	 * by specific build types.
	 * 
	 * @return the build URL of a build of default type
	 */
	public String calculateBuildUrl() {
		String publishedURL = GhprbTrigger.getDscp().getPublishedURL();

		return publishedURL + "/" + build.getUrl();
	}

	protected AbstractBuild build;

}