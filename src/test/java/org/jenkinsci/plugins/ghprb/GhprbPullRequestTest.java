package org.jenkinsci.plugins.ghprb;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Date;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
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

    @Test
    public void testConstructorWhenAuthorIsWhitelisted() throws IOException {
        // GIVEN
        setupStubs();

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
        // GIVEN
        setupStubs();

        // Mocks for GhprbRepository
        given(repo.getName()).willReturn(null);
        doNothing().when(repo).addComment(eq(10), anyString());

        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);
        GhprbRepository ghprbRepository = mock(GhprbRepository.class);
        given(ghprbRepository.getName()).willReturn("name");

        // WHEN
        ghprbPullRequest.init(helper, ghprbRepository);

        // THEN
        verify(ghprbRepository, times(1)).getName();

    }

    @Test
    public void testInitRepoNameNotNull() throws IOException {
        // GIVEN
        setupStubs();

        // Mocks for GhprbRepository
        given(repo.getName()).willReturn("name");
        doNothing().when(repo).addComment(eq(10), anyString());

        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);
        GhprbRepository ghprbRepository = mock(GhprbRepository.class);
        given(ghprbRepository.getName()).willReturn("name");

        // WHEN
        ghprbPullRequest.init(helper, ghprbRepository);

        // THEN
        verify(ghprbRepository, never()).getName();
    }

    @Test
    public void testDoesNotTriggerBuildsWhenProjectDisabled() throws IOException {
        // GIVEN
        setupStubs();

        // simulate our project is disabled
        given(helper.isProjectDisabled()).willReturn(true);

        // WHEN
        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);
        ghprbPullRequest.check(pr);

        // THEN
        verify(helper.getBuilds(), never()).build(eq(ghprbPullRequest), any(GHUser.class), any(String.class));
    }

    @Test
    public void testDoesTriggerBuildsWhenProjectIsEnabled() throws IOException {
        // GIVEN
        setupStubs();

        // simulate our project is NOT disabled
        given(helper.isProjectDisabled()).willReturn(false);

        // WHEN
        GhprbPullRequest ghprbPullRequest = new GhprbPullRequest(pr, helper, repo);
        ghprbPullRequest.check(pr);

        // THEN
        verify(helper.getBuilds(), times(1)).build(eq(ghprbPullRequest), any(GHUser.class), any(String.class));
    }

    private void setupStubs() throws IOException {
        GHUser ghUser = mock(GHUser.class);
        GHCommitPointer head = mock(GHCommitPointer.class);
        GHCommitPointer base = mock(GHCommitPointer.class);

        // Mocks for GHPullRequest
        given(pr.getNumber()).willReturn(10);
        given(pr.getUpdatedAt()).willReturn(new Date());
        given(pr.getTitle()).willReturn("title");
        given(pr.getHead()).willReturn(head);
        given(pr.getBase()).willReturn(base);
        given(head.getSha()).willReturn("some sha");
        given(base.getRef()).willReturn("some ref");
        given(pr.getUser()).willReturn(ghUser);
        given(ghUser.getEmail()).willReturn("email");

        // Mocks for Ghprb
        given(helper.isWhitelisted(ghUser)).willReturn(true);

        // mock builds so we can check if a build was triggered
        GhprbBuilds builds = mock(GhprbBuilds.class);
        given(helper.getBuilds()).willReturn(builds);

        GhprbTestUtil.mockCommitList(pr);
    }
}
