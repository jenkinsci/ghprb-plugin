package org.jenkinsci.plugins.ghprb;

import hudson.util.CopyOnWriteMap.Hash;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

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

/**
 * Unit tests for {@link GhprbRepository}.
 */
@RunWith(MockitoJUnitRunner.class)
public class GhprbRepositoryTest {

    private static final String TEST_USER_NAME = "test-user";
    private static final String TEST_REPO_NAME = "test-repo";
    private static final Date UPDATE_DATE = new Date();

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

    private GhprbRepository ghprbRepository;
    private Map<Integer,GhprbPullRequest> pulls;
    private GhprbPullRequest ghprbPullRequest;

    @Before
    public void setUp() throws IOException {
        initGHPRWithTestData();

        // Mock github API
        given(helper.getGitHub()).willReturn(gitHub);
        given(gitHub.get()).willReturn(gt);
        given(gt.getRepository(anyString())).willReturn(ghRepository);

        // Mock rate limit
        given(gt.getRateLimit()).willReturn(ghRateLimit);
        increaseRateLimitToDefaults();
    }

    @Test
    public void testCheckMethodWithOnlyExistingPRs() throws IOException {
        // GIVEN
        List<GHPullRequest> ghPullRequests = createListWithMockPR();
        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);

        mockHeadAndBase();

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
        verifyNoMoreInteractions(ghPullRequest);

