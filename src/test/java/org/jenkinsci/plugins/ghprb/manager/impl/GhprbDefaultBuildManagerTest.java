package org.jenkinsci.plugins.ghprb.manager.impl;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.HashMap;
import java.util.Map;


import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;

import org.jenkinsci.plugins.ghprb.GhprbITBaseTestCase;
import org.jenkinsci.plugins.ghprb.GhprbTestUtil;
import org.jenkinsci.plugins.ghprb.manager.GhprbBuildManager;
import org.jenkinsci.plugins.ghprb.manager.factory.GhprbBuildManagerFactoryUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
@RunWith(MockitoJUnitRunner.class)
public class GhprbDefaultBuildManagerTest extends GhprbITBaseTestCase {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    
    private MatrixProject project;

    @Before
    public void setUp() throws Exception {
        // GhprbTestUtil.mockGithubUserPage();
        project = jenkinsRule.getInstance().createProject(MatrixProject.class, "MTXPRJ");
       
        Map<String, Object> config = new HashMap<String, Object>(1);
       
        config.put("publishedURL", "defaultPublishedURL");
        super.beforeTest(config,  null, project);
    }

    @Test
    public void shouldCalculateUrlFromDefault() throws Exception {
        
        // GIVEN
        givenThatGhprbHasBeenTriggeredForAMatrixProject();

        // THEN
        assertThat(project.getBuilds().toArray().length).isEqualTo(1);

        MatrixBuild matrixBuild = project.getBuilds().getFirstBuild();

        GhprbBuildManager buildManager = GhprbBuildManagerFactoryUtil.getBuildManager(matrixBuild);

        assertThat(buildManager).isInstanceOf(GhprbDefaultBuildManager.class);

        assertThat(buildManager.calculateBuildUrl("defaultPublishedURL")).isEqualTo("defaultPublishedURL/" + matrixBuild.getUrl());
    }

    private void givenThatGhprbHasBeenTriggeredForAMatrixProject() throws Exception {
        given(commitPointer.getSha()).willReturn("sha");
        
        given(ghPullRequest.getNumber()).willReturn(1);

        given(ghRepository.getPullRequest(1)).willReturn(ghPullRequest);

        GhprbTestUtil.triggerRunAndWait(10, trigger, project);

    }
}