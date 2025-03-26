package org.jenkinsci.plugins.ghprb;

import com.coravy.hudson.plugins.github.GithubProjectProperty;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import hudson.util.Secret;
import org.apache.commons.codec.binary.Hex;
import org.fest.util.Collections;
import org.jenkinsci.plugins.ghprb.extensions.status.GhprbSimpleStatus;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Collections.singletonList;
import static org.kohsuke.github.GHCommitState.PENDING;
import static org.kohsuke.github.GHIssueState.OPEN;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Unit tests for {@link GhprbRepository}.
 */
@RunWith(MockitoJUnitRunner.class)
public class GhprbRepositoryTest {

    private static final String TEST_REPO_NAME = "test-user/test-repo";

    private static final Date UPDATE_DATE = new Date();

    private static final String MSG = "Build triggered for merge commit.";

    @Mock
    private GHRepository ghRepository;

    @Mock
    private GhprbGitHub gitHub;

    @Mock
    private Ghprb helper;

    @Mock
    private GHPullRequest ghPullRequest;

    @Mock
    private GHCommitPointer base;

    @Mock
    private GHCommitPointer head;

    @Mock
    private GHUser ghUser;

    private GitHub gt;

    private GhprbTrigger trigger;

    private GhprbRepository ghprbRepository;

    private ConcurrentMap<Integer, GhprbPullRequest> pulls;

    private GhprbPullRequest ghprbPullRequest;

    private Job<?, ?> project;

    private GHRateLimit rateLimit;

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Before
    public void setUp() throws Exception {
        project = jenkinsRule.createFreeStyleProject("GhprbRepoTest");
        project.addProperty(new GithubProjectProperty("https://github.com/" + TEST_REPO_NAME));

        getNewTrigger();
        startTrigger();

        pulls = new ConcurrentHashMap<>();


        doReturn(mock(QueueTaskFuture.class)).when(trigger).scheduleBuild(any(GhprbCause.class), any(GhprbRepository.class));
        initGHPRWithTestData();

        given(ghPullRequest.getUser()).willReturn(ghUser);

        // Mock github API
        given(helper.getGitHub()).willReturn(gitHub);
        given(helper.getTrigger()).willReturn(trigger);

        // Mock rate limit
        addSimpleStatus();
    }

    private void getNewTrigger() throws Exception {
        trigger = GhprbTestUtil.getTrigger(null);

        gt = trigger.getGitHub();

        rateLimit = gt.getRateLimit();
        verify(gt).getRateLimit();

        given(gt.getRepository(anyString())).willReturn(ghRepository);

    }

    private void startTrigger() throws IOException {

        trigger.start(project, true);
        trigger.setHelper(helper);
    }

