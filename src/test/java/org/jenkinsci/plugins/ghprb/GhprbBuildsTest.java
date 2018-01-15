package org.jenkinsci.plugins.ghprb;

import hudson.model.Build;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildStatus;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHCommitState;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.PrintStream;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


/**
 * Unit test for {@link org.jenkinsci.plugins.ghprb.GhprbBuilds}.
 */
@RunWith(MockitoJUnitRunner.class)
public class GhprbBuildsTest {

    @Mock
    private GhprbRepository repo;

    @Mock
    private GhprbCause cause;

    @Mock
    private GhprbBuildStatus appender;

    @Mock
    private TaskListener listener;

    @Mock
    private Build build;

    @Mock
    private PrintStream stream;

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private GhprbTrigger trigger;

    private GHCommitState state = GHCommitState.SUCCESS;

    @Before
    public void setup() throws Exception {
        // Mock trigger and add a mocked appender.
        trigger = GhprbTestUtil.getTrigger();
        trigger.getExtensions().add(appender);

        // Mocks for GhprbRepository
        doNothing().when(repo).addComment(anyInt(), anyString());

        // Mock out the logger.
        given(listener.getLogger()).willReturn(stream);
        doNothing().when(stream).println(anyString());
    }

    @Test
    public void testCommentOnBuildResultWithSkip() {
        String testMessage = "--none--";
        given(appender.postBuildComment(build, listener)).willReturn(testMessage);

        // WHEN
        GhprbBuilds builds = new GhprbBuilds(trigger, repo);
        builds.commentOnBuildResult(build, listener, state, cause);

        // THEN
        verify(repo, never()).addComment(Mockito.anyInt(), anyString());
    }

    @Test
    public void testCommentOnBuildResultNoSkip() {
        String testMessage = "test";
        given(appender.postBuildComment(build, listener)).willReturn(testMessage);

        // WHEN
        GhprbBuilds builds = new GhprbBuilds(trigger, repo);
        builds.commentOnBuildResult(build, listener, state, cause);

        // THEN
        verify(repo, times(1)).addComment(cause.getPullID(), testMessage, build, listener);
    }
}

