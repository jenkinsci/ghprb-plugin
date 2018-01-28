package org.jenkinsci.plugins.ghprb.extensions.status;

import org.jenkinsci.plugins.ghprb.GhprbPullRequest;
import org.jenkinsci.plugins.ghprb.GhprbTestUtil;
import org.jenkinsci.plugins.ghprb.GhprbTrigger;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildResultMessage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.ArrayList;
// Needed for testing commit context
/*
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.FreeStyleProject;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbProjectExtension;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildResultMessage;
import java.util.ArrayList;
import java.util.List;
*/

@RunWith(MockitoJUnitRunner.class)
public class GhprbSimpleStatusTest extends org.jenkinsci.plugins.ghprb.extensions.GhprbExtension {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Mock
    private GHRepository ghRepository;

    @Mock
    private GhprbPullRequest ghprbPullRequest;

    private GhprbTrigger trigger;

    //private FreeStyleProject project;

    @Before
    public void setUp() throws Exception {
        trigger = GhprbTestUtil.getTrigger(null);
    }

    @Test
    public void testMergedMessage() throws Exception {
        String mergedMessage = "Build triggered. sha1 is merged.";
        given(ghprbPullRequest.getHead()).willReturn("sha");
        given(ghprbPullRequest.isMergeable()).willReturn(true);

        GhprbSimpleStatus status = spy(new GhprbSimpleStatus("default"));
        status.onBuildTriggered(trigger.getActualProject(), "sha", true, 1, ghRepository);

        verify(ghRepository).createCommitStatus(eq("sha"), eq(GHCommitState.PENDING), eq(""), eq(mergedMessage), eq("default"));
        verifyNoMoreInteractions(ghRepository);

        verifyNoMoreInteractions(ghprbPullRequest);
    }

    @Test
    public void testMergeConflictMessage() throws Exception {
        String mergedMessage = "Build triggered. sha1 is original commit.";
        given(ghprbPullRequest.getHead()).willReturn("sha");
        given(ghprbPullRequest.isMergeable()).willReturn(false);

        GhprbSimpleStatus status = spy(new GhprbSimpleStatus("default"));
        status.onBuildTriggered(trigger.getActualProject(), "sha", false, 1, ghRepository);

        verify(ghRepository).createCommitStatus(eq("sha"), eq(GHCommitState.PENDING), eq(""), eq(mergedMessage), eq("default"));
        verifyNoMoreInteractions(ghRepository);

        verifyNoMoreInteractions(ghprbPullRequest);
    }

    @Test
    public void testDoesNotSendEmptyContext() throws Exception {
        String mergedMessage = "Build triggered. sha1 is original commit.";
        given(ghprbPullRequest.getHead()).willReturn("sha");
        given(ghprbPullRequest.isMergeable()).willReturn(false);

        GhprbSimpleStatus status = spy(new GhprbSimpleStatus(""));
        status.onBuildTriggered(trigger.getActualProject(), "sha", false, 1, ghRepository);

        verify(ghRepository).createCommitStatus(eq("sha"), eq(GHCommitState.PENDING), eq(""), eq(mergedMessage), isNull(String.class));
        verifyNoMoreInteractions(ghRepository);

        verifyNoMoreInteractions(ghprbPullRequest);
    }

    /*
    public static final GhprbSimpleStatusDescriptor TEST_DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends GhprbSimpleStatusDescriptor
                                             implements GhprbGlobalExtension, GhprbProjectExtension {

        @Override
        public String getDisplayName() {
            return "Update commit status during build";
        }

        public String getTriggeredStatusDefault(GhprbSimpleStatus local) {
            return "Build triggered. sha1 is original commit.";
        }

        public String getStatusUrlDefault(GhprbSimpleStatus local) {
            return "http://someserver.com";
        }

        public String getStartedStatusDefault(GhprbSimpleStatus local) {
            return "getStartedStatus";
        }

        public Boolean getAddTestResultsDefault(GhprbSimpleStatus local) {
            return true;
        }

        public List<GhprbBuildResultMessage> getCompletedStatusDefault(GhprbSimpleStatus local) {
            return new ArrayList<GhprbBuildResultMessage>(0);
        }

        public String getCommitStatusContextDefault(GhprbSimpleStatus local) {
            return "testing context";
        }

        public Boolean getShowMatrixStatusDefault(GhprbSimpleStatus local){
            return true;
        }

        public boolean addIfMissing() {
            return false;
        }

    } */

    @Test
    public void testUseDefaultContext() throws Exception {
        String mergedMessage = "Build triggered. sha1 is original commit.";
        String statusUrl = "http://someserver.com";
        String context = "testing context";
        given(ghprbPullRequest.getHead()).willReturn("sha");
        given(ghprbPullRequest.isMergeable()).willReturn(false);

        GhprbSimpleStatus globalStatus =
            new GhprbSimpleStatus(true, context, statusUrl, "test1", "test2", false, new ArrayList<GhprbBuildResultMessage>(0));
        GhprbTrigger.getDscp().getExtensions().add(globalStatus);
        GhprbTrigger localTrigger = GhprbTestUtil.getTrigger(null);

        GhprbSimpleStatus status = new GhprbSimpleStatus("");
        GhprbSimpleStatus statusSpy = spy(status);

        statusSpy.onBuildTriggered(trigger.getActualProject(), "sha", false, 1, ghRepository);
        //verify(ghRepository).createCommitStatus(eq("sha"), eq(GHCommitState.PENDING), eq(statusUrl), eq(mergedMessage), eq(context));
        verify(ghRepository).createCommitStatus(eq("sha"), eq(GHCommitState.PENDING), eq(""), eq(mergedMessage), isNull(String.class));

        //statusSpy.onBuildStart(<what goes here>, <can listener be null>, ghRepository);
        //verify(ghRepository).createCommitStatus(eq("sha"), eq(GHCommitState.PENDING), eq(statusUrl), eq(mergedMessage), eq(context));

        verifyNoMoreInteractions(ghRepository);

        verifyNoMoreInteractions(ghprbPullRequest);
    }
}
