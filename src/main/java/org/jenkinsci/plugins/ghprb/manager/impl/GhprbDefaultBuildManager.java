package org.jenkinsci.plugins.ghprb.manager.impl;

import hudson.model.Run;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public class GhprbDefaultBuildManager extends GhprbBaseBuildManager {

    public GhprbDefaultBuildManager(Run<?, ?> build) {
        super(build);
    }

}
