package org.jenkinsci.plugins.ghprb;

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
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

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class GhprbPullRequestMerge extends Recorder implements SimpleBuildStep {
    private transient TaskListener listener;

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
        return allowMergeWithoutTriggerPhrase == null ? Boolean.valueOf(false) : allowMergeWithoutTriggerPhrase;
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
    public void perform(
            @Nonnull Run<?, ?> run,
            @Nonnull FilePath filePath,
            @Nonnull Launcher launcher,
            @Nonnull TaskListener taskListener
    ) throws InterruptedException, IOException {
        listener = taskListener;
        Job<?, ?> project = run.getParent();
        if (run.getResult().isWorseThan(Result.SUCCESS)) {
            listener.getLogger().println("Build did not succeed, merge will not be run");
            return;
        }

        trigger = Ghprb.extractTrigger(project);
        if (trigger == null) {
            return;
        }

        cause = Ghprb.getCause(run);
        if (cause == null) {
            return;
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
            listener.getLogger().println("Comment from bot user " + triggerSender.getLogin() + " ignored.");
            return;
        }

        boolean intendToMerge = false;
        boolean canMerge = true;
        String commentBody = cause.getCommentBody();

        // If merge can only be triggered by a comment and there is a comment
        if (!getAllowMergeWithoutTriggerPhrase() && (commentBody == null || !helper.isTriggerPhrase(commentBody))) {
            listener.getLogger().println("The comment does not contain the required trigger phrase.");
        } else {
            intendToMerge = true;
        }

        // If there is no intention to merge there is no point checking
        if (intendToMerge && getOnlyAdminsMerge() && (triggerSender == null || !helper.isAdmin(triggerSender))) {
            canMerge = false;
            final String msg = "Only admins can merge this pull request, "
                    + (triggerSender != null ? triggerSender.getLogin()
                    + " is not an admin" : " and build was triggered via automation") + ".";
            listener.getLogger().println(msg);
            if (triggerSender != null) {
                commentOnRequest(
                        String.format(
                                "Code not merged because @%s (%s) is not in the Admin list.",
                                triggerSender.getLogin(),
                                triggerSender.getName()
                        )
                );
            }
        }

        // If there is no intention to merge there is no point checking
        if (intendToMerge && getDisallowOwnCode() && (triggerSender == null || isOwnCode(pr, triggerSender))) {
            canMerge = false;
            if (triggerSender != null) {
                listener.getLogger().println("The commentor is also one of the contributors.");
                commentOnRequest(
                        String.format(
                                "Code not merged because @%s (%s) has committed code in the request.",
                                triggerSender.getLogin(),
                                triggerSender.getName()
                        )
                );
            }
        }

        Boolean isMergeable = cause.isMerged();

        // The build should not fail if no merge is expected
        if (intendToMerge && canMerge && (isMergeable == null || !isMergeable)) {
            listener.getLogger().println("Pull request cannot be automerged.");
            commentOnRequest("Pull request is not mergeable.");

            listener.error(
                "%: Pull request is not mergeable. isMergeable=%s",
                Result.FAILURE.toString(),
                isMergeable
            );

            return;
        }

        if (intendToMerge && canMerge) {
            listener.getLogger().println("Merging the pull request");

            try {
                Field ghRootField = GHIssue.class.getDeclaredField("root");
                ghRootField.setAccessible(true);
                Object ghRoot = ghRootField.get(pr);
                Method anonMethod = GitHub.class.getMethod("isAnonymous");
                anonMethod.setAccessible(true);
                Boolean isAnonymous = (Boolean) (anonMethod.invoke(ghRoot));
                listener.getLogger().println("Merging PR[" + pr + "] is anonymous: " + isAnonymous);
            } catch (Exception e) {
                e.printStackTrace(listener.getLogger());
            }
            String mergeComment = Ghprb.replaceMacros(run, listener, getMergeComment());
            pr.merge(mergeComment);
            listener.getLogger().println("Pull request successfully merged");
            deleteBranch(run, launcher, listener);
        }

        // We should only fail the build if there is an intent to merge
        if (intendToMerge && !canMerge && getFailOnNonMerge()) {
            run.setResult(Result.FAILURE);
            return;
        }
    }

    private void deleteBranch(Run<?, ?> build, Launcher launcher, final TaskListener listener) {
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
            listener.getLogger().println("Failed to add comment");
            e.printStackTrace(listener.getLogger());
        }
    }

    private boolean isOwnCode(GHPullRequest pr, GHUser commenter) {
        try {
            String commenterName = commenter.getName();
            String commenterEmail = commenter.getEmail();
            String commenterLogin = commenter.getLogin();

            GHUser prUser = pr.getUser();
            if (prUser.getLogin().equals(commenterLogin)) {
                final String msg = commenterName + " (" + commenterLogin + ")  has submitted "
                        + "the PR[" + pr.getNumber() + pr.getNumber() + "] that is to be merged";
                listener.getLogger().println(msg);
                return true;
            }

            for (GHPullRequestCommitDetail detail : pr.listCommits()) {
                Commit commit = detail.getCommit();
                GitUser committer = commit.getCommitter();
                String committerName = committer.getName();
                String committerEmail = committer.getEmail();

                boolean isSame = false;

                isSame |= commenterName != null && commenterName.equals(committerName);
                isSame |= commenterEmail != null && commenterEmail.equals(committerEmail);

                if (isSame) {
                    final String msg = commenterName + " (" + commenterEmail + ")  has commits in "
                            + "PR[" + pr.getNumber() + "] that is to be merged";
                    listener.getLogger().println(msg);
                    return isSame;
                }
            }
        } catch (IOException e) {
            listener.getLogger().println("Unable to get committer name");
            e.printStackTrace(listener.getLogger());
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
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public FormValidation doCheck(@AncestorInPath Job<?, ?> project, @QueryParameter String value) throws IOException {
            FilePath buildDirectory = new FilePath(project.getBuildDir());
            return FilePath.validateFileMask(buildDirectory, value);
        }
    }

}
