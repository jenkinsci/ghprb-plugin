package org.jenkinsci.plugins.ghprb;

import org.apache.commons.codec.binary.Hex;
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
import hudson.model.AbstractProject;
import hudson.model.queue.QueueTaskFuture;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.kohsuke.github.GHCommitState.PENDING;
import static org.kohsuke.github.GHIssueState.OPEN;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;

/**
 * Unit tests for {@link GhprbRepository}.
 */
@RunWith(MockitoJUnitRunner.class)
public class GhprbRepositoryTest {

    private static final String TEST_USER_NAME = "test-user";
    private static final String TEST_REPO_NAME = "test-repo";
    private static final Date UPDATE_DATE = new Date();
    private static final String msg = "Build triggered. sha1 is merged.";

    @Mock
    private GitHub gt;
    @Mock
    private GHRepository ghRepository;
    @Mock
    private GhprbGitHub gitHub;
    @Mock
    private Ghprb helper;
    @Mock
    private GHRateLimit ghRateLimit;
    @Mock
    private GHPullRequest ghPullRequest;
    @Mock
    private GHCommitPointer base;
    @Mock
    private GHCommitPointer head;
    @Mock
    private GHUser ghUser;
    
    private GhprbTrigger trigger;

    private GhprbRepository ghprbRepository;
    private ConcurrentMap<Integer, GhprbPullRequest> pulls;
    private GhprbPullRequest ghprbPullRequest;

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Before
    @SuppressWarnings("unused")
    public void setUp() throws Exception {
        AbstractProject<?, ?> project = jenkinsRule.createFreeStyleProject("GhprbRepoTest");
        trigger = spy(GhprbTestUtil.getTrigger(null));
        doReturn(mock(QueueTaskFuture.class)).when(trigger).startJob(any(GhprbCause.class), any(GhprbRepository.class));
        initGHPRWithTestData();

        // Mock github API
        given(helper.getGitHub()).willReturn(gitHub);
        given(gitHub.get()).willReturn(gt);
        given(gt.getRepository(anyString())).willReturn(ghRepository);

        // Mock rate limit
        given(gt.getRateLimit()).willReturn(ghRateLimit);
        increaseRateLimitToDefaults();
        addSimpleStatus();
    }
    
    
    private void addSimpleStatus() throws IOException {
        GhprbSimpleStatus status = new GhprbSimpleStatus("default");
        trigger.getExtensions().remove(GhprbSimpleStatus.class);
        trigger.getExtensions().add(status);
    }

    @Test
    public void testCheckMethodWhenUsingGitHubEnterprise() throws IOException {
        // GIVEN
        given(gt.getRateLimit()).willThrow(new FileNotFoundException());
        List<GHPullRequest> ghPullRequests = createListWithMockPR();
        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);

        mockHeadAndBase();
        mockCommitList();

        given(helper.ifOnlyTriggerPhrase()).willReturn(true);

        pulls.put(1, ghprbPullRequest);

        given(ghPullRequest.getUpdatedAt()).willReturn(UPDATE_DATE);
        given(ghPullRequest.getNumber()).willReturn(1);

        // WHEN
        ghprbRepository.check();

