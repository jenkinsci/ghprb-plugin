package org.jenkinsci.plugins.ghprb.upstream;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.listeners.RunListener;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.ghprb.Ghprb;
import org.jenkinsci.plugins.ghprb.GhprbGitHubAuth;
import org.jenkinsci.plugins.ghprb.GhprbTrigger;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommitStatusException;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildResultMessage;
import org.jenkinsci.plugins.ghprb.extensions.status.GhprbSimpleStatus;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

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
public class GhprbUpstreamStatusListener extends RunListener<AbstractBuild<?, ?>> {
    private static final Logger logger = Logger.getLogger(GhprbUpstreamStatusListener.class.getName());
    
    private GhprbSimpleStatus statusUpdater;
    
    private GHRepository repo;

    // Gets all the custom env vars needed to send information to GitHub
    private boolean updateEnvironmentVars(AbstractBuild<?, ?> build, TaskListener listener){

        
        Map<String, String> envVars = Ghprb.getEnvVars(build, listener);

        if (!envVars.containsKey("ghprbUpstreamStatus")) {
            return false;
        }        
        
        String jobName = envVars.get("JOB_NAME");
        
        List<GhprbBuildResultMessage> statusMessages = new ArrayList<GhprbBuildResultMessage>(5);
        
        for (GHCommitState state : GHCommitState.values()) {
            String envVar = String.format("ghprb%sMessage", state.name());
            String message = envVars.get(envVar);
            statusMessages.add(new GhprbBuildResultMessage(state, message));
        }
        
        String context = envVars.get("commitStatusContext");
        
        if (StringUtils.isEmpty(context)) {
            context = jobName;
        }

        statusUpdater = new GhprbSimpleStatus(envVars.get("ghprbCommitStatusContext"), envVars.get("ghprbStatusUrl"), envVars.get("ghprbTriggeredStatus"), envVars.get("ghprbStartedStatus"), statusMessages);

        String credentialsId = envVars.get("ghprbCredentialsId");
        String repoName = envVars.get("ghprbGhRepository");
        
        GhprbGitHubAuth auth = GhprbTrigger.getDscp().getGitHubAuth(credentialsId);
        try {
            GitHub gh = auth.getConnection(build.getProject());
            repo = gh.getRepository(repoName);
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unable to connect to GitHub repo", e);
            return false;
        }
        
    }

    // Sets the status as pending when the job starts and then calls the createCommitStatus method to send it to GitHub
    @Override
    public Environment setUpEnvironment(@SuppressWarnings("rawtypes") AbstractBuild build, Launcher launcher, BuildListener listener) {
        if (updateEnvironmentVars(build, listener)) {
            logger.log(Level.FINE, "Job: " + build.getFullDisplayName() + " Attempting to send GitHub commit status");
            
            try {
                statusUpdater.onEnvironmentSetup(build, listener, repo);
            } catch (GhprbCommitStatusException e) {
                e.printStackTrace();
            }
        }

        return new Environment(){};
    }

    @Override
    public void onStarted(AbstractBuild<?, ?> build, TaskListener listener) {
        if (!updateEnvironmentVars(build, listener)) {
            return;
        }

        try {
            statusUpdater.onBuildStart(build, listener, repo);
        } catch (GhprbCommitStatusException e) {
            e.printStackTrace();
        }
    }

    // Sets the status to the build result when the job is done, and then calls the createCommitStatus method to send it to GitHub
    @Override
    public void onCompleted(AbstractBuild<?, ?> build, TaskListener listener) {
        if (!updateEnvironmentVars(build, listener)) {
            return;
        }
        
        try {
            statusUpdater.onBuildComplete(build, listener, repo);
        } catch (GhprbCommitStatusException e) {
            e.printStackTrace();
        }
    }
}
