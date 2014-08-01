package org.jenkinsci.plugins.ghprb;


import hudson.model.AbstractBuild;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public class GhprbDefaultBuildManager extends GhprbBaseBuildManager {

	public GhprbDefaultBuildManager(AbstractBuild build) {
		super(build);
	}

}