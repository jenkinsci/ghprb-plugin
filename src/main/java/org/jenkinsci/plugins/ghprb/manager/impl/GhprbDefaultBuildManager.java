package org.jenkinsci.plugins.ghprb.manager.impl;

import hudson.model.AbstractBuild;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public class GhprbDefaultBuildManager extends GhprbBaseBuildManager {

    public GhprbDefaultBuildManager(AbstractBuild<?, ?> build) {
        super(build);
    }

}