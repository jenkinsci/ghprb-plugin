package org.jenkinsci.plugins.ghprb;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHPullRequestCommitDetail.Commit;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitUser;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.google.common.annotations.VisibleForTesting;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;

public class GhprbPullRequestMerge extends Recorder {

    private transient PrintStream logger;
    private final Boolean onlyAdminsMerge;

    private final Boolean disallowOwnCode;
    private final String mergeComment;
    private final Boolean failOnNonMerge;
    private final Boolean deleteOnMerge;
    private final Boolean allowMergeWithoutTriggerPhrase;

    @DataBoundConstructor
    public GhprbPullRequestMerge(String mergeComment, boolean onlyAdminsMerge, boolean disallowOwnCode, boolean failOnNonMerge,
                                 boolean deleteOnMerge, boolean allowMergeWithoutTriggerPhrase) {

        this.mergeComment = mergeComment;
        this.onlyAdminsMerge = onlyAdminsMerge;
        this.disallowOwnCode = disallowOwnCode;
        this.failOnNonMerge = failOnNonMerge;
        this.deleteOnMerge = deleteOnMerge;
        this.allowMergeWithoutTriggerPhrase = allowMergeWithoutTriggerPhrase;
    }

    public String getMergeComment() {
        return mergeComment;
    }

    public boolean getOnlyAdminsMerge() {
        return onlyAdminsMerge == null ? false : onlyAdminsMerge;
    }

    public boolean getDisallowOwnCode() {
        return disallowOwnCode == null ? false : disallowOwnCode;
    }

    public boolean getFailOnNonMerge() {
        return failOnNonMerge == null ? false : failOnNonMerge;
    }

    public boolean getDeleteOnMerge() {
        return deleteOnMerge == null ? false : deleteOnMerge;
    }

    public Boolean getAllowMergeWithoutTriggerPhrase() {
        return allowMergeWithoutTriggerPhrase == null ? false : allowMergeWithoutTriggerPhrase;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    private transient GhprbTrigger trigger;
    private transient Ghprb helper;
    private transient GhprbCause cause;
    private transient GHPullRequest pr;

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

        cause = Ghprb.getCause(build);
        if (cause == null) {
            return true;
        }

        pr = trigger.getRepository().getActualPullRequest(cause.getPullID());

        if (helper == null) {
            helper = new Ghprb(trigger);
        }

        GHUser triggerSender = cause.getTriggerSender();

        // ignore comments from bot user, this fixes an issue where the bot would auto-merge
        // a PR when the 'request for testing' phrase contains the PR merge trigger phrase and
        // the bot is a member of a whitelisted organization
        if (helper.isBotUser(triggerSender)) {
            logger.println("Comment from bot user " + triggerSender.getLogin() + " ignored.");
            return false;
        }

        boolean intendToMerge = false;
        boolean canMerge = true;
        String commentBody = cause.getCommentBody();

        // If merge can only be triggered by a comment and there is a comment
        if (!getAllowMergeWithoutTriggerPhrase() && (commentBody == null || !helper.isTriggerPhrase(commentBody))) {
            logger.println("The comment does not contain the required trigger phrase.");
        } else {
            intendToMerge = true;
        }

        // If there is no intention to merge there is no point checking
        if (intendToMerge && getOnlyAdminsMerge() && (triggerSender == null || !helper.isAdmin(triggerSender))) {
            canMerge = false;
            logger.println("Only admins can merge this pull request, " + (triggerSender != null ? triggerSender.getLogin() + " is not an admin" : " and build was triggered via automation") + ".");
            if (triggerSender != null) {
                commentOnRequest(String.format("Code not merged because @%s (%s) is not in the Admin list.", triggerSender.getLogin(), triggerSender.getName()));
            }
        }

        // If there is no intention to merge there is no point checking
        if (intendToMerge && getDisallowOwnCode() && (triggerSender == null || isOwnCode(pr, triggerSender))) {
            canMerge = false;
            if (triggerSender != null) {
                logger.println("The commentor is also one of the contributors.");
                commentOnRequest(String.format("Code not merged because @%s (%s) has committed code in the request.", triggerSender.getLogin(), triggerSender.getName()));
            }
        }

        Boolean isMergeable = cause.isMerged();

        // The build should not fail if no merge is expected
        if (intendToMerge && canMerge && (isMergeable == null || !isMergeable)) {
            logger.println("Pull request cannot be automerged.");
            commentOnRequest("Pull request is not mergeable.");
            listener.finished(Result.FAILURE);
            return false;
        }

        if (intendToMerge && canMerge) {
            logger.println("Merging the pull request");

            try {
                Field ghRootField = GHIssue.class.getDeclaredField("root");
                ghRootField.setAccessible(true);
                Object ghRoot = ghRootField.get(pr);
                Method anonMethod = GitHub.class.getMethod("isAnonymous");
                anonMethod.setAccessible(true);
                Boolean isAnonymous = (Boolean) (anonMethod.invoke(ghRoot));
                logger.println("Merging PR[" + pr + "] is anonymous: " + isAnonymous);
            } catch (Exception e) {
                e.printStackTrace(logger);
            }
            String mergeComment = Ghprb.replaceMacros(build, listener, getMergeComment());
            pr.merge(mergeComment);
            logger.println("Pull request successfully merged");
            deleteBranch(build, launcher, listener);
        }

        // We should only fail the build if there is an intent to merge
        if (intendToMerge && !canMerge && getFailOnNonMerge()) {
            listener.finished(Result.FAILURE);
        } else {
            listener.finished(Result.SUCCESS);
        }
        return canMerge;
    }

