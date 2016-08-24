package org.jenkinsci.plugins.ghprb.manager.factory;

import static org.fest.assertions.Assertions.assertThat;

import com.cloudbees.plugins.flow.BuildFlow;
import com.cloudbees.plugins.flow.FlowRun;

import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;

import org.jenkinsci.plugins.ghprb.manager.GhprbBuildManager;
import org.jenkinsci.plugins.ghprb.manager.factory.GhprbBuildManagerFactoryUtil;
import org.jenkinsci.plugins.ghprb.manager.impl.GhprbDefaultBuildManager;
import org.jenkinsci.plugins.ghprb.manager.impl.downstreambuilds.BuildFlowBuildManager;
import org.jenkinsci.plugins.ghprb.rules.JenkinsRuleWithBuildFlow;

import org.junit.Rule;
import org.junit.Test;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public class GhprbBuildManagerFactoryUtilTest {

    @Rule
    public JenkinsRuleWithBuildFlow jenkinsRule = new JenkinsRuleWithBuildFlow();

    @Test
    public void shouldReturnDefaultManager() throws Exception {
        // GIVEN
        MatrixProject project = jenkinsRule.getInstance().createProject(MatrixProject.class, "PRJ");

        GhprbBuildManager buildManager = GhprbBuildManagerFactoryUtil.getBuildManager(new MatrixBuild(project));

        // THEN
        assertThat(buildManager).isInstanceOf(GhprbDefaultBuildManager.class);
    }

    @Test
    public void shouldReturnBuildFlowManager() throws Exception {
        // GIVEN
        BuildFlow buildFlowProject = jenkinsRule.createBuildFlowProject("BFPRJ");

        GhprbBuildManager buildManager = GhprbBuildManagerFactoryUtil.getBuildManager(new FlowRun(buildFlowProject));

        // THEN
        assertThat(buildManager).isInstanceOf(BuildFlowBuildManager.class);
    }

}