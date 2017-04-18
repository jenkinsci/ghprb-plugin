package org.jenkinsci.plugins.ghprb;

import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URLEncoder;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHEventPayload.IssueComment;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mock;
import org.mockito.Mockito;
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
    

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Mock
    private StaplerRequest req;

    private BufferedReader br;
    
    private GhprbTrigger trigger;
    

    private final int prId = 1;

    @Before
    public void setup() throws Exception {
        trigger = GhprbTestUtil.getTrigger();
        GitHub gitHub = trigger.getGitHub();
        
        given(gitHub.getRepository(anyString())).willReturn(ghRepository);
        given(commitPointer.getRef()).willReturn("ref");
        given(ghRepository.getName()).willReturn("dropwizard");

        GhprbTestUtil.mockPR(ghPullRequest, commitPointer, new DateTime(), new DateTime().plusDays(1));

        given(ghRepository.getPullRequests(eq(OPEN))).willReturn(newArrayList(ghPullRequest)).willReturn(newArrayList(ghPullRequest));

        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghUser.getEmail()).willReturn("email@email.com");
        given(ghUser.getLogin()).willReturn("user");
        given(ghUser.getName()).willReturn("User");


        GhprbTestUtil.mockCommitList(ghPullRequest);
    }

    @Test
    public void testUrlEncoded() throws Exception {
        // GIVEN
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("testUrlEncoded");
        
        doReturn(project).when(trigger).getActualProject();
        doReturn(true).when(trigger).getUseGitHubHooks();
        
        given(commitPointer.getSha()).willReturn("sha1");
        GhprbTestUtil.setupGhprbTriggerDescriptor(null);
        project.addProperty(new GithubProjectProperty("https://github.com/user/dropwizard"));
        given(ghPullRequest.getId()).willReturn(prId);
        given(ghPullRequest.getNumber()).willReturn(prId);
        given(ghRepository.getPullRequest(prId)).willReturn(ghPullRequest);
        Ghprb ghprb = spy(new Ghprb(trigger));
        doReturn(ghprbGitHub).when(ghprb).getGitHub();
        doReturn(true).when(ghprb).isAdmin(Mockito.any(GHUser.class));
        
        trigger.start(project, true);
        trigger.setHelper(ghprb);
        
        project.addTrigger(trigger);
        GitSCM scm = GhprbTestUtil.provideGitSCM();
        project.setScm(scm);

        GhprbTestUtil.triggerRunAndWait(10, trigger, project);

        assertThat(project.getBuilds().toArray().length).isEqualTo(0);

        BufferedReader br = new BufferedReader(new StringReader(
                "payload=" + URLEncoder.encode(GhprbTestUtil.PAYLOAD, "UTF-8")));
        


        given(req.getContentType()).willReturn("application/x-www-form-urlencoded");
        given(req.getParameter("payload")).willReturn(GhprbTestUtil.PAYLOAD);
        given(req.getHeader("X-GitHub-Event")).willReturn("issue_comment");
        given(req.getReader()).willReturn(br);
        given(req.getCharacterEncoding()).willReturn("UTF-8");


        StringReader brTest = new StringReader(GhprbTestUtil.PAYLOAD);
        
        IssueComment issueComment = spy(GitHub.connectAnonymously().parseEventPayload(brTest, IssueComment.class));
        brTest.close();
        
        GHIssueComment ghIssueComment = spy(issueComment.getComment());
        
        Mockito.when(issueComment.getComment()).thenReturn(ghIssueComment);
        Mockito.doReturn(ghUser).when(ghIssueComment).getUser();
        
        
        given(trigger.getGitHub().parseEventPayload(Mockito.any(Reader.class), Mockito.eq(IssueComment.class))).willReturn(issueComment);

        GhprbRootAction ra = new GhprbRootAction();
        ra.doIndex(req, null);
        // handles race condition around starting and finishing builds. Give the system time
        // to finish indexing, create a build, queue it, and run it.
        int count = 0;
        while (count < 5 && project.getBuilds().toArray().length == 0) {
            GhprbTestUtil.waitForBuildsToFinish(project);
            Thread.sleep(50);
            count = count + 1;
        }

        assertThat(project.getBuilds().toArray().length).isEqualTo(1);
    }
    
    @Test
    public void disabledJobsDontBuild() throws Exception {
        // GIVEN
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("disabledJobsDontBuild");
        doReturn(project).when(trigger).getActualProject();
        
        given(commitPointer.getSha()).willReturn("sha1");
        GhprbTestUtil.setupGhprbTriggerDescriptor(null);
        project.addProperty(new GithubProjectProperty("https://github.com/user/dropwizard"));
        given(ghPullRequest.getId()).willReturn(prId);
        given(ghPullRequest.getNumber()).willReturn(prId);
        given(ghRepository.getPullRequest(prId)).willReturn(ghPullRequest);
        Ghprb ghprb = spy(new Ghprb(trigger));
        doReturn(ghprbGitHub).when(ghprb).getGitHub();
        trigger.start(project, true);
        trigger.setHelper(ghprb);

        project.addTrigger(trigger);
        GitSCM scm = GhprbTestUtil.provideGitSCM();
        project.setScm(scm);

        GhprbTestUtil.triggerRunAndWait(10, trigger, project);

        assertThat(project.getBuilds().toArray().length).isEqualTo(1);
        
        project.disable();

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
