package org.jenkinsci.plugins.ghprb;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildWrapper;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Kevin Suwala
 */

public class GhprbCustomStatus extends BuildWrapper {
    private static final Logger logger = Logger.getLogger(Ghprb.class.getName());
    private String context = "";
    private String message = "";
    public String getContext() {
        return context;
    }

    public String getMessage() {
        return message;
    }

    @DataBoundConstructor
    public GhprbCustomStatus(String context, String message) {
        this.message = message;
        this.context = context;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<BuildWrapper> {
        public DescriptorImpl() {
            load();
        }

        @Override
        public String getDisplayName() {
            return "Set GitHub commit status with custom context and message (Must configure upstream job using GHPRB)";
        }

        public FormValidation doCheckValue(@QueryParameter String value) throws IOException, ServletException {
            if(value.isEmpty()) {
                return FormValidation.error("You must have a context!");
            }
            return FormValidation.ok();
        }
    }

    // sets the context and message as env vars so that they are available in the Listener class
    @Override
    public void makeBuildVariables(AbstractBuild build, Map<String,String> variables){
        variables.put("context", context);
        variables.put("message", message);
    }

    @Override
    public BuildWrapper.Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        makeBuildVariables(build, build.getBuildVariables());
        return new Environment(){};
    }

    @Override
    public void preCheckout(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        makeBuildVariables(build, build.getBuildVariables());
    }
}