    private void addSimpleStatus() {
        GhprbSimpleStatus status = new GhprbSimpleStatus("default");
        try {
            trigger.getExtensions().remove(GhprbSimpleStatus.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        trigger.getExtensions().add(status);
    }

    @Test
    public void testCheckMethodWhenUsingGitHubEnterprise() throws Exception {
        // GIVEN
        given(gt.getRateLimit()).willThrow(new FileNotFoundException());
        List<GHPullRequest> ghPullRequests = createListWithMockPR();
        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);
        given(ghRepository.getPullRequest((int) ghPullRequest.getId())).willReturn(ghPullRequest);

        mockHeadAndBase();
        mockCommitList();

        given(helper.ifOnlyTriggerPhrase()).willReturn(true);

        pulls.put(1, ghprbPullRequest);

        given(ghPullRequest.getUpdatedAt()).willReturn(UPDATE_DATE);
        given(ghPullRequest.getNumber()).willReturn(1);

        // WHEN
        ghprbRepository.check();

        // THEN
        verifyGetGithub(2, 2, 0);
    }

    @Test
    public void testCheckMethodWithOnlyExistingPRs() throws Exception {
        // GIVEN
        List<GHPullRequest> ghPullRequests = createListWithMockPR();
        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);
        given(ghRepository.getPullRequest(Mockito.anyInt())).willReturn(ghPullRequest);

        doReturn(ghRepository).when(ghprbRepository).getGitHubRepo();
        mockHeadAndBase();
        mockCommitList();

        given(helper.ifOnlyTriggerPhrase()).willReturn(true);

        pulls.put(1, ghprbPullRequest);
        ghprbRepository.addPullRequests(pulls);

        given(ghPullRequest.getUpdatedAt()).willReturn(UPDATE_DATE);
        given(ghPullRequest.getNumber()).willReturn(1);

        // WHEN
        ghprbRepository.check();

        // THEN
        verifyGetGithub(2, 2, 1);

        /* GH Repo verifications */
        verify(ghRepository, only()).getPullRequests(OPEN); // Call to Github API
        verifyNoMoreInteractions(ghRepository);

        /* GH PR verifications */
        verify(ghPullRequest, times(2)).getHead();
        verify(ghPullRequest, times(2)).getNumber();
        verify(ghPullRequest, times(1)).getUpdatedAt();
        verify(ghPullRequest, times(1)).getUser();
        verify(ghPullRequest, times(2)).getBase();
        verify(ghPullRequest, times(1)).getComments();
        verify(ghPullRequest, times(1)).listCommits();
        verifyNoMoreInteractions(ghPullRequest);

        verify(helper).ifOnlyTriggerPhrase();
        verify(helper).getWhiteListTargetBranches();
        verify(helper).getBlackListTargetBranches();
        verify(helper, times(2)).isProjectDisabled();
        verify(helper).checkSkipBuildPhrase(eq(ghPullRequest));
        verify(helper, times(1)).getBlackListLabels();
        verify(helper, times(1)).getWhiteListLabels();
        verifyNoMoreInteractions(helper);
        verifyNoMoreInteractions(gt);

        verify(ghUser, times(1)).getName();
        verify(ghUser, times(1)).getLogin();
        verifyNoMoreInteractions(ghUser);
        verifyZeroInteractions(ghUser);
    }

    @Test
    public void testCheckMethodWithNewPR() throws Exception {
        // GIVEN
        List<GHPullRequest> ghPullRequests = createListWithMockPR();
        ghPullRequests.add(ghPullRequest);

        GhprbBuilds builds = mockBuilds();

        mockHeadAndBase();
        mockCommitList();

        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);

