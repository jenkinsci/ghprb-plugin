package org.jenkinsci.plugins.ghprb;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.stubbing.OngoingStubbing;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
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
        given(head.getSha()).willReturn("some sha");
        given(base.getRef()).willReturn("some ref");

        // Mocks for GHPullRequest
        given(pr.getNumber()).willReturn(10);
        given(pr.getCreatedAt()).willReturn(new Date());
        given(pr.getUpdatedAt()).willReturn(new Date());
        given(pr.getTitle()).willReturn("title");
        given(pr.getHead()).willReturn(head);
        given(pr.getBase()).willReturn(base);

        // Mocks for listing file details in the GHPullRequest
        @SuppressWarnings("unchecked")
        PagedIterable<GHPullRequestFileDetail> pagedIterable = mock(PagedIterable.class);
        @SuppressWarnings("unchecked")
        PagedIterator<GHPullRequestFileDetail> pagedIterator = mock(PagedIterator.class);
        given(pagedIterable.iterator()).willReturn(pagedIterator);
        given(pagedIterable.withPageSize(100)).willReturn(pagedIterable);
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
        given(repository.getHttpTransportUrl()).willReturn(expectedAuthorRepoGitUrl);

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
}
