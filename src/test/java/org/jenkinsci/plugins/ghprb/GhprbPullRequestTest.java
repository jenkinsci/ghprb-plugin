package org.jenkinsci.plugins.ghprb;

import org.joda.time.Duration;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.OngoingStubbing;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link org.jenkinsci.plugins.ghprb.GhprbPullRequest}.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({GhprbPullRequest.class})
public class GhprbPullRequestTest {

    @Mock
    private GHPullRequest pr;

    @Mock
    private Ghprb helper;

    @Mock
    private GhprbRepository repo;

    @Mock
    private GHCommitPointer head;

    @Mock
    private GHPullRequestCommitDetail headDetail;

    @Mock
    private GHCommitPointer base;

    @Mock
    private GhprbRepository ghprbRepository;

    @Mock
    private GHUser ghUser;

    @Mock
    private GhprbBuilds builds;

    @Mock
    private GHIssueComment ghIssueComment;

    @Before
    public void setup() throws IOException {
        String headSha = "some sha";
        given(head.getSha()).willReturn(headSha);
        given(base.getRef()).willReturn("some ref");

        given(headDetail.getSha()).willReturn(headSha);
        given(headDetail.getCommit()).willReturn(mock(GHPullRequestCommitDetail.Commit.class));


        // Mocks for GHPullRequest
        Date prDate = Instant.now().minus(Duration.standardHours(1)).toDate();
        given(pr.getNumber()).willReturn(10);
        given(pr.getCreatedAt()).willReturn(prDate);
        given(pr.getUpdatedAt()).willReturn(prDate);
        given(pr.getTitle()).willReturn("title");
        given(pr.getHead()).willReturn(head);
        given(pr.getBase()).willReturn(base);
        given(pr.listCommits()).willReturn(iterableAsPaged(Collections.singletonList(headDetail)));


        // Mocks for listing file details in the GHPullRequest
        @SuppressWarnings("unchecked")
        PagedIterable<GHPullRequestFileDetail> pagedIterable = mock(PagedIterable.class);
        @SuppressWarnings("unchecked")
        PagedIterator<GHPullRequestFileDetail> pagedIterator = mock(PagedIterator.class);
        given(pagedIterable.iterator()).willReturn(pagedIterator);
        given(pr.listFiles()).willReturn(pagedIterable);

        // Create the list of file paths to return
        List<String> filePaths = Arrays.asList(
                "path1/file1.txt",
                "path1/file2.exe",
                "path1/file3.py",
                "path2/file1.java",
                "path2/file2.rb",
                "path3/file1.yaml",
                ".gitignore");
        List<GHPullRequestFileDetail> fileDetails = new ArrayList<GHPullRequestFileDetail>();

        for (String filePath : filePaths) {
            GHPullRequestFileDetail fileDetail = mock(GHPullRequestFileDetail.class);
            given(fileDetail.getFilename()).willReturn(filePath);
            fileDetails.add(fileDetail);
        }

        // Mock the iterator return calls
        OngoingStubbing<GHPullRequestFileDetail> stubbingNext = when(pagedIterator.next());
        for (GHPullRequestFileDetail fileDetail : fileDetails) {
            stubbingNext = stubbingNext.thenReturn(fileDetail);
        }

        OngoingStubbing<Boolean> stubbingHasNext = when(pagedIterator.hasNext());
        for (int i = 0; i < fileDetails.size(); i++) {
            stubbingHasNext = stubbingHasNext.thenReturn(true);
        }
        stubbingHasNext.thenReturn(false);

        given(ghUser.getEmail()).willReturn("email");

        given(ghprbRepository.getActualPullRequest(10)).willReturn(pr);
        given(ghprbRepository.getName()).willReturn("name");

        given(pr.getHead()).willReturn(head);
        given(pr.getUser()).willReturn(ghUser);

        given(pr.getComments()).willReturn(new ArrayList<GHIssueComment>(Arrays.asList(ghIssueComment)));
        given(ghIssueComment.getBody()).willReturn("My phrase: request for testing");
        given(ghIssueComment.getUpdatedAt()).willReturn(prDate);

        // Mocks for Ghprb
        given(helper.isWhitelisted(ghUser)).willReturn(true);
        given(helper.getBuilds()).willReturn(builds);

        doNothing().when(builds).build(any(GhprbPullRequest.class), any(GHUser.class), anyString());

        // Mocks for GhprbRepository
        given(repo.getName()).willReturn("repoName");

        // Mocks for GhprbRepository
        doNothing().when(repo).addComment(Mockito.anyInt(), anyString());
    }

    @Test
    public void testConstructorWhenAuthorIsWhitelisted() throws IOException {

        // WHEN
        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);

