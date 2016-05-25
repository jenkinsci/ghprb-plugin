package org.jenkinsci.plugins.ghprb;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;

import javax.annotation.Nonnull;

import java.io.IOException;

@Extension
public class GhprbAdditionalParameterEnvironmentContributor extends EnvironmentContributor {

    @Override
    @SuppressWarnings("rawtypes")
    public void buildEnvironmentFor(@Nonnull Run run,
                                    @Nonnull EnvVars envs,
                                    @Nonnull TaskListener listener) throws IOException, InterruptedException {
        GhprbCause cause = (GhprbCause) ((Run<?, ?>) run).getCause(GhprbCause.class);
        if (cause == null) {
            return;
        }

        final String commitSha = cause.isMerged() ? "origin/pr/" + cause.getPullID() + "/merge" : cause.getCommit();
        putEnvVar(envs, "sha1", commitSha);
        putEnvVar(envs, "ghprbActualCommit", cause.getCommit());

        try {
            putEnvVar(envs, "ghprbTriggerAuthor", cause.getTriggerSender().getName());
        } catch (Exception e) {}
        try {
            putEnvVar(envs, "ghprbTriggerAuthorEmail", cause.getTriggerSender().getEmail());
        } catch (Exception e) {}

        String triggerAuthorLogin = "";
        try {
            triggerAuthorLogin = cause.getTriggerSender().getLogin();
            if (triggerAuthorLogin == null) {
                triggerAuthorLogin = "";
            }
            putEnvVar(envs, "ghprbTriggerAuthorLogin", triggerAuthorLogin);
        } catch (Exception e) {}

        setCommitAuthor(cause, envs);

        putEnvVar(envs, "ghprbAuthorRepoGitUrl", cause.getAuthorRepoGitUrl());

        putEnvVar(envs,
                  "ghprbTriggerAuthorLoginMention",
                  !triggerAuthorLogin.isEmpty() ? "@" + triggerAuthorLogin : "");
        putEnvVar(envs, "ghprbPullId", String.valueOf(cause.getPullID()));
        putEnvVar(envs, "ghprbTargetBranch", cause.getTargetBranch());
        putEnvVar(envs, "ghprbSourceBranch", cause.getSourceBranch());
        putEnvVar(envs, "GIT_BRANCH", cause.getSourceBranch());

        // it's possible the GHUser doesn't have an associated email address
        putEnvVar(envs, "ghprbPullAuthorEmail", cause.getAuthorEmail());
        putEnvVar(envs, "ghprbPullAuthorLogin", cause.getPullRequestAuthor().getLogin());
        putEnvVar(envs, "ghprbPullAuthorLoginMention", "@" + cause.getPullRequestAuthor().getLogin());

        putEnvVar(envs, "ghprbPullDescription", escapeText(String.valueOf(cause.getShortDescription())));
        putEnvVar(envs, "ghprbPullTitle", cause.getTitle());
        putEnvVar(envs, "ghprbPullLink", String.valueOf(cause.getUrl()));
        putEnvVar(envs, "ghprbPullLongDescription", escapeText(String.valueOf(cause.getDescription())));

        putEnvVar(envs, "ghprbCommentBody", escapeText(String.valueOf(cause.getCommentBody())));

        putEnvVar(envs, "ghprbGhRepository", escapeText(String.valueOf(cause.getRepositoryName())));
        putEnvVar(envs, "ghprbCredentialsId", escapeText(String.valueOf(cause.getCredentialsId())));

    }


    private void setCommitAuthor(GhprbCause cause,
                                 EnvVars values) {
        String authorName = "";
        String authorEmail = "";
        if (cause.getCommitAuthor() != null) {
            authorName = getString(cause.getCommitAuthor().getName(), "");
            authorEmail = getString(cause.getCommitAuthor().getEmail(), "");
        }

        putEnvVar(values, "ghprbActualCommitAuthor", authorName);
        putEnvVar(values, "ghprbActualCommitAuthorEmail", authorEmail);
    }

    private static String getString(String actual,
                                    String d) {
        return actual == null ? d : actual;
    }

    private static String escapeText(String text) {
        return text.replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"");
    }

    private static void putEnvVar(@Nonnull EnvVars envs,
                                  String name,
                                  String value) {
        envs.put(name, getString(value, ""));
    }
}