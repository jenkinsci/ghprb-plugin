package org.jenkinsci.plugins.ghprb.downstreambuilds;

import org.jenkinsci.plugins.ghprb.GhprbTrigger;

import hudson.model.AbstractBuild;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public abstract class BaseDownstreamBuildManager
	implements IDownstreamBuildManager {

	public BaseDownstreamBuildManager(AbstractBuild build) {
		this.build = build;
	}

	public String calculateBuildUrl() {
		String publishedURL = GhprbTrigger.getDscp().getPublishedURL();

		return publishedURL + "/" + build.getUrl();
	}

	protected AbstractBuild build;

}