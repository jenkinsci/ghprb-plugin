package org.jenkinsci.plugins.ghprb;

import hudson.model.*;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.util.List;

public class GhprbRetriggerAction implements Action {

    private String projectName;
    private List<ParameterValue> values;
    private GhprbCause cause;

    public GhprbRetriggerAction(String projectName, List<ParameterValue> values, GhprbCause cause) {
        this.projectName = projectName;
        this.values = values;
        this.cause = cause;
    }

    public String getIconFileName() {
        return "redo.png";
    }

    public String getDisplayName() {
        return "Retrigger";
    }

    public String getUrlName() {
        return "ghprb-retrigger";
    }

    @SuppressWarnings("UnusedDeclaration")
    public void doIndex(StaplerRequest request, StaplerResponse response) throws IOException {
        // Find the PullID
        StringParameterValue pullId = null;
        for (ParameterValue value : values) {
            if (value instanceof StringParameterValue && "ghprbPullId".equals(value.getName())) {
                pullId = (StringParameterValue) value;
                break;
            }
        }
        if (pullId == null) {
            throw new IllegalStateException();
        }

        AbstractProject<?, ?> project = (AbstractProject<?, ?>) Jenkins.getInstance().getItem(projectName);;

        project.scheduleBuild2(0, cause,
                new ParametersAction(values),
                GhprbTrigger.findPreviousBuildForPullId(pullId, project),
                new GhprbRetriggerAction(projectName, values, cause));

        response.sendRedirect2(project.getAbsoluteUrl());
    }
}