    private void deleteBranch(AbstractBuild<?, ?> build, Launcher launcher, final BuildListener listener) {
        if (!getDeleteOnMerge()) {
            return;
        }
        String branchName = pr.getHead().getRef();
        try {
            GHRepository repo = pr.getRepository();
            GHRef ref = repo.getRef("heads/" + branchName);
            ref.delete();
            listener.getLogger().println("Deleted branch " + branchName);

        } catch (IOException e) {
            listener.getLogger().println("Unable to delete branch " + branchName);
            e.printStackTrace(listener.getLogger());
        }
    }

    private void commentOnRequest(String comment) {
        try {
            trigger.getRepository().addComment(pr.getNumber(), comment);
        } catch (Exception e) {
            logger.println("Failed to add comment");
            e.printStackTrace(logger);
        }
    }

    private boolean isOwnCode(GHPullRequest pr, GHUser commentor) {
        try {
            String commentorName = commentor.getName();
            String commentorEmail = commentor.getEmail();
            String commentorLogin = commentor.getLogin();

            GHUser prUser = pr.getUser();
            if (prUser.getLogin().equals(commentorLogin)) {
                logger.println(commentorName + " (" + commentorLogin + ")  has submitted the PR[" + pr.getNumber() + pr.getNumber() + "] that is to be merged");
                return true;
            }

            for (GHPullRequestCommitDetail detail : pr.listCommits()) {
                Commit commit = detail.getCommit();
                GitUser committer = commit.getCommitter();
                String committerName = committer.getName();
                String committerEmail = committer.getEmail();

                boolean isSame = false;

                isSame |= commentorName != null && commentorName.equals(committerName);
                isSame |= commentorEmail != null && commentorEmail.equals(committerEmail);

                if (isSame) {
                    logger.println(commentorName + " (" + commentorEmail + ")  has commits in PR[" + pr.getNumber() + "] that is to be merged");
                    return isSame;
                }
            }
        } catch (IOException e) {
            logger.println("Unable to get committer name");
            e.printStackTrace(logger);
        }
        return false;
    }

    @Extension(ordinal = -1)
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public String getDisplayName() {
            return "Github Pull Request Merger";
        }

        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        public FormValidation doCheck(@AncestorInPath AbstractProject<?, ?> project, @QueryParameter String value) throws IOException {
            return FilePath.validateFileMask(project.getSomeWorkspace(), value);
        }

    }

}
