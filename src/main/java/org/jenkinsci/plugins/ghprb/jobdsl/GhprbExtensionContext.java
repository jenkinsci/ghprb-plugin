package org.jenkinsci.plugins.ghprb.jobdsl;

import javaposse.jobdsl.dsl.Context;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.status.GhprbSimpleStatus;

import java.util.ArrayList;
import java.util.List;

class GhprbExtensionContext implements Context {
    List<GhprbExtension> extensions = new ArrayList<GhprbExtension>();

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
}
