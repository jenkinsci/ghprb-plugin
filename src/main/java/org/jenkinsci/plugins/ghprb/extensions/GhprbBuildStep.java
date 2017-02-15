package org.jenkinsci.plugins.ghprb.extensions;

import hudson.model.Action;
import hudson.model.Job;

import org.jenkinsci.plugins.ghprb.GhprbCause;

public interface GhprbBuildStep extends Action {
    public static String buildStep = "GhprbBuildStep";
    
    public void onScheduleBuild(Job<?, ?> project, GhprbCause cause);
}
