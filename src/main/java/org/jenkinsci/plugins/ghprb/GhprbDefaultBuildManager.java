package org.jenkinsci.plugins.ghprb;

import org.jenkinsci.plugins.ghprb.downstreambuilds.BaseDownstreamBuildManager;

import hudson.model.AbstractBuild;

public class GhprbDefaultBuildManager extends BaseDownstreamBuildManager {

	public GhprbDefaultBuildManager(AbstractBuild build) {
		super(build);
	}

}