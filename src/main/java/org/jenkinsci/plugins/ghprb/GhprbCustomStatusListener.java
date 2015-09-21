package org.jenkinsci.plugins.ghprb;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kevin Suwala
 * This class is responsible for sending the custom status and message on
 * downstream jobs that have the option configured.
 */

@Extension
public class GhprbCustomStatusListener extends RunListener<AbstractBuild<?, ?>> {
    private static final Logger logger = Logger.getLogger(Ghprb.class.getName());
    private String context = "";
    private String message = "";
    private String sha = "";
    private String url = "";
    private String upstreamJob = "";
    private String jobName = "";

    // Gets all the custom env vars needed to send information to GitHub
    private void updateEnvironmentVars(AbstractBuild<?, ?> build, TaskListener listener){
        EnvVars envVars = new EnvVars();
        try {
            envVars = build.getEnvironment(listener);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Unable to get update environment variables!");
            listener.getLogger().println("Unable to get environment variables, is the upstream job configured with the GHPRB plugin?");
            return;
        }

        context = envVars.get("context");
        message = envVars.get("message");
        sha = envVars.get("ghprbActualCommit");
        url = envVars.get("BUILD_URL");
        if (url == "") url = envVars.get("JOB_URL");
        jobName = envVars.get("JOB_NAME");
        upstreamJob = envVars.get("ghprbTriggerJob");
    }

    // Sends the commit status and message to GitHub
    private void createCommitStatus(String sha, GHCommitState state, String url, String message, String context, AbstractBuild<?, ?> build) {
        // return if it's the upstream job, otherwise if context isn't set make it job name
        if (context == null) return;
        if (context.isEmpty()) context = jobName;

        logger.log(Level.FINE, "Job: " + build.getFullDisplayName() + " Attempting to send GitHub commit status");
        GHRepository r = GhprbCustomStatusRepoPasser.getRepoMap().get(upstreamJob);

        try {
            r.createCommitStatus(sha, state, url, message, context);
            logger.log(Level.FINE, "Status sent successfully");
        } catch(Exception e) {
            logger.log(Level.WARNING, "GitHub status could not be created!");
        }
    }

    // Sets the status as pending when the job starts and then calls the createCommitStatus method to send it to GitHub
    @Override
    public Environment setUpEnvironment(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        updateEnvironmentVars(build, listener);

        GHCommitState state = GHCommitState.PENDING;
        createCommitStatus(sha, state, url, "Build has started, please wait for results... ", context, build);

        return new hudson.model.Environment(){};
    }

    @Override
    public void onStarted(AbstractBuild<?, ?> build, TaskListener listener) {
        updateEnvironmentVars(build, listener);
    }

    // Sets the status to the build result when the job is done, and then calls the createCommitStatus method to send it to GitHub
    @Override
    public void onCompleted(AbstractBuild<?, ?> build, TaskListener listener) {
        updateEnvironmentVars(build, listener);

        GHCommitState state = GHCommitState.SUCCESS;

        if (build.getResult().isWorseThan(Result.SUCCESS)) {
            state = GHCommitState.FAILURE;
        }

        String newMessage = "";
        if (message.isEmpty()) {
            newMessage = "Build finished.";
        } else {
            newMessage = "Build finished with message: " + message;
        }
        createCommitStatus(sha, state, url, newMessage, context, build);
    }
}
