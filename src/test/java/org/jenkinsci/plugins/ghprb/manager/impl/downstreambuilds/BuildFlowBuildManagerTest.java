package org.jenkinsci.plugins.ghprb.manager.impl.downstreambuilds;

import com.cloudbees.plugins.flow.BuildFlow;
import com.cloudbees.plugins.flow.FlowRun;
import com.cloudbees.plugins.flow.JobInvocation;
import org.jenkinsci.plugins.ghprb.GhprbITBaseTestCase;
import org.jenkinsci.plugins.ghprb.GhprbTestUtil;
import org.jenkinsci.plugins.ghprb.manager.GhprbBuildManager;
import org.jenkinsci.plugins.ghprb.manager.factory.GhprbBuildManagerFactoryUtil;
import org.jenkinsci.plugins.ghprb.rules.JenkinsRuleWithBuildFlow;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Iterator;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
@RunWith(MockitoJUnitRunner.class)
public class BuildFlowBuildManagerTest extends GhprbITBaseTestCase {

    @Rule
    public JenkinsRuleWithBuildFlow jenkinsRule = new JenkinsRuleWithBuildFlow();

    private BuildFlow buildFlowProject;

    @Before
    public void setUp() throws Exception {

        buildFlowProject = jenkinsRule.createBuildFlowProject();

        jenkinsRule.createFreeStyleProject("downstreamProject1");
        jenkinsRule.createFreeStyleProject("downstreamProject2");
        jenkinsRule.createFreeStyleProject("downstreamProject3");

        StringBuilder dsl = new StringBuilder();

        dsl.append("parallel (");
        dsl.append("    { build(\"downstreamProject1\") },");
        dsl.append("    { build(\"downstreamProject2\") }");
        dsl.append(")");
        dsl.append("{ build(\"downstreamProject3\") }");

        buildFlowProject.setDsl(dsl.toString());

        given(ghPullRequest.getNumber()).willReturn(1);
        given(ghRepository.getPullRequest(1)).willReturn(ghPullRequest);

        super.beforeTest(null, null, buildFlowProject);
    }

    @Test
    public void shouldCalculateUrlWithDownstreamBuilds() throws Exception {
        // GIVEN
        GhprbTestUtil.triggerRunAndWait(10, trigger, buildFlowProject);

        // THEN
        assertThat(buildFlowProject.getBuilds().toArray().length).isEqualTo(1);

        FlowRun flowRun = buildFlowProject.getBuilds().getFirstBuild();

        GhprbBuildManager buildManager = GhprbBuildManagerFactoryUtil.getBuildManager(flowRun);

        assertThat(buildManager).isInstanceOf(BuildFlowBuildManager.class);

        Iterator<?> iterator = buildManager.downstreamProjects();

        StringBuilder expectedUrl = new StringBuilder();

        int count = 0;

        while (iterator.hasNext()) {
            Object downstreamBuild = iterator.next();

            assertThat(downstreamBuild).isInstanceOf(JobInvocation.class);

            JobInvocation jobInvocation = (JobInvocation) downstreamBuild;

            String jobInvocationBuildUrl = jobInvocation.getBuildUrl();

            expectedUrl.append("\n<a href='");
            expectedUrl.append(jobInvocationBuildUrl);
            expectedUrl.append("'>");
            expectedUrl.append(jobInvocationBuildUrl);
            expectedUrl.append("</a>");

            count++;
        }

        assertThat(count).isEqualTo(4);

        assertThat(buildManager.calculateBuildUrl(null)).isEqualTo(expectedUrl.toString());
    }

}
