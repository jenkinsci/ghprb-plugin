package org.jenkinsci.plugins.ghprb.jobdsl;

import javaposse.jobdsl.dsl.Context;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildResultMessage;
import org.kohsuke.github.GHCommitState;

import java.util.ArrayList;
import java.util.List;

class GhprbBuildStatusContext implements Context {
    List<GhprbBuildResultMessage> completedStatus = new ArrayList<GhprbBuildResultMessage>();

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
