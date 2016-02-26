package org.jenkinsci.plugins.ghprb.jobdsl;

import javaposse.jobdsl.dsl.Context;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildResultMessage;
import org.kohsuke.github.GHCommitState;

import java.util.ArrayList;
import java.util.List;

class GhprbUpstreamStatusContext implements Context {
    String context;
    String triggeredStatus;
    String startedStatus;
    String statusUrl;
    Boolean addTestResults;
    List<GhprbBuildResultMessage> completedStatus = new ArrayList<GhprbBuildResultMessage>();

    /**
     * A string label to differentiate this status from the status of other systems.
     */
    void context(String context) {
        this.context = context;
    }

    /**
     * Use a custom status for when a build is triggered.
     */
    void triggeredStatus(String triggeredStatus) {
        this.triggeredStatus = triggeredStatus;
    }

    /**
     * Use a custom status for when a build is started.
     */
    void startedStatus(String startedStatus) {
        this.startedStatus = startedStatus;
    }

    /**
     * Use a custom URL instead of the job default.
     */
    void statusUrl(String statusUrl) {
        this.statusUrl = statusUrl;
    }
    
    /**
     * Add the test results as one line if available
     */
    void addTestResults(Boolean addTestResults) {
        this.addTestResults = addTestResults;
    }

    /**
     * Use a custom status for when a build is completed. Can be called multiple times to set messages for different
     * build results. Valid build results are {@code 'SUCCESS'}, {@code 'FAILURE'}, and {@code 'ERROR'}.
     */
    void completedStatus(String buildResult, String message) {
        completedStatus.add(new GhprbBuildResultMessage(
                GHCommitState.valueOf(buildResult),
                message
        ));
    }
}
