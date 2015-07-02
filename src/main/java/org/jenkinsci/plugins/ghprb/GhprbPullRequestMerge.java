package org.jenkinsci.plugins.ghprb;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.ConcurrentMap;

import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHPullRequestCommitDetail.Commit;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHUser;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.google.common.annotations.VisibleForTesting;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;

public class GhprbPullRequestMerge extends Recorder {

    private PrintStream logger;

    private final boolean onlyAdminsMerge;
    private final boolean disallowOwnCode;
    private boolean onlyTriggerPhrase;
    private String mergeComment;

    @DataBoundConstructor
    public GhprbPullRequestMerge(String mergeComment, boolean onlyTriggerPhrase, boolean onlyAdminsMerge, boolean disallowOwnCode) {

        this.mergeComment = mergeComment;
        this.onlyTriggerPhrase = onlyTriggerPhrase;
        this.onlyAdminsMerge = onlyAdminsMerge;
        this.disallowOwnCode = disallowOwnCode;
    }

    public String getMergeComment() {
        return mergeComment;
    }

    public boolean isOnlyTriggerPhrase() {
        return onlyTriggerPhrase;
    }

    public boolean isOnlyAdminsMerge() {
        return onlyAdminsMerge;
    }

    public boolean isDisallowOwnCode() {
        return disallowOwnCode;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    private GhprbTrigger trigger;
    private Ghprb helper;
    private GhprbCause cause;
    private GHPullRequest pr;

    @VisibleForTesting
    void setHelper(Ghprb helper) {
        this.helper = helper;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
        logger = listener.getLogger();
        AbstractProject<?, ?> project = build.getProject();
        if (build.getResult().isWorseThan(Result.SUCCESS)) {
            logger.println("Build did not succeed, merge will not be run");
            return true;
        }

        trigger = Ghprb.extractTrigger(project);
        if (trigger == null)
            return false;

        cause = getCause(build);
        if (cause == null) {
            return true;
        }

        ConcurrentMap<Integer, GhprbPullRequest> pulls = trigger.getDescriptor().getPullRequests(project.getFullName());

        pr = pulls.get(cause.getPullID()).getPullRequest();

        if (pr == null) {
            logger.println("Pull request is null for ID: " + cause.getPullID());
            logger.println("" + pulls.toString());
            return false;
        }

        Boolean isMergeable = cause.isMerged();

        if (helper == null) {
            helper = new Ghprb(project, trigger, pulls);
            helper.init();
        }

        if (isMergeable == null || !isMergeable) {
            logger.println("Pull request cannot be automerged.");
            commentOnRequest("Pull request is not mergeable.");
            listener.finished(Result.FAILURE);
            return false;
        }

        GHUser triggerSender = cause.getTriggerSender();

        // ignore comments from bot user, this fixes an issue where the bot would auto-merge
        // a PR when the 'request for testing' phrase contains the PR merge trigger phrase and
        // the bot is a member of a whitelisted organisation
        if (helper.isBotUser(triggerSender)) {
            logger.println("Comment from bot user " + triggerSender.getLogin() + " ignored.");
            return false;
        }

        boolean merge = true;
        String commentBody = cause.getCommentBody();

        if (isOnlyAdminsMerge() && (triggerSender == null || !helper.isAdmin(triggerSender) )) {
            merge = false;
            logger.println("Only admins can merge this pull request, " + triggerSender.getLogin() + " is not an admin.");
            commentOnRequest(String.format("Code not merged because %s is not in the Admin list.", triggerSender.getName()));
        }

        if (isOnlyTriggerPhrase() && (commentBody == null || !helper.isTriggerPhrase(cause.getCommentBody()) )) {
            merge = false;
            logger.println("The comment does not contain the required trigger phrase.");

            commentOnRequest(String.format("Please comment with '%s' to automerge this request", trigger.getTriggerPhrase()));
        }

        if (isDisallowOwnCode() && (triggerSender == null || isOwnCode(pr, triggerSender) )) {
            merge = false;
            logger.println("The commentor is also one of the contributors.");
            commentOnRequest(String.format("Code not merged because %s has committed code in the request.", triggerSender.getName()));
        }

        if (merge) {
            logger.println("Merging the pull request");

            pr.merge(getMergeComment());
            logger.println("Pull request successfully merged");
            // deleteBranch(); //TODO: Update so it also deletes the branch being pulled from. probably make it an option.
        }

        if (merge) {
            listener.finished(Result.SUCCESS);
        } else {
            listener.finished(Result.FAILURE);
        }
        return merge;
    }

    private void deleteBranch() {
        String branchName = pr.getHead().getRef();
        try {
            GHBranch branch = pr.getRepository().getBranches().get(branchName);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void commentOnRequest(String comment) {
        try {
            helper.getRepository().addComment(pr.getNumber(), comment);
        } catch (Exception e) {
            logger.println("Failed to add comment");
            e.printStackTrace(logger);
        }
    }

    private boolean isOwnCode(GHPullRequest pr, GHUser committer) {
        try {
            String commentorName = committer.getName();
            for (GHPullRequestCommitDetail detail : pr.listCommits()) {
                Commit commit = detail.getCommit();
                String committerName = commit.getCommitter().getName();

                if (committerName.equalsIgnoreCase(commentorName)) {
                    return true;
                }
            }
        } catch (IOException e) {
            logger.println("Unable to get committer name");
            e.printStackTrace(logger);
        }
        return false;
    }

    private GhprbCause getCause(AbstractBuild<?, ?> build) {
        Cause cause = build.getCause(GhprbCause.class);
        if (cause == null || (!(cause instanceof GhprbCause)))
            return null;
        return (GhprbCause) cause;
    }

    @Extension(ordinal = -1)
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public String getDisplayName() {
            return "Github Pull Request Merger";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public FormValidation doCheck(@AncestorInPath AbstractProject<?, ?> project, @QueryParameter String value) throws IOException {
            return FilePath.validateFileMask(project.getSomeWorkspace(), value);
        }

    }

}
