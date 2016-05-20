package org.jenkinsci.plugins.ghprb;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Date;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link org.jenkinsci.plugins.ghprb.GhprbPullRequest}.
 */
@RunWith(MockitoJUnitRunner.class)
public class GhprbPullRequestTest {

    @Mock
    private GHPullRequest pr;
    @Mock
    private Ghprb helper;
    @Mock
    private GhprbRepository repo;
    @Mock
    private GHCommitPointer head, base;
    @Mock
    private GhprbRepository ghprbRepository;
    @Mock
    private GHUser ghUser;
    @Mock
    private GhprbBuilds builds;
    
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
        
        given(ghUser.getEmail()).willReturn("email");
        
        given(ghprbRepository.getActualPullRequest(10)).willReturn(pr);
        given(ghprbRepository.getName()).willReturn("name");
        
        given(pr.getHead()).willReturn(head);
        given(pr.getUser()).willReturn(ghUser);
        
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
}
