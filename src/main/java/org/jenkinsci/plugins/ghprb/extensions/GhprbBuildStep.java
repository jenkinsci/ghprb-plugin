package org.jenkinsci.plugins.ghprb.extensions;

import org.jenkinsci.plugins.ghprb.GhprbCause;

import hudson.model.AbstractProject;
import hudson.model.Action;

public interface GhprbBuildStep extends Action {
    public static String buildStep = "GhprbBuildStep";
    
    public void onScheduleBuild(AbstractProject<?, ?> project, GhprbCause cause);
}
