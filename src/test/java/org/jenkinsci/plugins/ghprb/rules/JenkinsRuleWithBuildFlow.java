package org.jenkinsci.plugins.ghprb.rules;

import com.cloudbees.plugins.flow.BuildFlow;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public class JenkinsRuleWithBuildFlow extends JenkinsRule {

    public BuildFlow createBuildFlowProject() throws IOException {
        return createBuildFlowProject(createUniqueProjectName());
    }

    public BuildFlow createBuildFlowProject(String name) throws IOException {
        return jenkins.createProject(BuildFlow.class, name);
    }
}
