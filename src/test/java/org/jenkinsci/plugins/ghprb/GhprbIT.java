package org.jenkinsci.plugins.ghprb;

import com.google.common.collect.Lists;

import hudson.model.FreeStyleProject;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.stapler.RequestImpl;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import static com.google.common.collect.Lists.newArrayList;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;

@RunWith(MockitoJUnitRunner.class)
public class GhprbIT extends GhprbITBaseTestCase {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    
    @Mock
    private RequestImpl req;
    @Mock
    private GHIssueComment comment;

    
    private FreeStyleProject project;
    

    @Before
    public void setUp() throws Exception {// GIVEN
        project = jenkinsRule.createFreeStyleProject("PRJ");
        super.beforeTest(null, null, project);
    }

    @Test
    public void shouldBuildTriggersOnNewPR() throws Exception {
        given(ghPullRequest.getNumber()).willReturn(1);
        
        GhprbTestUtil.triggerRunAndWait(10, trigger, project);

        assertThat(project.getBuilds().toArray().length).isEqualTo(1);
    }

    @Test
    public void shouldBuildTriggersOnUpdatingNewCommitsPR() throws Exception {
        // GIVEN
        given(commitPointer.getSha()).willReturn("sha").willReturn("newOne").willReturn("newOne");
        given(ghPullRequest.getComments()).willReturn(Lists.<GHIssueComment> newArrayList());
        
        given(ghPullRequest.getNumber()).willReturn(2).willReturn(2).willReturn(3).willReturn(3);
        
        GhprbTestUtil.triggerRunAndWait(10, trigger, project);

        assertThat(project.getBuilds().toArray().length).isEqualTo(2);
    }

    @Test
    public void shouldBuildTriggersOnUpdatingRetestMessagePR() throws Exception {
        // GIVEN
        GhprbTestUtil.triggerRunAndWait(10, trigger, project);
        
        given(comment.getBody()).willReturn("retest this please");
        given(comment.getUpdatedAt()).willReturn(new DateTime().plusDays(1).toDate());
        given(comment.getUser()).willReturn(ghUser);

        given(ghPullRequest.getComments()).willReturn(newArrayList(comment));
        given(ghPullRequest.getNumber()).willReturn(5).willReturn(5);

        
        GhprbTestUtil.triggerRunAndWait(10, trigger, project);
        assertThat(project.getBuilds().toArray().length).isEqualTo(2);
    }
    

    @Test
    public void shouldNotBuildDisabledBuild() throws Exception {
        // GIVEN
        
        given(commitPointer.getSha()).willReturn("sha");

        given(comment.getBody()).willReturn("retest this please");
        given(comment.getUpdatedAt()).willReturn(new DateTime().plusDays(1).toDate());
        given(comment.getUser()).willReturn(ghUser);
        given(ghPullRequest.getComments()).willReturn(newArrayList(comment));
        given(ghPullRequest.getNumber()).willReturn(5);
        
        project.disable();

        GhprbTestUtil.triggerRunAndWait(10, trigger, project);
        assertThat(project.getBuilds().toArray().length).isEqualTo(0);
        
        Mockito.verify(ghRepository, Mockito.times(0)).createCommitStatus(any(String.class), any(GHCommitState.class), any(String.class), any(String.class));
    }
    
    @Test
    public void triggerIsRemovedFromListWhenProjectChanges() {
        
    }

}
