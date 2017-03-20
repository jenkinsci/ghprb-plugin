package org.jenkinsci.plugins.ghprb.upstream;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.listeners.RunListener;

import org.jenkinsci.plugins.ghprb.Ghprb;
import org.jenkinsci.plugins.ghprb.GhprbGitHubAuth;
import org.jenkinsci.plugins.ghprb.GhprbTrigger;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommitStatusException;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildResultMessage;
import org.jenkinsci.plugins.ghprb.extensions.status.GhprbSimpleStatus;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kevin Suwala
 * This class is responsible for sending the custom status and message on
 * downstream jobs that have the option configured.
 */

@Extension
public class GhprbUpstreamStatusListener extends RunListener<Run<?, ?>> {
    private static final Logger logger = Logger.getLogger(GhprbUpstreamStatusListener.class.getName());
    
    private GHRepository repo;

    // Gets all the custom env vars needed to send information to GitHub
    private Map<String, String> returnEnvironmentVars(Run<?, ?> build, TaskListener listener){
        Map<String, String> envVars = Ghprb.getEnvVars(build, listener);

        if (!envVars.containsKey("ghprbUpstreamStatus")) {
            return null;
        }        
        
        GhprbGitHubAuth auth = GhprbTrigger.getDscp()
                .getGitHubAuth(envVars.get("ghprbCredentialsId"));

        try {
            GitHub gh = auth.getConnection(build.getParent());
            repo = gh.getRepository(envVars.get("ghprbGhRepository"));
            return envVars;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unable to connect to GitHub repo", e);
            return null;
        }
        
    }

    private GhprbSimpleStatus returnGhprbSimpleStatus(Map<String, String> envVars) {
        List<GhprbBuildResultMessage> statusMessages = new ArrayList<GhprbBuildResultMessage>(5);

        for (GHCommitState state : GHCommitState.values()) {
            String envVar = String.format("ghprb%sMessage", state.name());
            String message = envVars.get(envVar);
            statusMessages.add(new GhprbBuildResultMessage(state, message));
        }

        return new GhprbSimpleStatus(
                Boolean.valueOf(envVars.get("ghprbShowMatrixStatus")),
                envVars.get("ghprbCommitStatusContext"),
                envVars.get("ghprbStatusUrl"),
                envVars.get("ghprbTriggeredStatus"),
                envVars.get("ghprbStartedStatus"),
                new Boolean(envVars.get("ghprbAddTestResults")),
                statusMessages
        );
    }

    @Override
    public void onStarted(Run<?, ?> build, TaskListener listener) {
        Map<String, String> envVars = returnEnvironmentVars(build, listener);
        if (envVars == null) {
            return;
        }

        try {
            returnGhprbSimpleStatus(envVars).onBuildStart(build, listener, repo);
        } catch (GhprbCommitStatusException e) {
            e.printStackTrace();
        }
    }

    // Sets the status to the build result when the job is done, and then calls the createCommitStatus method to send it to GitHub
    @Override
    public void onCompleted(Run<?, ?> build, TaskListener listener) {
        Map<String, String> envVars = returnEnvironmentVars(build, listener);
        if (envVars == null) {
            return;
        }

        try {
            returnGhprbSimpleStatus(envVars).onBuildComplete(build, listener, repo);
        } catch (GhprbCommitStatusException e) {
            e.printStackTrace();
        }
    }
}
