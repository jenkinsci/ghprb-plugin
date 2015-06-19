package org.jenkinsci.plugins.ghprb.extensions.status;

import org.jenkinsci.plugins.ghprb.GhprbPullRequest;
import org.jenkinsci.plugins.ghprb.GhprbTrigger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class GhprbSimpleStatusTest {

    @Mock
    private GHRepository ghRepository;
    @Mock
    private GhprbPullRequest ghprbPullRequest;
    @Mock
    private GhprbTrigger trigger;
    
    @Test
    public void testMergedMessage() throws Exception {
        String mergedMessage = "Build triggered. sha1 is merged.";
        given(ghprbPullRequest.getHead()).willReturn("sha");
        given(ghprbPullRequest.isMergeable()).willReturn(true);

        GhprbSimpleStatus status = spy(new GhprbSimpleStatus("default"));
        status.onBuildTriggered(trigger, ghprbPullRequest, ghRepository);
        
        verify(ghRepository).createCommitStatus(eq("sha"), eq(GHCommitState.PENDING), isNull(String.class), eq(mergedMessage), eq("default"));
        verifyNoMoreInteractions(ghRepository);

        verify(ghprbPullRequest).getHead();
        verify(ghprbPullRequest).isMergeable();
        verifyNoMoreInteractions(ghprbPullRequest);
    }
    
    @Test
    public void testMergeConflictMessage() throws Exception {
        String mergedMessage = "Build triggered. sha1 is original commit.";
        given(ghprbPullRequest.getHead()).willReturn("sha");
        given(ghprbPullRequest.isMergeable()).willReturn(false);

        GhprbSimpleStatus status = spy(new GhprbSimpleStatus("default"));
        status.onBuildTriggered(trigger, ghprbPullRequest, ghRepository);
        
        verify(ghRepository).createCommitStatus(eq("sha"), eq(GHCommitState.PENDING), isNull(String.class), eq(mergedMessage), eq("default"));
        verifyNoMoreInteractions(ghRepository);

        verify(ghprbPullRequest).getHead();
        verify(ghprbPullRequest).isMergeable();
        verifyNoMoreInteractions(ghprbPullRequest);
    }
    
    @Test
    public void testDoesNotSendEmptyContext() throws Exception {
        String mergedMessage = "Build triggered. sha1 is original commit.";
        given(ghprbPullRequest.getHead()).willReturn("sha");
        given(ghprbPullRequest.isMergeable()).willReturn(false);

        GhprbSimpleStatus status = spy(new GhprbSimpleStatus(""));
        status.onBuildTriggered(trigger, ghprbPullRequest, ghRepository);
        
        verify(ghRepository).createCommitStatus(eq("sha"), eq(GHCommitState.PENDING), isNull(String.class), eq(mergedMessage), isNull(String.class));
        verifyNoMoreInteractions(ghRepository);

        verify(ghprbPullRequest).getHead();
        verify(ghprbPullRequest).isMergeable();
        verifyNoMoreInteractions(ghprbPullRequest);
    }
}
