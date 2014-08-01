package org.jenkinsci.plugins.ghprb;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

	/**
	 * Return a downstream iterator of a build of default type. This will be overriden
	 * by specific build types.
	 * 
	 * @return the downstream builds as an iterator
	 */
	public Iterator downstreamIterator() {
		List downstreamList = new ArrayList();

		downstreamList.add(build);

		return downstreamList.iterator();
	}

	protected AbstractBuild build;

}