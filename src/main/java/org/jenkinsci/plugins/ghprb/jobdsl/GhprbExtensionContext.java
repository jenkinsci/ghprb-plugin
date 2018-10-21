package org.jenkinsci.plugins.ghprb.jobdsl;

import javaposse.jobdsl.dsl.Context;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.build.GhprbCancelBuildsOnUpdate;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildStatus;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbCommentFile;
import org.jenkinsci.plugins.ghprb.extensions.status.GhprbSimpleStatus;

import java.util.ArrayList;
import java.util.List;

class GhprbExtensionContext implements Context {
    private List<GhprbExtension> extensions = new ArrayList<GhprbExtension>();

    /**
     * Updates the commit status during the build.
     */
    void commitStatus(Runnable closure) {
        GhprbSimpleStatusContext context = new GhprbSimpleStatusContext();
        ContextExtensionPoint.executeInContext(closure, context);

        extensions.add(new GhprbSimpleStatus(
                context.showMatrixStatus,
                context.context,
                context.statusUrl,
                context.triggeredStatus,
                context.startedStatus,
                context.addTestResults,
                context.completedStatus
        ));
    }

    /**
     * Adds build result messages
     */
    void buildStatus(Runnable closure) {
        GhprbBuildStatusContext context = new GhprbBuildStatusContext();
        ContextExtensionPoint.executeInContext(closure, context);

        extensions.add(new GhprbBuildStatus(context.getCompletedStatus()));
    }

    /**
     * Adds comment file path handling
     */
    void commentFilePath(Runnable closure) {
        GhprbCommentFilePathContext context = new GhprbCommentFilePathContext();
        ContextExtensionPoint.executeInContext(closure, context);

        extensions.add(new GhprbCommentFile(context.getCommentFilePath()));
    }

    /**
     * Overrides global settings for cancelling builds when a PR was updated
     */
    void cancelBuildsOnUpdate(Runnable closure) {
        GhprbCancelBuildsOnUpdateContext context = new GhprbCancelBuildsOnUpdateContext();
        ContextExtensionPoint.executeInContext(closure, context);

        extensions.add(new GhprbCancelBuildsOnUpdate(context.getOverrideGlobal()));
    }

    public List<GhprbExtension> getExtensions() {
        return extensions;
    }
}
