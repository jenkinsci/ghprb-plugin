package org.jenkinsci.plugins.ghprb;

import com.coravy.hudson.plugins.github.GithubProjectProperty;
import com.google.common.collect.Lists;

import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import net.sf.json.JSONObject;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueComment;
import org.mockito.runners.MockitoJUnitRunner;

import static com.google.common.collect.Lists.newArrayList;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Matchers.any;

@RunWith(MockitoJUnitRunner.class)
public class GhprbIT extends GhprbITBaseTestCase {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Before
    public void setUp() throws Exception {
        // GhprbTestUtil.mockGithubUserPage();
        super.beforeTest();
    }

    @Test
    public void shouldBuildTriggersOnNewPR() throws Exception {
        // GIVEN
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("PRJ");
        GhprbTrigger trigger = GhprbTestUtil.getTrigger(null);
        given(commitPointer.getSha()).willReturn("sha");
        JSONObject jsonObject = GhprbTestUtil.provideConfiguration();
        GhprbTrigger.getDscp().configure(null, jsonObject);
        project.addProperty(new GithubProjectProperty("https://github.com/user/dropwizard"));
        given(ghPullRequest.getNumber()).willReturn(1);

        // Creating spy on ghprb, configuring repo
        Ghprb ghprb = spy(trigger.createGhprb(project));
        doReturn(ghprbGitHub).when(ghprb).getGitHub();
        ghprb.getRepository().setHelper(ghprb);

        // Configuring and adding Ghprb trigger
        project.addTrigger(trigger);
        project.getTriggers().keySet().iterator().next().configure(null, jsonObject);

        // Configuring Git SCM
        GitSCM scm = GhprbTestUtil.provideGitSCM();
        project.setScm(scm);

        trigger.start(project, true);
        trigger.setHelper(ghprb);

        GhprbTestUtil.triggerRunAndWait(10, trigger, project);

        assertThat(project.getBuilds().toArray().length).isEqualTo(1);
    }

    @Test
    public void shouldBuildTriggersOnUpdatingNewCommitsPR() throws Exception {
        // GIVEN
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("PRJ");
        GhprbTrigger trigger = GhprbTestUtil.getTrigger(null);
        given(commitPointer.getSha()).willReturn("sha").willReturn("sha").willReturn("newOne").willReturn("newOne");
        given(ghPullRequest.getComments()).willReturn(Lists.<GHIssueComment> newArrayList());
        JSONObject jsonObject = GhprbTestUtil.provideConfiguration();
        GhprbTrigger.getDscp().configure(null, jsonObject);
        project.addProperty(new GithubProjectProperty("https://github.com/user/dropwizard"));
        given(ghPullRequest.getNumber()).willReturn(2).willReturn(2).willReturn(3).willReturn(3);
        Ghprb ghprb = spy(trigger.createGhprb(project));
        doReturn(ghprbGitHub).when(ghprb).getGitHub();
        trigger.start(project, true);
        trigger.setHelper(ghprb);
        ghprb.getRepository().setHelper(ghprb);
        project.addTrigger(trigger);
        project.getTriggers().keySet().iterator().next().configure(null, jsonObject);
        GitSCM scm = GhprbTestUtil.provideGitSCM();
        project.setScm(scm);

        GhprbTestUtil.triggerRunAndWait(10, trigger, project);

        assertThat(project.getBuilds().toArray().length).isEqualTo(2);
    }

    @Test
    public void shouldBuildTriggersOnUpdatingRetestMessagePR() throws Exception {
        // GIVEN
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("PRJ");
        GhprbTrigger trigger = GhprbTestUtil.getTrigger(null);

        given(commitPointer.getSha()).willReturn("sha");

        GHIssueComment comment = mock(GHIssueComment.class);
        given(comment.getBody()).willReturn("retest this please");
        given(comment.getUpdatedAt()).willReturn(new DateTime().plusDays(1).toDate());
        given(comment.getUser()).willReturn(ghUser);
        given(ghPullRequest.getComments()).willReturn(newArrayList(comment));
        given(ghPullRequest.getNumber()).willReturn(5).willReturn(5);
        JSONObject jsonObject = GhprbTestUtil.provideConfiguration();
        GhprbTrigger.getDscp().configure(null, jsonObject);
        project.addProperty(new GithubProjectProperty("https://github.com/user/dropwizard"));

        Ghprb ghprb = spy(trigger.createGhprb(project));
        doReturn(ghprbGitHub).when(ghprb).getGitHub();
        trigger.start(project, true);
        trigger.setHelper(ghprb);
        ghprb.getRepository().setHelper(ghprb);
        project.addTrigger(trigger);
        project.getTriggers().keySet().iterator().next().configure(null, jsonObject);
        GitSCM scm = GhprbTestUtil.provideGitSCM();
        project.setScm(scm);

        GhprbTestUtil.triggerRunAndWait(10, trigger, project);
        assertThat(project.getBuilds().toArray().length).isEqualTo(2);
    }
    

    @Test
    public void shouldNotBuildDisabledBuild() throws Exception {
        // GIVEN
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("PRJ");
        GhprbTrigger trigger = GhprbTestUtil.getTrigger(null);

        given(commitPointer.getSha()).willReturn("sha");

        GHIssueComment comment = mock(GHIssueComment.class);
        given(comment.getBody()).willReturn("retest this please");
        given(comment.getUpdatedAt()).willReturn(new DateTime().plusDays(1).toDate());
        given(comment.getUser()).willReturn(ghUser);
        given(ghPullRequest.getComments()).willReturn(newArrayList(comment));
        given(ghPullRequest.getNumber()).willReturn(5);
        JSONObject jsonObject = GhprbTestUtil.provideConfiguration();
        GhprbTrigger.getDscp().configure(null, jsonObject);
        project.addProperty(new GithubProjectProperty("https://github.com/user/dropwizard"));

        Ghprb ghprb = spy(trigger.createGhprb(project));
        doReturn(ghprbGitHub).when(ghprb).getGitHub();
        trigger.start(project, true);
        trigger.setHelper(ghprb);
        ghprb.getRepository().setHelper(ghprb);
        project.addTrigger(trigger);
        project.getTriggers().keySet().iterator().next().configure(null, jsonObject);
        GitSCM scm = GhprbTestUtil.provideGitSCM();
        project.setScm(scm);
        
        project.disable();

        GhprbTestUtil.triggerRunAndWait(10, trigger, project);
        assertThat(project.getBuilds().toArray().length).isEqualTo(0);
        
        verify(ghRepository, times(0)).createCommitStatus(any(String.class), any(GHCommitState.class), any(String.class), any(String.class));
    }

}
