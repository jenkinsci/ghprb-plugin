package org.jenkinsci.plugins.ghprb.upstream;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildWrapper;

import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildResultMessage;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author Kevin Suwala
 */

public class GhprbUpstreamStatus extends BuildWrapper {

    private final String commitStatusContext;
    private final String triggeredStatus;
    private final String startedStatus;
    private final String statusUrl;
    private final Boolean addTestResults;
    private final List<GhprbBuildResultMessage> completedStatus;
    
    // sets the context and message as env vars so that they are available in the Listener class
    @Override
    public void makeBuildVariables(@SuppressWarnings("rawtypes") AbstractBuild build, Map<String,String> variables){
        variables.put("ghprbUpstreamStatus", "true");
        variables.put("ghprbCommitStatusContext", getCommitStatusContext());
        variables.put("ghprbTriggeredStatus", getTriggeredStatus());
        variables.put("ghprbStartedStatus", getStartedStatus());
        variables.put("ghprbStatusUrl", getStatusUrl());
        variables.put("ghprbAddTestResults", Boolean.toString(getAddTestResults()));
        
        Map<GHCommitState, StringBuilder> statusMessages = new HashMap<GHCommitState, StringBuilder>(5);
        
        for (GhprbBuildResultMessage message : getCompletedStatus()) {
            GHCommitState state = message.getResult();
            StringBuilder sb;
            if (!statusMessages.containsKey(state)) {
                sb = new StringBuilder();
                statusMessages.put(state, sb);
            } else {
                sb = statusMessages.get(state);
                sb.append("\n");
            }
            sb.append(message.getMessage());
        }
        
        for (Entry<GHCommitState, StringBuilder> next : statusMessages.entrySet()) {
            String key = String.format("ghprb%sMessage", next.getKey().name());
            variables.put(key, next.getValue().toString());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public BuildWrapper.Environment setUp(@SuppressWarnings("rawtypes") AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        makeBuildVariables(build, build.getBuildVariables());
        return new Environment(){};
    }

    @Override
    @SuppressWarnings("unchecked")
    public void preCheckout(@SuppressWarnings("rawtypes") AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        makeBuildVariables(build, build.getBuildVariables());
    }

    @DataBoundConstructor
    public GhprbUpstreamStatus(
            String commitStatusContext, 
            String statusUrl, 
            String triggeredStatus, 
            String startedStatus, 
            Boolean addTestResults,
            List<GhprbBuildResultMessage> completedStatus
            ) {
        this.statusUrl = statusUrl;
        this.commitStatusContext = commitStatusContext == null ? "" : commitStatusContext;
        this.triggeredStatus = triggeredStatus;
        this.startedStatus = startedStatus;
        this.addTestResults = addTestResults;
        this.completedStatus = completedStatus;
    }
    

    public String getStatusUrl() {
        return statusUrl == null ? "" : statusUrl;
    }
    
    public String getCommitStatusContext() {
        return commitStatusContext == null ? "" : commitStatusContext;
    }

    public String getStartedStatus() {
        return startedStatus == null ? "" : startedStatus;
    }
    
    public String getTriggeredStatus() {
        return triggeredStatus == null ? "" : triggeredStatus;
    }

    public Boolean getAddTestResults() {
        return addTestResults == null ? false : addTestResults;
    }


    public List<GhprbBuildResultMessage> getCompletedStatus() {
        return completedStatus == null ? new ArrayList<GhprbBuildResultMessage>(0) : completedStatus;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<BuildWrapper> {

        @Override
        public String getDisplayName() {
            return "Set GitHub commit status with custom context and message (Must configure upstream job using GHPRB trigger)";
        }
    }


}
