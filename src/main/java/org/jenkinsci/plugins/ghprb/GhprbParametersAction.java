package org.jenkinsci.plugins.ghprb;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


@Restricted(NoExternalUse.class)
public class GhprbParametersAction extends ParametersAction {

    public GhprbParametersAction(List<ParameterValue> parameters) {
        super(parameters);
    }

    @Extension
    public static final class GhprbAdditionalParameterEnvironmentContributor extends EnvironmentContributor {

        // See SECURITY-170

        @Override
        @SuppressWarnings("rawtypes")
        public void buildEnvironmentFor(@Nonnull Run run,
                                        @Nonnull EnvVars envs,
                                        @Nonnull TaskListener listener) throws IOException, InterruptedException {

            GhprbParametersAction gpa = run.getAction(GhprbParametersAction.class);
            if (gpa != null) {
                for (ParameterValue p : gpa.getParameters()) {
                    envs.put(p.getName(), String.valueOf(p.getValue()));
                }
            }
        }
    }
}

