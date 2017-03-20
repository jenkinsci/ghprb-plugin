package org.jenkinsci.plugins.ghprb.upstream;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildWrapper;

import jenkins.tasks.SimpleBuildWrapper;
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

public class GhprbUpstreamStatus extends SimpleBuildWrapper {
    private final Boolean showMatrixStatus;
    private final String commitStatusContext;
    private final String triggeredStatus;
    private final String startedStatus;
    private final String statusUrl;
    private final Boolean addTestResults;
    private final List<GhprbBuildResultMessage> completedStatus;

    @Override
    public void setUp(Context context, Run<?, ?> run, FilePath filePath, Launcher launcher, TaskListener taskListener, EnvVars envVars) throws IOException, InterruptedException {
        envVars.put("ghprbShowMatrixStatus",Boolean.toString(getShowMatrixStatus()));
        envVars.put("ghprbUpstreamStatus", "true");
        envVars.put("ghprbCommitStatusContext", getCommitStatusContext());
        envVars.put("ghprbTriggeredStatus", getTriggeredStatus());
        envVars.put("ghprbStartedStatus", getStartedStatus());
        envVars.put("ghprbStatusUrl", getStatusUrl());
        envVars.put("ghprbAddTestResults", Boolean.toString(getAddTestResults()));

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
            envVars.put(key, next.getValue().toString());
        }
    }

    @DataBoundConstructor
    public GhprbUpstreamStatus(
            Boolean showMatrixStatus,
            String commitStatusContext, 
            String statusUrl, 
            String triggeredStatus, 
            String startedStatus, 
            Boolean addTestResults,
            List<GhprbBuildResultMessage> completedStatus
            ) {
        this.showMatrixStatus = showMatrixStatus;
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

    public Boolean getShowMatrixStatus(){
        return showMatrixStatus == null ? false : showMatrixStatus;
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
