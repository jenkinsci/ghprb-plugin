package org.jenkinsci.plugins.ghprb.manager.impl.downstreambuilds;

import static org.fest.assertions.Assertions.assertThat;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;

import com.cloudbees.plugins.flow.BuildFlow;
import com.cloudbees.plugins.flow.FlowRun;
import com.cloudbees.plugins.flow.JobInvocation;
import com.coravy.hudson.plugins.github.GithubProjectProperty;

import java.util.Iterator;

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.ghprb.Ghprb;
import org.jenkinsci.plugins.ghprb.GhprbITBaseTestCase;
import org.jenkinsci.plugins.ghprb.GhprbTestUtil;
import org.jenkinsci.plugins.ghprb.GhprbTrigger;
import org.jenkinsci.plugins.ghprb.manager.GhprbBuildManager;
import org.jenkinsci.plugins.ghprb.manager.factory.GhprbBuildManagerFactoryUtil;
import org.jenkinsci.plugins.ghprb.rules.JenkinsRuleWithBuildFlow;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
@RunWith(MockitoJUnitRunner.class)
public class BuildFlowBuildManagerTest extends GhprbITBaseTestCase {

	@Rule
	public JenkinsRuleWithBuildFlow jenkinsRule = new JenkinsRuleWithBuildFlow();

	@Before
	public void setUp() throws Exception {
		super.beforeTest();
	}

	@Test
	public void shouldCalculateUrlWithDownstreamBuilds() throws Exception {
		// GIVEN
		BuildFlow buildFlowProject =
			givenThatGhprbHasBeenTriggeredForABuildFlowProject();

		// THEN
		assertThat(buildFlowProject.getBuilds().toArray().length).isEqualTo(1);

		FlowRun flowRun = buildFlowProject.getBuilds().getFirstBuild();

		GhprbBuildManager buildManager =
			GhprbBuildManagerFactoryUtil.getBuildManager(flowRun);

		assertThat(buildManager).isInstanceOf(BuildFlowBuildManager.class);

		Iterator iterator = buildManager.downstreamProjects();

		StringBuilder expectedUrl = new StringBuilder();

		int count = 0;

		while (iterator.hasNext()) {
			Object downstreamBuild = iterator.next();

			assertThat(downstreamBuild).isInstanceOf(JobInvocation.class);

			JobInvocation jobInvocation = (JobInvocation)downstreamBuild;

			String jobInvocationBuildUrl = jobInvocation.getBuildUrl();

			expectedUrl.append("\n<a href='");
			expectedUrl.append(jobInvocationBuildUrl);
			expectedUrl.append("'>");
			expectedUrl.append(jobInvocationBuildUrl);
			expectedUrl.append("</a>");

			count++;
		}

		assertThat(count).isEqualTo(4);

		assertThat(buildManager.calculateBuildUrl()).isEqualTo(expectedUrl.toString());
	}

	private BuildFlow givenThatGhprbHasBeenTriggeredForABuildFlowProject()
		throws Exception {

		BuildFlow buildFlowProject = jenkinsRule.createBuildFlowProject();

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

		GhprbTrigger trigger = new GhprbTrigger("user", "user", "",
			"*/1 * * * *", "retest this please", false, false, false, false,
			false, null, null, false, null, null, null, false);

		given(commitPointer.getSha()).willReturn("sha");
		JSONObject jsonObject = GhprbTestUtil.provideConfiguration();

		GhprbTrigger.DESCRIPTOR.configure(null, jsonObject);

		buildFlowProject.addProperty(new GithubProjectProperty(
			"https://github.com/user/dropwizard"));

		given(ghPullRequest.getNumber()).willReturn(1);

		// Creating spy on ghprb, configuring repo
		Ghprb ghprb = spyCreatingGhprb(trigger, buildFlowProject);

		doReturn(ghprbGitHub).when(ghprb).getGitHub();

		setRepositoryHelper(ghprb);

		given(ghRepository.getPullRequest(1)).willReturn(ghPullRequest);

		// Configuring and adding Ghprb trigger
		buildFlowProject.addTrigger(trigger);

		buildFlowProject.getTriggers().keySet().iterator().next()
			.configure(null, jsonObject);

		// Configuring Git SCM
		buildFlowProject.setScm(GhprbTestUtil.provideGitSCM());

		trigger.start(buildFlowProject, true);

		setTriggerHelper(trigger, ghprb);

		Thread.sleep(130000);

		return buildFlowProject;
	}

}
