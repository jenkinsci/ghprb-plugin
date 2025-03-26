package org.jenkinsci.plugins.ghprb.jobdsl;

import antlr.ANTLRException;
import com.google.common.base.Joiner;
import hudson.Extension;
import javaposse.jobdsl.dsl.helpers.publisher.PublisherContext;
import javaposse.jobdsl.dsl.helpers.triggers.TriggerContext;
import javaposse.jobdsl.dsl.helpers.wrapper.WrapperContext;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslExtensionMethod;
import org.jenkinsci.plugins.ghprb.GhprbPullRequestMerge;
import org.jenkinsci.plugins.ghprb.GhprbTrigger;
import org.jenkinsci.plugins.ghprb.upstream.GhprbUpstreamStatus;


@Extension(optional = true)
public class GhprbContextExtensionPoint extends ContextExtensionPoint {
    @DslExtensionMethod(context = TriggerContext.class)
    public Object githubPullRequest(Runnable closure) throws ANTLRException {
        GhprbTriggerContext context = new GhprbTriggerContext();
        executeInContext(closure, context);
        return new GhprbTrigger(
                Joiner.on("\n").join(context.admins),
                Joiner.on("\n").join(context.userWhitelist),
                Joiner.on("\n").join(context.orgWhitelist),
                context.cron,
                context.triggerPhrase,
                context.onlyTriggerPhrase,
                context.useGitHubHooks,
                context.permitAll,
                context.autoCloseFailedPullRequests,
                context.displayBuildErrorsOnDownstreamBuilds,
                null,
                context.skipBuildPhrase,
                context.blackListCommitAuthor,
                context.whiteListTargetBranches,
                context.blackListTargetBranches,
                context.allowMembersOfWhitelistedOrgsAsAdmin,
                null,
                null,
                null,
                null,
                context.buildDescriptionTemplate,
                Joiner.on("\n").join(context.blackListLabels),
                Joiner.on("\n").join(context.whiteListLabels),
                context.extensionContext.getExtensions(),
                context.includedRegions,
                context.excludedRegions,
                context.reportSuccessIfNotRegion
        );
    }

    @DslExtensionMethod(context = PublisherContext.class)
    public Object mergeGithubPullRequest(Runnable closure) {
        GhprbPullRequestMergeContext context = new GhprbPullRequestMergeContext();
        executeInContext(closure, context);

        return new GhprbPullRequestMerge(
                context.mergeComment,
                context.onlyAdminsMerge,
                context.disallowOwnCode,
                context.failOnNonMerge,
                context.deleteOnMerge,
                context.allowMergeWithoutTriggerPhrase);
    }

    @DslExtensionMethod(context = WrapperContext.class)
    public Object downstreamCommitStatus(Runnable closure) {
        GhprbUpstreamStatusContext context = new GhprbUpstreamStatusContext();
        executeInContext(closure, context);

        return new GhprbUpstreamStatus(
                context.showMatrixStatus,
                context.context,
                context.statusUrl,
                context.triggeredStatus,
                context.startedStatus,
                context.addTestResults,
                context.completedStatus
        );
    }
}