        // THEN
        verifyGetGithub(1);
    }

    @Test
    public void testCheckMethodWithOnlyExistingPRs() throws IOException {
        // GIVEN
        List<GHPullRequest> ghPullRequests = createListWithMockPR();
        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);

        mockHeadAndBase();
        mockCommitList();

        given(helper.ifOnlyTriggerPhrase()).willReturn(true);

        pulls.put(1, ghprbPullRequest);

        given(ghPullRequest.getUpdatedAt()).willReturn(UPDATE_DATE);
        given(ghPullRequest.getNumber()).willReturn(1);

        // WHEN
        ghprbRepository.check();

        // THEN
        verifyGetGithub(1);

        /** GH Repo verifications */
        verify(ghRepository, only()).getPullRequests(OPEN); // Call to Github API
        verifyNoMoreInteractions(ghRepository);

        /** GH PR verifications */
        verify(ghPullRequest, times(3)).getHead();
        verify(ghPullRequest, times(1)).getBase();
        verify(ghPullRequest, times(2)).getNumber();
        verify(ghPullRequest, times(1)).getUpdatedAt();
        verify(ghPullRequest, times(1)).getBody();
        verifyNoMoreInteractions(ghPullRequest);

        verify(helper).ifOnlyTriggerPhrase();
        verify(helper).getWhiteListTargetBranches();
        verify(helper, times(3)).isProjectDisabled();
        verifyNoMoreInteractions(helper);
        verifyNoMoreInteractions(gt);

        verifyZeroInteractions(ghUser);
    }

    @Test
    public void testCheckMethodWithNewPR() throws IOException {
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

        given(ghUser.getEmail()).willReturn("email");

        given(helper.ifOnlyTriggerPhrase()).willReturn(false);
        given(helper.isWhitelisted(ghUser)).willReturn(true);
        given(helper.getTrigger()).willReturn(trigger);

        // WHEN
        ghprbRepository.check();

        // THEN

        verifyGetGithub(1);
        verifyNoMoreInteractions(gt);

        /** GH PR verifications */
        verify(builds, times(1)).build(any(GhprbPullRequest.class), any(GHUser.class), any(String.class));
        verify(ghRepository, times(1)).getPullRequests(OPEN); // Call to Github API
        verify(ghRepository, times(1)).createCommitStatus(eq("head sha"), eq(PENDING), isNull(String.class), eq(msg), eq("default")); // Call to Github API
        verifyNoMoreInteractions(ghRepository);

        verify(ghPullRequest, times(1)).getTitle();
        verify(ghPullRequest, times(2)).getUser();
        verify(ghPullRequest, times(1)).getMergeable(); // Call to Github API
        verify(ghPullRequest, times(8)).getHead();
        verify(ghPullRequest, times(3)).getBase();
        verify(ghPullRequest, times(5)).getNumber();
        verify(ghPullRequest, times(3)).getUpdatedAt();
        verify(ghPullRequest, times(1)).getHtmlUrl();
        verify(ghPullRequest, times(1)).listCommits();
        verify(ghPullRequest, times(2)).getBody();
        verifyNoMoreInteractions(ghPullRequest);

        verify(helper, times(1)).isWhitelisted(eq(ghUser)); // Call to Github API
        verify(helper, times(2)).ifOnlyTriggerPhrase();
        verify(helper, times(1)).getBuilds();
        verify(helper, times(2)).getWhiteListTargetBranches();
        verify(helper, times(5)).isProjectDisabled();
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
    public void testCheckMethodWhenPrWasUpdatedWithNonKeyPhrase() throws IOException {
        // GIVEN
        List<GHPullRequest> ghPullRequests = createListWithMockPR();

        mockComments("comment body");
        mockHeadAndBase();
        mockCommitList();
        GhprbBuilds builds = mockBuilds();

        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);
        given(ghPullRequest.getUpdatedAt()).willReturn(new Date()).willReturn(new DateTime().plusDays(1).toDate());
        given(ghPullRequest.getNumber()).willReturn(100);
        given(ghPullRequest.getMergeable()).willReturn(true);
        given(ghPullRequest.getTitle()).willReturn("title");
        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghPullRequest.getHtmlUrl()).willReturn(new URL("https://github.com/org/repo/pull/100"));
        given(ghPullRequest.getApiURL()).willReturn(new URL("https://github.com/org/repo/pull/100"));

        given(ghUser.getEmail()).willReturn("email");
        given(ghUser.getLogin()).willReturn("login");

        given(helper.ifOnlyTriggerPhrase()).willReturn(false);
        given(helper.isWhitelisted(ghUser)).willReturn(true);
        given(helper.getTrigger()).willReturn(trigger);

        // WHEN
        ghprbRepository.check(); // PR was created
        ghprbRepository.check(); // PR was updated

        // THEN
        verifyGetGithub(2);
        verifyNoMoreInteractions(gt);

        /** GH PR verifications */
        verify(builds, times(1)).build(any(GhprbPullRequest.class), any(GHUser.class), any(String.class));
        verify(ghRepository, times(2)).getPullRequests(eq(OPEN)); // Call to Github API
        verify(ghRepository, times(1)).createCommitStatus(eq("head sha"), eq(PENDING), isNull(String.class), eq(msg), eq("default")); // Call to Github API
        verifyNoMoreInteractions(ghRepository);

        verify(ghPullRequest, times(2)).getTitle();
        verify(ghPullRequest, times(2)).getUser();
        verify(ghPullRequest, times(1)).getMergeable(); // Call to Github API
        verify(ghPullRequest, times(8)).getHead();
        verify(ghPullRequest, times(3)).getBase();
        verify(ghPullRequest, times(5)).getNumber();
        verify(ghPullRequest, times(1)).getHtmlUrl();
        verify(ghPullRequest, times(4)).getUpdatedAt();

        verify(ghPullRequest, times(1)).getComments();
        verify(ghPullRequest, times(1)).listCommits();
        verify(ghPullRequest, times(2)).getBody();
        verifyNoMoreInteractions(ghPullRequest);

        verify(helper, times(1)).isWhitelisted(eq(ghUser)); // Call to Github API
        verify(helper, times(2)).ifOnlyTriggerPhrase();
        verify(helper, times(1)).getBuilds();
        verify(helper, times(2)).getWhiteListTargetBranches();

        // verify(helper).isBotUser(eq(ghUser));
        verify(helper).isWhitelistPhrase(eq("comment body"));
        verify(helper).isOktotestPhrase(eq("comment body"));
        verify(helper).isRetestPhrase(eq("comment body"));
        verify(helper).isTriggerPhrase(eq("comment body"));
        verify(helper, times(6)).isProjectDisabled();
        verifyNoMoreInteractions(helper);

        verify(ghUser, times(1)).getEmail(); // Call to Github API
        verify(ghUser, times(1)).getLogin();
        verifyNoMoreInteractions(ghUser);
    }

    private List<GHPullRequest> createListWithMockPR() {
        List<GHPullRequest> ghPullRequests = new ArrayList<GHPullRequest>();
        ghPullRequests.add(ghPullRequest);
        return ghPullRequests;
    }

    @Test
    public void testCheckMethodWhenPrWasUpdatedWithRetestPhrase() throws IOException {
        // GIVEN
        List<GHPullRequest> ghPullRequests = createListWithMockPR();

        mockComments("test this please");
        mockHeadAndBase();
        mockCommitList();
        GhprbBuilds builds = mockBuilds();

        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);
        given(ghPullRequest.getUpdatedAt()).willReturn(new Date()).willReturn(new Date()).willReturn(new DateTime().plusDays(1).toDate());
        given(ghPullRequest.getNumber()).willReturn(100);
        given(ghPullRequest.getMergeable()).willReturn(true);
        given(ghPullRequest.getTitle()).willReturn("title");
        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghPullRequest.getHtmlUrl()).willReturn(new URL("https://github.com/org/repo/pull/100"));
        given(ghPullRequest.getApiURL()).willReturn(new URL("https://github.com/org/repo/pull/100"));

        given(ghUser.getEmail()).willReturn("email");
        given(ghUser.getLogin()).willReturn("login");

        given(helper.ifOnlyTriggerPhrase()).willReturn(false);
        given(helper.isRetestPhrase(eq("test this please"))).willReturn(true);
        given(helper.isWhitelisted(ghUser)).willReturn(true);
        given(helper.getTrigger()).willReturn(trigger);

        // WHEN
        ghprbRepository.check(); // PR was created
        ghprbRepository.check(); // PR was updated

        // THEN
        verifyGetGithub(2);
        verifyNoMoreInteractions(gt);

        /** GH PR verifications */
        verify(builds, times(2)).build(any(GhprbPullRequest.class), any(GHUser.class), any(String.class));
        verifyNoMoreInteractions(builds);
        
        verify(ghRepository, times(2)).getPullRequests(eq(OPEN)); // Call to Github API
        verify(ghRepository, times(2)).createCommitStatus(eq("head sha"), eq(PENDING), isNull(String.class), eq(msg), eq("default")); // Call to Github API
        verifyNoMoreInteractions(ghRepository);

        verify(ghPullRequest, times(2)).getTitle();
        verify(ghPullRequest, times(2)).getUser();
        verify(ghPullRequest, times(2)).getMergeable(); // Call to Github API
        verify(ghPullRequest, times(8)).getHead();
        verify(ghPullRequest, times(3)).getBase();
        verify(ghPullRequest, times(5)).getNumber();
        verify(ghPullRequest, times(4)).getUpdatedAt();
        verify(ghPullRequest, times(1)).getHtmlUrl();

        verify(ghPullRequest, times(1)).getComments();
        verify(ghPullRequest, times(2)).listCommits();

        verify(ghPullRequest, times(2)).getBody();
        verifyNoMoreInteractions(ghPullRequest);

        verify(helper, times(2)).isWhitelisted(eq(ghUser)); // Call to Github API
        verify(helper, times(2)).ifOnlyTriggerPhrase();
        verify(helper, times(2)).getBuilds();
        verify(helper, times(2)).getWhiteListTargetBranches();

        verify(helper).isWhitelistPhrase(eq("test this please"));
        verify(helper).isOktotestPhrase(eq("test this please"));
        verify(helper).isRetestPhrase(eq("test this please"));
        verify(helper).isAdmin(eq(ghUser));
        verify(helper, times(6)).isProjectDisabled();
        verifyNoMoreInteractions(helper);

        verify(ghUser, times(1)).getEmail(); // Call to Github API
        verify(ghUser, times(1)).getLogin();
        verifyNoMoreInteractions(ghUser);

        verify(builds, times(2)).build(any(GhprbPullRequest.class), any(GHUser.class), any(String.class));
        verifyNoMoreInteractions(builds);
    }

    private void mockComments(String commentBody) throws IOException {
        GHIssueComment comment = mock(GHIssueComment.class);
        given(comment.getUpdatedAt()).willReturn(new DateTime().plusDays(3).toDate());
        given(comment.getUser()).willReturn(ghUser);
        given(comment.getBody()).willReturn(commentBody);
        List<GHIssueComment> comments = new ArrayList<GHIssueComment>();
        comments.add(comment);
        given(ghPullRequest.getComments()).willReturn(comments);
    }

    private void mockHeadAndBase() {
        /** Mock head\base */
        given(ghPullRequest.getHead()).willReturn(head);
        given(base.getSha()).willReturn("base sha");
        given(ghPullRequest.getBase()).willReturn(base);
        given(head.getSha()).willReturn("head sha");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void mockCommitList() {
        PagedIterator itr = Mockito.mock(PagedIterator.class);
        PagedIterable pagedItr = Mockito.mock(PagedIterable.class);
        Mockito.when(ghPullRequest.listCommits()).thenReturn(pagedItr);
        Mockito.when(pagedItr.iterator()).thenReturn(itr);
        Mockito.when(itr.hasNext()).thenReturn(false);
    }

    @Test
    public void testCheckMethodWithNoPR() throws IOException {
        // GIVEN
        List<GHPullRequest> ghPullRequests = new ArrayList<GHPullRequest>();
        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);

        // WHEN
        ghprbRepository.check();
        verify(helper).isProjectDisabled();

        // THEN
        verifyGetGithub(1);
        verifyNoMoreInteractions(gt);

        verify(ghRepository, times(1)).getPullRequests(OPEN); // Call to Github API
        verifyNoMoreInteractions(ghRepository);
        verifyZeroInteractions(helper);
    }

    @Test
    public void testExceedRateLimit() throws IOException {
        // GIVEN
        ghRateLimit.remaining = 0;

        // WHEN
        ghprbRepository.check();

        // THEN
        verify(helper, only()).getGitHub();
        verify(gitHub, only()).get();
        verify(gt, only()).getRateLimit();
        verifyZeroInteractions(ghRepository);
        verifyZeroInteractions(gitHub);
        verifyZeroInteractions(gt);
    }

    @Test
    public void testSignature() throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        String body = URLEncoder.encode("payload=" + GhprbTestUtil.PAYLOAD, "UTF-8");
        String actualSecret = "123";
        String actualSignature = createSHA1Signature(actualSecret, body);
        String fakeSignature = createSHA1Signature("abc", body);

        given(helper.getTrigger()).willReturn(trigger);
        given(trigger.getSecret()).willReturn(actualSecret);

        Assert.assertFalse(actualSignature.equals(fakeSignature));
        Assert.assertTrue(ghprbRepository.checkSignature(body, actualSignature));
        Assert.assertFalse(ghprbRepository.checkSignature(body, fakeSignature));
    }

    private String createSHA1Signature(String secret, String body) throws UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {
        String algorithm = "HmacSHA1";
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(), algorithm);
        Mac mac = Mac.getInstance(algorithm);
        mac.init(keySpec);

        byte[] signatureBytes = mac.doFinal(body.getBytes("UTF-8"));
        String signature = new String(Hex.encodeHexString(signatureBytes));
        return "sha1=" + signature;
    }

    private void initGHPRWithTestData() throws IOException {
        /** Mock PR data */
        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghUser.getEmail()).willReturn("email");
        given(helper.isWhitelisted(ghUser)).willReturn(true);
        given(ghPullRequest.getUpdatedAt()).willReturn(UPDATE_DATE);

        /** Mock head\base */
        given(base.getSha()).willReturn("base sha");
        given(ghPullRequest.getBase()).willReturn(base);

        given(ghPullRequest.getHead()).willReturn(head);
        given(head.getSha()).willReturn("head sha");

        pulls = new ConcurrentHashMap<Integer, GhprbPullRequest>();
        ghprbRepository = new GhprbRepository(TEST_USER_NAME, TEST_REPO_NAME, helper, pulls);
        ghprbPullRequest = new GhprbPullRequest(ghPullRequest, helper, ghprbRepository);

        // Reset mocks not to mix init data invocations with tests
        reset(ghPullRequest, ghUser, helper, head, base);
    }

    private void increaseRateLimitToDefaults() {
        ghRateLimit.remaining = 5000;
    }

    // Verifications
    private void verifyGetGithub(int callsCount) throws IOException {
        verify(helper, times(callsCount)).getGitHub();
        verify(gitHub, times(callsCount)).get(); // Call to Github API (once, than cached)
        verify(gt, times(1)).getRepository(anyString()); // Call to Github API
        verify(gt, times(callsCount)).getRateLimit();
    }
}