        // THEN
        assertThat(ghprbPullRequest.getId()).isEqualTo(10);
        assertThat(ghprbPullRequest.getAuthorEmail()).isEqualTo("email");
        assertThat(ghprbPullRequest.getHead()).isEqualTo("some sha");
        assertThat(ghprbPullRequest.getTitle()).isEqualTo("title");
        assertThat(ghprbPullRequest.getTarget()).isEqualTo("some ref");
        assertThat(ghprbPullRequest.isMergeable()).isFalse();
    }

    @Test
    public void testInitRepoNameNull() throws IOException {

        given(repo.getName()).willReturn(null);

        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);

        // WHEN
        ghprbPullRequest.init(helper, ghprbRepository);

        // THEN
        verify(pr, times(1)).getHead();
        verify(pr, times(1)).getBase();
        verify(pr, times(1)).getNumber();
        verify(pr, times(1)).getCreatedAt();
        verify(pr, times(2)).getUser();
        Mockito.verifyNoMoreInteractions(pr);

    }

    @Test
    public void testInitRepoNameNotNull() throws IOException {

        // Mocks for GhprbRepository
        given(repo.getName()).willReturn("name");
        doNothing().when(repo).addComment(eq(10), anyString());

        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);

        // WHEN
        ghprbPullRequest.init(helper, ghprbRepository);

        // THEN
        verify(ghprbRepository, never()).getName();
        Mockito.verifyNoMoreInteractions(ghprbRepository);
    }

    @Test
    public void authorRepoGitUrlShouldBeNullWhenNoRepository() throws Exception {
        // GIVEN

        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);

        // WHEN
        ghprbPullRequest.init(helper, ghprbRepository);

        // THEN
        assertThat(ghprbPullRequest.getAuthorRepoGitUrl()).isEqualTo("");
    }

    @Test
    public void authorRepoGitUrlShouldBeSetWhenRepository() throws Exception {
        // GIVEN
        String expectedAuthorRepoGitUrl = "https://github.com/jenkinsci/ghprb-plugin";
        GHRepository repository = mock(GHRepository.class);
        given(repository.gitHttpTransportUrl()).willReturn(expectedAuthorRepoGitUrl);

        given(head.getRepository()).willReturn(repository);

        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);

        // THEN
        assertThat(ghprbPullRequest.getAuthorRepoGitUrl()).isEqualTo(expectedAuthorRepoGitUrl);
    }

    @Test
    public void testContainsWatchedPathsNoRegionsDefined() {
        // GIVEN
        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);

        // WHEN
        ghprbPullRequest.init(helper, ghprbRepository);

        // THEN
        assertThat(ghprbPullRequest.containsWatchedPaths(pr)).isEqualTo(true);
    }

    @Test
    public void testContainsWatchedPathsMatchingIncludedRegion() {
        // GIVEN
        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);
        given(helper.getIncludedRegionPatterns()).willReturn(Collections.singletonList(Pattern.compile("path2/.*")));

        // WHEN
        ghprbPullRequest.init(helper, ghprbRepository);

        // THEN
        assertThat(ghprbPullRequest.containsWatchedPaths(pr)).isEqualTo(true);
    }

    @Test
    public void testContainsWatchedPathsNotMatchingIncludedRegions() {
        // GIVEN
        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);
        given(helper.getIncludedRegionPatterns()).willReturn(Collections.singletonList(Pattern.compile("unknown/.*")));

        // WHEN
        ghprbPullRequest.init(helper, ghprbRepository);

        // THEN
        assertThat(ghprbPullRequest.containsWatchedPaths(pr)).isEqualTo(false);
    }

    @Test
    public void testContainsWatchedPathAllExcluded() {
        // GIVEN
        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);
        given(helper.getExcludedRegionPatterns()).willReturn(Collections.singletonList(Pattern.compile(".*")));

        // WHEN
        ghprbPullRequest.init(helper, ghprbRepository);

        // THEN
        assertThat(ghprbPullRequest.containsWatchedPaths(pr)).isEqualTo(false);
    }

    @Test
    public void testContainsWatchedPathPartialExclusion() {
        // GIVEN
        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);
        given(helper.getExcludedRegionPatterns()).willReturn(Collections.singletonList(Pattern.compile("path1/.*")));

        // WHEN
        ghprbPullRequest.init(helper, ghprbRepository);

        // THEN
        assertThat(ghprbPullRequest.containsWatchedPaths(pr)).isEqualTo(true);
    }

    @Test
    public void testContainsWatchedPathAllExcludedWithInclude() {
        // GIVEN
        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);
        given(helper.getIncludedRegionPatterns()).willReturn(Collections.singletonList(Pattern.compile("path1/.*")));
        given(helper.getExcludedRegionPatterns()).willReturn(Collections.singletonList(Pattern.compile(".*")));

        // WHEN
        ghprbPullRequest.init(helper, ghprbRepository);

        // THEN
        assertThat(ghprbPullRequest.containsWatchedPaths(pr)).isEqualTo(false);
    }

    @Test
    public void testContainsWatchedPathSomeExcludedWithInclude() {
        // GIVEN
        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);
        given(helper.getIncludedRegionPatterns()).willReturn(Collections.singletonList(Pattern.compile("path1/.*")));
        given(helper.getExcludedRegionPatterns()).willReturn(Collections.singletonList(Pattern.compile("path2/.*")));

        // WHEN
        ghprbPullRequest.init(helper, ghprbRepository);

        // THEN
        assertThat(ghprbPullRequest.containsWatchedPaths(pr)).isEqualTo(true);
    }

    @Test
    public void testContainsWatchedPathIncludeFileExtension() {
        // GIVEN
        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);
        given(helper.getIncludedRegionPatterns()).willReturn(Collections.singletonList(Pattern.compile(".*\\.java")));

        // WHEN
        ghprbPullRequest.init(helper, ghprbRepository);

        // THEN
        assertThat(ghprbPullRequest.containsWatchedPaths(pr)).isEqualTo(true);
    }

    @Test
    public void testContainsWatchedPathIncludeFileExtensionExcludeFolder() {
        // GIVEN
        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);
        given(helper.getIncludedRegionPatterns()).willReturn(Collections.singletonList(Pattern.compile(".*\\.java")));
        given(helper.getExcludedRegionPatterns()).willReturn(Collections.singletonList(Pattern.compile("path2/.*")));

        // WHEN
        ghprbPullRequest.init(helper, ghprbRepository);

        // THEN
        assertThat(ghprbPullRequest.containsWatchedPaths(pr)).isEqualTo(false);
    }

    @Test
    public void testCommentMatchingPhraseTrigger() throws IOException {
        // GIVEN
        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);
        ghprbPullRequest.init(helper, ghprbRepository);

        // WHEN
        String phrase = "abracadabra";
        given(helper.isTriggerPhrase(phrase)).willReturn(true);

        GHIssueComment comment = createMockComment(phrase);
        given(pr.getComments()).willReturn(Arrays.asList(ghIssueComment, comment));

        ghprbPullRequest.check(comment);

        // THEN
        verify(builds).build(eq(ghprbPullRequest), eq(ghUser), anyString());
    }

    @Test
    public void testTriggeredWithNoMatchingWatchedPaths() throws IOException {
        // GIVEN
        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);
        given(helper.getIncludedRegionPatterns()).willReturn(Collections.singletonList(Pattern.compile("unknown/.*")));
        ghprbPullRequest.init(helper, ghprbRepository);

        // WHEN
        String phrase = "abracadabra";
        given(helper.isTriggerPhrase(phrase)).willReturn(true);

        GHIssueComment comment = createMockComment(phrase);
        given(pr.getComments()).willReturn(Arrays.asList(ghIssueComment, comment));

        ghprbPullRequest.check(comment);

        // THEN
        verify(builds).build(eq(ghprbPullRequest), eq(ghUser), anyString());
    }

    private GHIssueComment createMockComment(String body) throws IOException {
        GHIssueComment comment = mock(GHIssueComment.class);
        given(comment.getBody()).willReturn(body);
        given(comment.getUpdatedAt()).willReturn(new Date());
        given(comment.getUser()).willReturn(ghUser);
        return comment;
    }

    @Test
    public void shouldNotAddDuplicateRequestForTestingComment() throws Exception {
        PowerMockito.mockStatic(GhprbPullRequest.class);
        // GIVEN
        given(GhprbPullRequest.getRequestForTestingPhrase()).willReturn("My phrase: request for testing");
        given(helper.isWhitelisted(ghUser)).willReturn(false);

        // WHEN
        new GhprbPullRequest(pr, helper, repo);

        // THEN
        verify(repo, never()).addComment(anyInt(), anyString());
    }

    /**
     * Create a {@link PagedIterable} from an {@link Iterable}. PagedIterable is closed for extension, making it
     * difficult to create instances for testing. This works around the limitations using mocks.
     */
    private static <T> PagedIterable<T> iterableAsPaged(final Iterable<T> input) {
        return new PagedIterable<T>() {
            @Override
            public PagedIterator<T> _iterator(final int pageSize) {
                final Iterator<T> base = input.iterator();
                @SuppressWarnings("unchecked")
                PagedIterator<T> iterator = mock(PagedIterator.class);
                given(iterator.hasNext()).willAnswer(new Answer<Boolean>() {
                    @Override
                    public Boolean answer(InvocationOnMock invocation) {
                        return base.hasNext();
                    }
                });
                given(iterator.next()).willAnswer(new Answer<T>() {
                    @Override
                    public T answer(InvocationOnMock invocation) {
                        return base.next();
                    }
                });
                given(iterator.nextPage()).willAnswer(new Answer<List<T>>() {
                    @Override
                    public List<T> answer(InvocationOnMock invocation) {
                        ArrayList<T> list = new ArrayList<>();
                        for (int i = 0; i < pageSize && base.hasNext(); i++) {
                            list.add(base.next());
                        }
                        return list;
                    }
                });
                return iterator;
            }
        };
    }
}
