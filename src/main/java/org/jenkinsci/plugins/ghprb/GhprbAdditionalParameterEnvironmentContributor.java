package org.jenkinsci.plugins.ghprb;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;

import javax.annotation.Nonnull;
import java.io.IOException;

@Extension(optional = true)
public class GhprbAdditionalParameterEnvironmentContributor extends EnvironmentContributor {

    /*
     * Fail the load of this extension if ParametersAction.getAllParameter() method doesn't exist
     */
    static {
        try {
            ParametersAction.class.getMethod("getAllParameters");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void buildEnvironmentFor(@Nonnull Run r, @Nonnull EnvVars envs, @Nonnull TaskListener listener)
            throws IOException, InterruptedException {
        GhprbCause cause = (GhprbCause) r.getCause(GhprbCause.class);
        ParametersAction pa = r.getAction(ParametersAction.class);
        if (cause != null && pa != null) {
            final String commitSha = cause.isMerged() ? "origin/pr/" + cause.getPullID() + "/merge" : cause.getCommit();
            putEnvVar(envs, "sha1", commitSha);
            putEnvVar(envs, "ghprbActualCommit", cause.getCommit());

            try {
                putEnvVar(envs, "ghprbTriggerAuthor", cause.getTriggerSender().getName());
            } catch (Exception e) {
            }
            try {
                putEnvVar(envs, "ghprbTriggerAuthorEmail", cause.getTriggerSender().getEmail());
            } catch (Exception e) {
            }

            String triggerAuthorLogin = "";
            try {
                triggerAuthorLogin = cause.getTriggerSender().getLogin();
                if (triggerAuthorLogin == null) {
                    triggerAuthorLogin = "";
                }
                putEnvVar(envs, "ghprbTriggerAuthorLogin", triggerAuthorLogin);
            } catch (Exception e) {
            }

            putEnvVar(envs, "ghprbActualCommitAuthor", cause.getCommitAuthor().getName());
            putEnvVar(envs, "ghprbActualCommitAuthorEmail", cause.getCommitAuthor().getEmail());
            putEnvVar(envs, "ghprbAuthorRepoGitUrl", cause.getAuthorRepoGitUrl());

            putEnvVar(envs, "ghprbTriggerAuthorLoginMention", !triggerAuthorLogin.isEmpty() ? "@"
                    + triggerAuthorLogin : "");
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

            // Cheating for now as the data is not available in the Cause
            for (ParameterValue pv : pa.getAllParameters()) {
                if (pv.getName() != null && pv.getValue() != null && pv instanceof StringParameterValue) {
                    if ("ghprbGhRepository".equals(pv.getName()) || "ghprbCredentialsId".equals(pv.getName()))
                        envs.put(pv.getName(), pv.getValue().toString());
                }
            }
        }
    }

    private static String escapeText(String text) {
        return text.replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"");
    }

    private static void putEnvVar(@Nonnull EnvVars envs, String name, String value){
        if (value != null) {
            envs.put(name, value);
        } else {
            envs.put(name, "");
        }
    }
}