        given(ghPullRequest.getUpdatedAt()).willReturn(UPDATE_DATE);
        given(ghPullRequest.getNumber()).willReturn(100);
        given(ghPullRequest.getMergeable()).willReturn(true);
        given(ghPullRequest.getTitle()).willReturn("title");
        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghPullRequest.getHtmlUrl()).willReturn(new URL("https://github.com/org/repo/pull/100"));
        given(ghPullRequest.getApiURL()).willReturn(new URL("https://github.com/org/repo/pull/100"));
        given(ghPullRequest.getId()).willReturn(100L);
        given(ghRepository.getPullRequest((int) ghPullRequest.getId())).willReturn(ghPullRequest);

        given(ghUser.getEmail()).willReturn("email");

        given(helper.ifOnlyTriggerPhrase()).willReturn(false);
        given(helper.isWhitelisted(ghUser)).willReturn(true);
        given(helper.getTrigger()).willReturn(trigger);
        given(helper.getBlackListLabels()).willReturn(Collections.set("bug", "help wanted"));
        given(helper.getWhiteListLabels()).willReturn(null);

        // WHEN
        ghprbRepository.check();

        // THEN
        verifyGetGithub(2, 2, 1);
        verifyNoMoreInteractions(gt);

        /* GH PR verifications */
        verify(builds, times(1)).build(any(GhprbPullRequest.class), any(GHUser.class), any(String.class));
        verify(ghRepository, times(1)).getPullRequests(OPEN); // Call to Github API
        verify(ghRepository, times(1))
                .createCommitStatus(eq("head sha"), eq(PENDING), eq(""), eq(MSG), eq("default")); // Call to Github API
        verify(ghRepository, times(1)).getPullRequest(Mockito.anyInt());
        verifyNoMoreInteractions(ghRepository);

        verify(ghPullRequest, times(1)).getTitle();
        verify(ghPullRequest, times(3)).getUser();
        verify(ghPullRequest, times(1)).getMergeable(); // Call to Github API
        verify(ghPullRequest, times(7)).getHead();
        verify(ghPullRequest, times(6)).getBase();
        verify(ghPullRequest, times(5)).getNumber();
        verify(ghPullRequest, times(2)).getUpdatedAt();
        verify(ghPullRequest, times(1)).getCreatedAt();
        verify(ghPullRequest, times(1)).getHtmlUrl();
        verify(ghPullRequest, times(3)).listCommits();
        verify(ghPullRequest, times(1)).getBody();
        verify(ghPullRequest, times(1)).getId();
        verify(ghPullRequest, times(2)).getLabels();
        verifyNoMoreInteractions(ghPullRequest);

        verify(helper, times(1)).isWhitelisted(eq(ghUser)); // Call to Github API
        verify(helper, times(2)).ifOnlyTriggerPhrase();
        verify(helper, times(1)).getBuilds();
        verify(helper, times(2)).getWhiteListTargetBranches();
        verify(helper, times(2)).getBlackListTargetBranches();
        verify(helper, times(4)).isProjectDisabled();
        verify(helper, times(2)).checkSkipBuildPhrase(eq(ghPullRequest));
        verify(helper, times(2)).getBlackListLabels();
        verify(helper, times(2)).getWhiteListLabels();
        verify(helper).getIncludedRegionPatterns();
        verify(helper).getExcludedRegionPatterns();
        verifyNoMoreInteractions(helper);

        verify(ghUser, times(1)).getEmail(); // Call to Github API
        verify(ghUser, times(1)).getLogin();
        verifyNoMoreInteractions(ghUser);
    }

    @Test
    public void testShouldSkipBuildIfLabelInBlackListWithNewPR() throws Exception {
        // GIVEN
        GHLabel label1 = mock(GHLabel.class);
        given(label1.getName()).willReturn("in progress");
        GHLabel label2 = mock(GHLabel.class);
        given(label2.getName()).willReturn("testing");
        List<GHLabel> ghLabels = Arrays.asList(label1, label2);

        List<GHPullRequest> ghPullRequests = createListWithMockPR();
        ghPullRequests.add(ghPullRequest);

        GhprbBuilds builds = mockBuilds();

        mockHeadAndBase();
        mockCommitList();

        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);

        given(ghPullRequest.getUpdatedAt()).willReturn(UPDATE_DATE);
        given(ghPullRequest.getNumber()).willReturn(100);
        given(ghPullRequest.getMergeable()).willReturn(true);
        given(ghPullRequest.getTitle()).willReturn("title");
        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghPullRequest.getHtmlUrl()).willReturn(new URL("https://github.com/org/repo/pull/100"));
        given(ghPullRequest.getApiURL()).willReturn(new URL("https://github.com/org/repo/pull/100"));
        given(ghPullRequest.getId()).willReturn(100L);
        given(ghPullRequest.getLabels()).willReturn(ghLabels);
        given(ghRepository.getPullRequest((int) ghPullRequest.getId())).willReturn(ghPullRequest);

        given(ghUser.getEmail()).willReturn("email");

        given(helper.ifOnlyTriggerPhrase()).willReturn(false);
        given(helper.isWhitelisted(ghUser)).willReturn(true);
        given(helper.getTrigger()).willReturn(trigger);
        given(helper.getBlackListLabels()).willReturn(Collections.set("in progress"));
        given(helper.getWhiteListLabels()).willReturn(null);

        // WHEN
        ghprbRepository.check();

        // THEN
        verifyGetGithub(2, 2, 1);
        verifyNoMoreInteractions(gt);

        /* Verify no attempt was made to start a build */
        verifyNoMoreInteractions(builds);
    }

    @Test
    public void testShouldSkipBuildIfWhiteListLabelIsSetAndLabelNotInWhiteListWithNewPR() throws Exception {
        // GIVEN
        GHLabel label1 = mock(GHLabel.class);
        given(label1.getName()).willReturn("Whitelist Label Not Included");
        List<GHLabel> ghLabels = singletonList(label1);

        List<GHPullRequest> ghPullRequests = createListWithMockPR();
        ghPullRequest.setLabels("Whitelist Label Not Included");
        ghPullRequests.add(ghPullRequest);

        GhprbBuilds builds = mockBuilds();

        mockHeadAndBase();
        mockCommitList();

        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);

        given(ghPullRequest.getUpdatedAt()).willReturn(UPDATE_DATE);
        given(ghPullRequest.getNumber()).willReturn(100);
        given(ghPullRequest.getMergeable()).willReturn(true);
        given(ghPullRequest.getTitle()).willReturn("title");
        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghPullRequest.getHtmlUrl()).willReturn(new URL("https://github.com/org/repo/pull/100"));
        given(ghPullRequest.getApiURL()).willReturn(new URL("https://github.com/org/repo/pull/100"));
        given(ghPullRequest.getId()).willReturn(100L);
        given(ghPullRequest.getLabels()).willReturn(ghLabels);
        given(ghRepository.getPullRequest((int) ghPullRequest.getId())).willReturn(ghPullRequest);

        given(ghUser.getEmail()).willReturn("email");

        given(helper.ifOnlyTriggerPhrase()).willReturn(false);
        given(helper.isWhitelisted(ghUser)).willReturn(true);
        given(helper.getTrigger()).willReturn(trigger);
        given(helper.getBlackListLabels()).willReturn(null);
        given(helper.getWhiteListLabels()).willReturn(Collections.set("Whitelist Label"));

        // WHEN
        ghprbRepository.check();

        // THEN
        verifyGetGithub(2, 2, 1);
        verifyNoMoreInteractions(gt);

        /* Verify no attempt was made to start a build */
        verifyNoMoreInteractions(builds);
    }

    @Test
    public void testCheckBuildWithBlackWhiteLabelsSet() throws Exception {
        // GIVEN
        GHLabel label1 = mock(GHLabel.class);
        given(label1.getName()).willReturn("Whitelist Label");
        List<GHLabel> ghLabels = singletonList(label1);

        List<GHPullRequest> ghPullRequests = createListWithMockPR();
        ghPullRequests.add(ghPullRequest);

        GhprbBuilds builds = mockBuilds();

        mockHeadAndBase();
        mockCommitList();

        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);

        given(ghPullRequest.getUpdatedAt()).willReturn(UPDATE_DATE);
        given(ghPullRequest.getNumber()).willReturn(100);
        given(ghPullRequest.getMergeable()).willReturn(true);
        given(ghPullRequest.getTitle()).willReturn("title");
        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghPullRequest.getHtmlUrl()).willReturn(new URL("https://github.com/org/repo/pull/100"));
        given(ghPullRequest.getApiURL()).willReturn(new URL("https://github.com/org/repo/pull/100"));
        given(ghPullRequest.getId()).willReturn(100L);
        given(ghPullRequest.getLabels()).willReturn(ghLabels);
        given(ghRepository.getPullRequest((int) ghPullRequest.getId())).willReturn(ghPullRequest);

        given(ghUser.getEmail()).willReturn("email");

        given(helper.ifOnlyTriggerPhrase()).willReturn(false);
        given(helper.isWhitelisted(ghUser)).willReturn(true);
        given(helper.getTrigger()).willReturn(trigger);
        given(helper.getBlackListLabels()).willReturn(Collections.set("bug", "help wanted"));
        given(helper.getWhiteListLabels()).willReturn(Collections.set("Whitelist Label"));

        // WHEN
        ghprbRepository.check();

        // THEN
        verifyGetGithub(2, 2, 1);
        verifyNoMoreInteractions(gt);

        /* GH PR verifications */
        verify(builds, times(1)).build(any(GhprbPullRequest.class), any(GHUser.class), any(String.class));
        verify(ghRepository, times(1)).getPullRequests(OPEN); // Call to Github API
        verify(ghRepository, times(1))
                .createCommitStatus(eq("head sha"), eq(PENDING), eq(""), eq(MSG), eq("default")); // Call to Github API
        verify(ghRepository, times(1)).getPullRequest(Mockito.anyInt());
        verifyNoMoreInteractions(ghRepository);

        verify(ghPullRequest, times(1)).getTitle();
        verify(ghPullRequest, times(3)).getUser();
        verify(ghPullRequest, times(1)).getMergeable(); // Call to Github API
        verify(ghPullRequest, times(7)).getHead();
        verify(ghPullRequest, times(6)).getBase();
        verify(ghPullRequest, times(5)).getNumber();
        verify(ghPullRequest, times(2)).getUpdatedAt();
        verify(ghPullRequest, times(1)).getCreatedAt();
        verify(ghPullRequest, times(1)).getHtmlUrl();
        verify(ghPullRequest, times(3)).listCommits();
        verify(ghPullRequest, times(1)).getBody();
        verify(ghPullRequest, times(1)).getId();
        verify(ghPullRequest, times(4)).getLabels();
        verifyNoMoreInteractions(ghPullRequest);

        verify(helper, times(1)).isWhitelisted(eq(ghUser)); // Call to Github API
        verify(helper, times(2)).ifOnlyTriggerPhrase();
        verify(helper, times(1)).getBuilds();
        verify(helper, times(2)).getWhiteListTargetBranches();
        verify(helper, times(2)).getBlackListTargetBranches();
        verify(helper, times(4)).isProjectDisabled();
        verify(helper, times(2)).checkSkipBuildPhrase(eq(ghPullRequest));
        verify(helper, times(2)).getBlackListLabels();
        verify(helper, times(2)).getWhiteListLabels();
        verify(helper).getIncludedRegionPatterns();
        verify(helper).getExcludedRegionPatterns();
        verifyNoMoreInteractions(helper);

        verify(ghUser, times(1)).getEmail(); // Call to Github API
        verify(ghUser, times(1)).getLogin();
        verifyNoMoreInteractions(ghUser);
    }

    private GhprbBuilds mockBuilds() throws IOException {
        GhprbBuilds builds = spy(new GhprbBuilds(trigger, ghprbRepository));
        given(helper.getBuilds()).willReturn(builds);
        given(ghRepository.createCommitStatus(anyString(), any(GHCommitState.class), anyString(), anyString())).willReturn(null);
        return builds;
    }

    @Test
    public void testCheckMethodWhenPrWasUpdatedWithNonKeyPhrase() throws Exception {
        // GIVEN
        List<GHPullRequest> ghPullRequests = createListWithMockPR();

        mockHeadAndBase();
        mockCommitList();
        GhprbBuilds builds = mockBuilds();

        Date later = new DateTime().plusHours(3).toDate();
        Date tomorrow = new DateTime().plusDays(1).toDate();


        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);

        given(ghPullRequest.getUpdatedAt()).willReturn(later).willReturn(tomorrow);
        given(ghPullRequest.getNumber()).willReturn(100);
        given(ghPullRequest.getMergeable()).willReturn(true);
        given(ghPullRequest.getTitle()).willReturn("title");
        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghPullRequest.getHtmlUrl()).willReturn(new URL("https://github.com/org/repo/pull/100"));
        given(ghPullRequest.getApiURL()).willReturn(new URL("https://github.com/org/repo/pull/100"));
        given(ghPullRequest.getId()).willReturn(100L);
        given(ghRepository.getPullRequest((int) ghPullRequest.getId())).willReturn(ghPullRequest);

        given(ghUser.getEmail()).willReturn("email");
        given(ghUser.getLogin()).willReturn("login");

        given(helper.ifOnlyTriggerPhrase()).willReturn(false);
        given(helper.isWhitelisted(ghUser)).willReturn(true);
        given(helper.getTrigger()).willReturn(trigger);
        given(helper.getBlackListLabels()).willReturn(Collections.set("bug", "help wanted"));
        given(helper.getWhiteListLabels()).willReturn(null);

        // WHEN
        ghprbRepository.check(); // PR was created

        mockComments("comment body", tomorrow);
        ghprbRepository.check(); // PR was updated

        // THEN
        verifyGetGithub(2, 2, 1);
        verifyNoMoreInteractions(gt);

        /* GH PR verifications */
        verify(builds, times(1)).build(any(GhprbPullRequest.class), any(GHUser.class), any(String.class));
        verify(ghRepository, times(2)).getPullRequests(eq(OPEN)); // Call to Github API
        verify(ghRepository, times(1))
                .createCommitStatus(eq("head sha"), eq(PENDING), eq(""), eq(MSG), eq("default"));
        verify(ghRepository, times(1)).getPullRequest(Mockito.anyInt());
        verifyNoMoreInteractions(ghRepository);

        verify(ghPullRequest, times(1)).getTitle();
        verify(ghPullRequest, times(5)).getUser();
        verify(ghPullRequest, times(1)).getMergeable(); // Call to Github API
        verify(ghPullRequest, times(7)).getHead();
        verify(ghPullRequest, times(6)).getBase();
        verify(ghPullRequest, times(5)).getNumber();
        verify(ghPullRequest, times(1)).getHtmlUrl();
        verify(ghPullRequest, times(2)).getUpdatedAt();
        verify(ghPullRequest, times(1)).getCreatedAt();

        verify(ghPullRequest, times(2)).getComments();
        verify(ghPullRequest, times(3)).listCommits();
        verify(ghPullRequest, times(1)).getBody();
        verify(ghPullRequest, times(1)).getId();
        verify(ghPullRequest, times(2)).getLabels();
        verifyNoMoreInteractions(ghPullRequest);

        verify(helper, times(1)).isWhitelisted(eq(ghUser)); // Call to Github API
        verify(helper, times(2)).ifOnlyTriggerPhrase();
        verify(helper, times(1)).getBuilds();
        verify(helper, times(2)).getWhiteListTargetBranches();
        verify(helper, times(2)).getBlackListTargetBranches();
        verify(helper, times(2)).getBlackListLabels();
        verify(helper, times(2)).getWhiteListLabels();

        // verify(helper).isBotUser(eq(ghUser));
        verify(helper).isWhitelistPhrase(eq("comment body"));
        verify(helper).isOktotestPhrase(eq("comment body"));
        verify(helper).isRetestPhrase(eq("comment body"));
        verify(helper).isTriggerPhrase(eq("comment body"));
        verify(helper, times(4)).isProjectDisabled();
        verify(helper, times(2)).checkSkipBuildPhrase(eq(ghPullRequest));
        verify(helper).getIncludedRegionPatterns();
        verify(helper).getExcludedRegionPatterns();
        verifyNoMoreInteractions(helper);

        verify(ghUser, times(1)).getEmail(); // Call to Github API
        verify(ghUser, times(4)).getLogin();
        verify(ghUser, times(3)).getName();
        verifyNoMoreInteractions(ghUser);
    }

    private List<GHPullRequest> createListWithMockPR() throws IOException {

        given(ghPullRequest.getCreatedAt()).willReturn(new Date());
        List<GHPullRequest> ghPullRequests = new ArrayList<>();
        ghPullRequests.add(ghPullRequest);
        return ghPullRequests;
    }

    @Test
    public void testCheckMethodWhenPrWasUpdatedWithRetestPhrase() throws Exception {
        // GIVEN
        List<GHPullRequest> ghPullRequests = createListWithMockPR();
        Date now = new Date();
        Date tomorrow = new DateTime().plusDays(1).toDate();

        mockHeadAndBase();
        mockCommitList();
        GhprbBuilds builds = mockBuilds();

        given(ghPullRequest.getUpdatedAt()).willReturn(now).willReturn(tomorrow);
        given(ghPullRequest.getNumber()).willReturn(100);
        given(ghPullRequest.getMergeable()).willReturn(true);
        given(ghPullRequest.getTitle()).willReturn("title");
        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghPullRequest.getHtmlUrl()).willReturn(new URL("https://github.com/org/repo/pull/100"));
        given(ghPullRequest.getApiURL()).willReturn(new URL("https://github.com/org/repo/pull/100"));
        given(ghPullRequest.getId()).willReturn(100L);
        given(ghRepository.getPullRequest((int) ghPullRequest.getId())).willReturn(ghPullRequest);
        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);

        given(ghUser.getEmail()).willReturn("email");
        given(ghUser.getLogin()).willReturn("login");

        given(helper.ifOnlyTriggerPhrase()).willReturn(false);
        given(helper.isRetestPhrase(eq("test this please"))).willReturn(true);
        given(helper.isWhitelisted(ghUser)).willReturn(true);
        given(helper.getTrigger()).willReturn(trigger);
        given(helper.getBlackListLabels()).willReturn(Collections.set("bug", "help wanted"));
        given(helper.getWhiteListLabels()).willReturn(null);

        // WHEN
        ghprbRepository.check(); // PR was created

        mockComments("test this please", tomorrow);
        ghprbRepository.check(); // PR was updated

        // THEN
        verifyGetGithub(2, 2, 1);
        verifyNoMoreInteractions(gt);

        /* GH PR verifications */
        verify(builds, times(2)).build(any(GhprbPullRequest.class), any(GHUser.class), any(String.class));
        verifyNoMoreInteractions(builds);

        verify(ghRepository, times(2)).getPullRequests(eq(OPEN)); // Call to Github API
        verify(ghRepository, times(2))
                .createCommitStatus(eq("head sha"), eq(PENDING), eq(""), eq(MSG), eq("default")); // Call to Github API
        verify(ghRepository, times(1)).getPullRequest(Mockito.anyInt());
        verifyNoMoreInteractions(ghRepository);

        verify(ghPullRequest, times(2)).getTitle();
        verify(ghPullRequest, times(5)).getUser();
        verify(ghPullRequest, times(2)).getMergeable(); // Call to Github API
        verify(ghPullRequest, times(9)).getHead();
        verify(ghPullRequest, times(7)).getBase();
        verify(ghPullRequest, times(5)).getNumber();
        verify(ghPullRequest, times(2)).getUpdatedAt();
        verify(ghPullRequest, times(1)).getCreatedAt();
        verify(ghPullRequest, times(2)).getHtmlUrl();
        verify(ghPullRequest, times(2)).getLabels();

        verify(ghPullRequest, times(1)).getId();
        verify(ghPullRequest, times(1)).getComments();
        verify(ghPullRequest, times(4)).listCommits();

        verify(ghPullRequest, times(2)).getBody();
        verifyNoMoreInteractions(ghPullRequest);

        verify(helper, times(2)).isWhitelisted(eq(ghUser)); // Call to Github API
        verify(helper, times(2)).ifOnlyTriggerPhrase();
        verify(helper, times(2)).getBuilds();
        verify(helper, times(2)).getWhiteListTargetBranches();
        verify(helper, times(2)).getBlackListTargetBranches();
        verify(helper, times(2)).getBlackListLabels();
        verify(helper, times(2)).getWhiteListLabels();
        verify(helper, times(2)).getIncludedRegionPatterns();
        verify(helper, times(2)).getExcludedRegionPatterns();

        verify(helper).isWhitelistPhrase(eq("test this please"));
        verify(helper).isOktotestPhrase(eq("test this please"));
        verify(helper).isRetestPhrase(eq("test this please"));
        verify(helper).isAdmin(eq(ghUser));
        verify(helper, times(4)).isProjectDisabled();
        verify(helper, times(2)).checkSkipBuildPhrase(eq(ghPullRequest));
        verifyNoMoreInteractions(helper);

        verify(ghUser, times(1)).getEmail(); // Call to Github API
        verify(ghUser, times(3)).getLogin();
        verify(ghUser, times(2)).getName();
        verifyNoMoreInteractions(ghUser);

        verify(builds, times(2)).build(any(GhprbPullRequest.class), any(GHUser.class), any(String.class));
        verifyNoMoreInteractions(builds);
    }

    private void mockComments(String commentBody, Date updated) throws IOException {
        GHIssueComment comment = mock(GHIssueComment.class);
        given(comment.getUpdatedAt()).willReturn(updated);
        given(comment.getUser()).willReturn(ghUser);
        given(comment.getBody()).willReturn(commentBody);
        List<GHIssueComment> comments = new ArrayList<>();
        comments.add(comment);
        given(ghPullRequest.getComments()).willReturn(comments);
        given(ghPullRequest.getCommentsCount()).willReturn(comments.size());
    }

    private void mockHeadAndBase() {
        given(ghPullRequest.getHead()).willReturn(head);
        given(base.getSha()).willReturn("base sha");
        given(ghPullRequest.getBase()).willReturn(base);
        given(head.getSha()).willReturn("head sha");
    }

    @SuppressWarnings(value = {"rawtypes", "unchecked"})
    private void mockCommitList() {
        PagedIterator itr = Mockito.mock(PagedIterator.class);
        PagedIterable pagedItr = Mockito.mock(PagedIterable.class);
        Mockito.when(ghPullRequest.listCommits()).thenReturn(pagedItr);
        Mockito.when(pagedItr.iterator()).thenReturn(itr);
        Mockito.when(itr.hasNext()).thenReturn(false);
    }

    @Test
    public void testCheckMethodWithNoPR() throws Exception {
        // GIVEN
        List<GHPullRequest> ghPullRequests = new ArrayList<>();
        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);
        given(ghRepository.getPullRequest((int) ghPullRequest.getId())).willReturn(ghPullRequest);

        // WHEN
        ghprbRepository.check();
        verify(trigger).isActive();

        // THEN
        verifyGetGithub(2, 2, 1);
        verifyNoMoreInteractions(gt);

        verify(ghRepository, times(1)).getPullRequests(OPEN); // Call to Github API
        verifyNoMoreInteractions(helper, ghRepository);
    }

    @Test
    public void testExceedRateLimit() throws Exception {
        // GIVEN
        getNewTrigger();
        rateLimit.remaining = 0;
        verify(gt, times(1)).getRateLimit();

        // WHEN
        startTrigger();

        // THEN
        verify(gt, times(1)).getRateLimit();
        verifyGetGithub(1, 1, 0);
        verifyZeroInteractions(ghRepository);
        verifyZeroInteractions(gitHub);
        verifyZeroInteractions(gt);
    }

    @Test
    public void testSignature() throws Exception {
        String body = URLEncoder.encode("payload=" + GhprbTestUtil.PAYLOAD, "UTF-8");
        String actualSecret = "123";
        String actualSignature = createSHA1Signature(actualSecret, body);
        String fakeSignature = createSHA1Signature("abc", body);

        GhprbGitHubAuth ghAuth = Mockito.spy(new GhprbGitHubAuth("", "", "", "", "", Secret.fromString(actualSecret)));
        doReturn(true).when(trigger).isActive();

        doReturn(ghAuth).when(trigger).getGitHubApiAuth();

        Assert.assertFalse(actualSignature.equals(fakeSignature));
        Assert.assertTrue(actualSecret.equals(ghAuth.getSecret().getPlainText()));

        Assert.assertTrue(trigger.matchSignature(body, actualSignature));
        Assert.assertFalse(trigger.matchSignature(body, fakeSignature));
    }

    private String createSHA1Signature(String secret, String body) throws Exception {
        String algorithm = "HmacSHA1";
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(), algorithm);
        Mac mac = Mac.getInstance(algorithm);
        mac.init(keySpec);

        byte[] signatureBytes = mac.doFinal(body.getBytes("UTF-8"));
        String signature = Hex.encodeHexString(signatureBytes);
        return "sha1=" + signature;
    }

    private void initGHPRWithTestData() throws IOException {
        /* Mock PR data */
        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghUser.getEmail()).willReturn("email");
        given(helper.isWhitelisted(ghUser)).willReturn(true);
        given(ghPullRequest.getUpdatedAt()).willReturn(UPDATE_DATE);

        /* Mock head\base */
        given(base.getSha()).willReturn("base sha");
        given(ghPullRequest.getBase()).willReturn(base);

        given(ghPullRequest.getHead()).willReturn(head);
        given(head.getSha()).willReturn("head sha");

        ghprbRepository = spy(new GhprbRepository(TEST_REPO_NAME, trigger));

        Mockito.doNothing().when(ghprbRepository).addComment(Mockito.anyInt(), anyString());
        Mockito.doNothing().when(ghprbRepository).addComment(Mockito.anyInt(), anyString(), any(Run.class), any(TaskListener.class));

        doReturn(ghprbRepository).when(trigger).getRepository();

        ghprbPullRequest = new GhprbPullRequest(ghPullRequest, helper, ghprbRepository);

        // Reset mocks not to mix init data invocations with tests
        reset(ghPullRequest, ghUser, helper, head, base);
    }

    // Verifications
    private void verifyGetGithub(int triggerCount, int rateCount, int repoCount) throws Exception {
        verify(trigger, times(triggerCount)).getGitHub();
        verify(gt, times(rateCount)).getRateLimit();
        verify(gt, times(repoCount)).getRepository(anyString());
    }
}