        verify(helper).ifOnlyTriggerPhrase();
        verify(helper).getWhiteListTargetBranches();
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

        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);

        given(ghPullRequest.getUpdatedAt()).willReturn(UPDATE_DATE);
        given(ghPullRequest.getNumber()).willReturn(100);
        given(ghPullRequest.getMergeable()).willReturn(true);
        given(ghPullRequest.getTitle()).willReturn("title");
        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghPullRequest.getUrl()).willReturn(new URL("https://github.com/org/repo/pull/100"));

        given(ghUser.getEmail()).willReturn("email");

        given(helper.ifOnlyTriggerPhrase()).willReturn(false);
        given(helper.isWhitelisted(ghUser)).willReturn(true);

        // WHEN
        ghprbRepository.check();

        // THEN

        verifyGetGithub(1);
        verifyNoMoreInteractions(gt);

        /** GH PR verifications */
        verify(builds, times(1)).build(any(GhprbPullRequest.class));
        verify(ghRepository, times(1)).getPullRequests(OPEN); // Call to Github API
        verify(ghRepository, times(1))
                .createCommitStatus(eq("head sha"), eq(PENDING), isNull(String.class), eq("msg")); // Call to Github API
        verifyNoMoreInteractions(ghRepository);

        verify(ghPullRequest, times(1)).getTitle();
        verify(ghPullRequest, times(2)).getUser();
        verify(ghPullRequest, times(1)).getMergeable(); // Call to Github API
        verify(ghPullRequest, times(8)).getHead();
        verify(ghPullRequest, times(3)).getBase();
        verify(ghPullRequest, times(5)).getNumber();
        verify(ghPullRequest, times(3)).getUpdatedAt();
        verify(ghPullRequest, times(1)).getUrl();
        verifyNoMoreInteractions(ghPullRequest);

        verify(helper, times(1)).isWhitelisted(eq(ghUser));  // Call to Github API
        verify(helper, times(2)).ifOnlyTriggerPhrase();
        verify(helper, times(1)).getBuilds();
        verify(helper, times(2)).getWhiteListTargetBranches();
        verifyNoMoreInteractions(helper);

        verify(ghUser, times(1)).getEmail();   // Call to Github API
        verify(ghUser, times(1)).getLogin();
        verifyNoMoreInteractions(ghUser);
    }

    private GhprbBuilds mockBuilds() throws IOException {
        GhprbBuilds builds = mock(GhprbBuilds.class);
        given(helper.getBuilds()).willReturn(builds);
        given(builds.build(any(GhprbPullRequest.class))).willReturn("msg");
        given(ghRepository.createCommitStatus(anyString(), any(GHCommitState.class), anyString(), anyString())).willReturn(null);
        return builds;
    }

    @Test
    public void testCheckMethodWhenPrWasUpdatedWithNonKeyPhrase() throws IOException {
        // GIVEN
        List<GHPullRequest> ghPullRequests = createListWithMockPR();

        mockComments("comment body");
        mockHeadAndBase();
        GhprbBuilds builds = mockBuilds();

        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);
        given(ghPullRequest.getUpdatedAt()).willReturn(new Date()).willReturn(new DateTime().plusDays(1).toDate());
        given(ghPullRequest.getNumber()).willReturn(100);
        given(ghPullRequest.getMergeable()).willReturn(true);
        given(ghPullRequest.getTitle()).willReturn("title");
        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghPullRequest.getUrl()).willReturn(new URL("https://github.com/org/repo/pull/100"));

        given(ghUser.getEmail()).willReturn("email");
        given(ghUser.getLogin()).willReturn("login");

        given(helper.ifOnlyTriggerPhrase()).willReturn(false);
        given(helper.isWhitelisted(ghUser)).willReturn(true);

        // WHEN
        ghprbRepository.check();  // PR was created
        ghprbRepository.check();  // PR was updated

        // THEN
        verifyGetGithub(2);
        verifyNoMoreInteractions(gt);

        /** GH PR verifications */
        verify(builds, times(1)).build(any(GhprbPullRequest.class));
        verify(ghRepository, times(2)).getPullRequests(eq(OPEN)); // Call to Github API
        verify(ghRepository, times(1))
                .createCommitStatus(eq("head sha"), eq(PENDING), isNull(String.class), eq("msg")); // Call to Github API
        verifyNoMoreInteractions(ghRepository);

        verify(ghPullRequest, times(2)).getTitle();
        verify(ghPullRequest, times(2)).getUser();
        verify(ghPullRequest, times(1)).getMergeable(); // Call to Github API
        verify(ghPullRequest, times(8)).getHead();
        verify(ghPullRequest, times(3)).getBase();
        verify(ghPullRequest, times(5)).getNumber();
        verify(ghPullRequest, times(1)).getUrl();
        verify(ghPullRequest, times(4)).getUpdatedAt();

        verify(ghPullRequest, times(1)).getComments();
        verifyNoMoreInteractions(ghPullRequest);

        verify(helper, times(1)).isWhitelisted(eq(ghUser));  // Call to Github API
        verify(helper, times(2)).ifOnlyTriggerPhrase();
        verify(helper, times(1)).getBuilds();
        verify(helper, times(2)).getWhiteListTargetBranches();

        verify(helper).isWhitelistPhrase(eq("comment body"));
        verify(helper).isOktotestPhrase(eq("comment body"));
        verify(helper).isRetestPhrase(eq("comment body"));
        verify(helper).isTriggerPhrase(eq("comment body"));
        verifyNoMoreInteractions(helper);

        verify(ghUser, times(1)).getEmail();   // Call to Github API
        verify(ghUser, times(2)).getLogin();
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
        GhprbBuilds builds = mockBuilds();

        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);
        given(ghPullRequest.getUpdatedAt()).willReturn(new Date()).willReturn(new Date()).willReturn(new DateTime().plusDays(1).toDate());
        given(ghPullRequest.getNumber()).willReturn(100);
        given(ghPullRequest.getMergeable()).willReturn(true);
        given(ghPullRequest.getTitle()).willReturn("title");
        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghPullRequest.getUrl()).willReturn(new URL("https://github.com/org/repo/pull/100"));

        given(ghUser.getEmail()).willReturn("email");
        given(ghUser.getLogin()).willReturn("login");

        given(helper.ifOnlyTriggerPhrase()).willReturn(false);
        given(helper.isRetestPhrase(eq("test this please"))).willReturn(true);
        given(helper.isWhitelisted(ghUser)).willReturn(true);

        // WHEN
        ghprbRepository.check();  // PR was created
        ghprbRepository.check();  // PR was updated

        // THEN
        verifyGetGithub(2);
        verifyNoMoreInteractions(gt);

        /** GH PR verifications */
        verify(builds, times(2)).build(any(GhprbPullRequest.class));
        verify(ghRepository, times(2)).getPullRequests(eq(OPEN)); // Call to Github API
        verify(ghRepository, times(2))
                .createCommitStatus(eq("head sha"), eq(PENDING), isNull(String.class), eq("msg")); // Call to Github API
        verifyNoMoreInteractions(ghRepository);

        verify(ghPullRequest, times(2)).getTitle();
        verify(ghPullRequest, times(2)).getUser();
        verify(ghPullRequest, times(2)).getMergeable(); // Call to Github API
        verify(ghPullRequest, times(8)).getHead();
        verify(ghPullRequest, times(3)).getBase();
        verify(ghPullRequest, times(5)).getNumber();
        verify(ghPullRequest, times(4)).getUpdatedAt();
        verify(ghPullRequest, times(1)).getUrl();

        verify(ghPullRequest, times(1)).getComments();
        verifyNoMoreInteractions(ghPullRequest);

        verify(helper, times(2)).isWhitelisted(eq(ghUser));  // Call to Github API
        verify(helper, times(2)).ifOnlyTriggerPhrase();
        verify(helper, times(2)).getBuilds();
        verify(helper, times(2)).getWhiteListTargetBranches();

        verify(helper).isWhitelistPhrase(eq("test this please"));
        verify(helper).isOktotestPhrase(eq("test this please"));
        verify(helper).isRetestPhrase(eq("test this please"));
        verify(helper).isAdmin(eq("login"));
        verifyNoMoreInteractions(helper);

        verify(ghUser, times(1)).getEmail();   // Call to Github API
        verify(ghUser, times(2)).getLogin();
        verifyNoMoreInteractions(ghUser);

        verify(builds, times(2)).build(any(GhprbPullRequest.class));
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

    @Test
    public void testCheckMethodWithNoPR() throws IOException {
        // GIVEN
        List<GHPullRequest> ghPullRequests = new ArrayList<GHPullRequest>();
        given(ghRepository.getPullRequests(eq(GHIssueState.OPEN))).willReturn(ghPullRequests);

        // WHEN
        ghprbRepository.check();

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

        pulls = new Hash<Integer, GhprbPullRequest>();
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
