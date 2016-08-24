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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Extension
public class GhprbAdditionalParameterEnvironmentContributor extends EnvironmentContributor {

    private static Set<String> params =
                    new HashSet<String>(Arrays.asList("sha1",
                                                      "ghprbCredentialsId",
                                                      "ghprbActualCommit",
                                                      "ghprbPullId",
                                                      "ghprbPullDescription",
                                                      "ghprbPullTitle",
                                                      "ghprbPullLink",
                                                      "ghprbPullLongDescription",
                                                      "ghprbTargetBranch",
                                                      "ghprbSourceBranch",
                                                      "ghprbCommentBody",
                                                      "ghprbGhRepository",
                                                      "ghprbTriggerAuthor",
                                                      "ghprbTriggerAuthorEmail",
                                                      "ghprbTriggerAuthorLogin",
                                                      "ghprbTriggerAuthorLoginMention",
                                                      "GIT_BRANCH",
                                                      "ghprbPullAuthorEmail",
                                                      "ghprbPullAuthorLogin",
                                                      "ghprbPullAuthorLoginMention",
                                                      "ghprbAuthorRepoGitUrl",
                                                      "ghprbActualCommitAuthor",
                                                      "ghprbActualCommitAuthorEmail"));

    @Override
    @SuppressWarnings("rawtypes")
    public void buildEnvironmentFor(@Nonnull Run run,
                                    @Nonnull EnvVars envs,
                                    @Nonnull TaskListener listener) throws IOException, InterruptedException {
        GhprbCause cause = (GhprbCause) ((Run<?, ?>) run).getCause(GhprbCause.class);
        if (cause == null || envs.containsKey(params.iterator().next())) {
            return;
        }

        ParametersAction pa = run.getAction(ParametersAction.class);
        for (String param : params) {
            addParameter(param, pa, envs);
        }
    }

    private static void addParameter(String key,
                                     ParametersAction pa,
                                     EnvVars envs) {
        ParameterValue pv = pa.getParameter(key);
        if (pv == null || !(pv instanceof StringParameterValue)) {
            return;
        }
        StringParameterValue value = (StringParameterValue) pa.getParameter(key);
        envs.put(key, getString(value.value, ""));
    }

    private static String getString(String actual,
                                    String d) {
        return actual == null ? d : actual;
    }
}