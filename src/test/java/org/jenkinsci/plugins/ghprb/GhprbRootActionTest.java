package org.jenkinsci.plugins.ghprb;

import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URLEncoder;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.coravy.hudson.plugins.github.GithubProjectProperty;

import static com.google.common.collect.Lists.newArrayList;
import static org.fest.assertions.Assertions.assertThat;
import static org.kohsuke.github.GHIssueState.OPEN;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class GhprbRootActionTest {
    

    @Mock
    protected GHCommitPointer commitPointer;
    @Mock
    protected GHPullRequest ghPullRequest;
    @Mock
    protected GhprbGitHub ghprbGitHub;
    @Mock
    protected GHRepository ghRepository;
    @Mock
    protected GHUser ghUser;
    
    protected GitHub gitHub;
    // Stubs
    protected GHRateLimit ghRateLimit = new GHRateLimit();


    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Mock
    private StaplerRequest req;

    private BufferedReader br;

    @Before
    public void setup() throws Exception {
        gitHub = spy(GitHub.connectAnonymously());
        given(ghprbGitHub.get()).willReturn(gitHub);
        given(gitHub.getRateLimit()).willReturn(ghRateLimit);
        doReturn(ghRepository).when(gitHub).getRepository(anyString());
        given(commitPointer.getRef()).willReturn("ref");
        given(ghRepository.getName()).willReturn("dropwizard");

        GhprbTestUtil.mockPR(ghPullRequest, commitPointer, new DateTime(), new DateTime().plusDays(1));

        given(ghRepository.getPullRequests(eq(OPEN))).willReturn(newArrayList(ghPullRequest)).willReturn(newArrayList(ghPullRequest));

        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghUser.getEmail()).willReturn("email@email.com");
        given(ghUser.getLogin()).willReturn("user");

        ghRateLimit.remaining = GhprbTestUtil.INITIAL_RATE_LIMIT;

        GhprbTestUtil.mockCommitList(ghPullRequest);
    }

    @Test
    public void testUrlEncoded() throws Exception {
        // GIVEN
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("testUrlEncoded");
        GhprbTrigger trigger = spy(GhprbTestUtil.getTrigger(null));
        given(commitPointer.getSha()).willReturn("sha1");
        GhprbTestUtil.setupGhprbTriggerDescriptor(null);
        project.addProperty(new GithubProjectProperty("https://github.com/user/dropwizard"));
        given(ghPullRequest.getNumber()).willReturn(1);
        Ghprb ghprb = spy(trigger.createGhprb(project));
        doReturn(ghprbGitHub).when(ghprb).getGitHub();
        trigger.start(project, true);
        trigger.setHelper(ghprb);
        ghprb.getRepository().setHelper(ghprb);
        project.addTrigger(trigger);
        GitSCM scm = GhprbTestUtil.provideGitSCM();
        project.setScm(scm);

        GhprbTestUtil.triggerRunAndWait(10, trigger, project);

        assertThat(project.getBuilds().toArray().length).isEqualTo(1);

		doReturn(gitHub).when(trigger).getGitHub();

        BufferedReader br = new BufferedReader(new StringReader(
                "payload=" + URLEncoder.encode(GhprbTestUtil.PAYLOAD, "UTF-8")));

        given(req.getContentType()).willReturn("application/x-www-form-urlencoded");
        given(req.getParameter("payload")).willReturn(GhprbTestUtil.PAYLOAD);
        given(req.getHeader("X-GitHub-Event")).willReturn("issue_comment");
        given(req.getReader()).willReturn(br);
        given(req.getCharacterEncoding()).willReturn("UTF-8");

        GhprbRootAction ra = new GhprbRootAction();
        ra.doIndex(req, null);
        GhprbTestUtil.waitForBuildsToFinish(project);

        assertThat(project.getBuilds().toArray().length).isEqualTo(2);
    }
    
    @Test
    public void disabledJobsDontBuild() throws Exception {
        // GIVEN
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("disabledJobsDontBuild");
        GhprbTrigger trigger = spy(GhprbTestUtil.getTrigger(null));
        given(commitPointer.getSha()).willReturn("sha1");
        GhprbTestUtil.setupGhprbTriggerDescriptor(null);
        project.addProperty(new GithubProjectProperty("https://github.com/user/dropwizard"));
        given(ghPullRequest.getNumber()).willReturn(1);
        Ghprb ghprb = spy(trigger.createGhprb(project));
        doReturn(ghprbGitHub).when(ghprb).getGitHub();
        trigger.start(project, true);
        trigger.setHelper(ghprb);
        ghprb.getRepository().setHelper(ghprb);
        project.addTrigger(trigger);
        GitSCM scm = GhprbTestUtil.provideGitSCM();
        project.setScm(scm);

        GhprbTestUtil.triggerRunAndWait(10, trigger, project);

        assertThat(project.getBuilds().toArray().length).isEqualTo(1);
        
        project.disable();

		doReturn(gitHub).when(trigger).getGitHub();

        BufferedReader br = new BufferedReader(new StringReader(
                "payload=" + URLEncoder.encode(GhprbTestUtil.PAYLOAD, "UTF-8")));

        given(req.getContentType()).willReturn("application/x-www-form-urlencoded");
        given(req.getParameter("payload")).willReturn(GhprbTestUtil.PAYLOAD);
        given(req.getHeader("X-GitHub-Event")).willReturn("issue_comment");
        given(req.getReader()).willReturn(br);
        given(req.getCharacterEncoding()).willReturn("UTF-8");

        GhprbRootAction ra = new GhprbRootAction();
        ra.doIndex(req, null);
        GhprbTestUtil.waitForBuildsToFinish(project);
        
        assertThat(project.getBuilds().toArray().length).isEqualTo(1);
    }

    @Test
    public void testJson() throws Exception {
        given(req.getContentType()).willReturn("application/json");
        given(req.getHeader("X-GitHub-Event")).willReturn("issue_comment");

        // convert String into InputStream
        InputStream is = new ByteArrayInputStream(GhprbTestUtil.PAYLOAD.getBytes());
        // read it with BufferedReader
        br = spy(new BufferedReader(new InputStreamReader(is)));

        given(req.getReader()).willReturn(br);

        GhprbRootAction ra = new GhprbRootAction();
        ra.doIndex(req, null);

        verify(br, times(1)).close();
    }

}
