package org.jenkinsci.plugins.ghprb;

import com.google.common.collect.Lists;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.Run;
import net.sf.json.JSONObject;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.stapler.BindInterceptor;
import org.kohsuke.stapler.RequestImpl;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    public void setUp() throws Exception {
        req = Mockito.mock(RequestImpl.class);

        given(req.bindJSON(any(Class.class), any(JSONObject.class))).willCallRealMethod();
        given(req.bindJSON(any(Class.class), any(Class.class), any(JSONObject.class))).willCallRealMethod();
        given(req.setBindInterceptor(any(BindInterceptor.class))).willCallRealMethod();
        given(req.setBindListener(any(BindInterceptor.class))).willCallRealMethod();
        given(req.getBindInterceptor()).willReturn(BindInterceptor.NOOP);

        req.setBindListener(BindInterceptor.NOOP);
        req.setBindInterceptor(BindInterceptor.NOOP);
        req.setBindInterceptor(BindInterceptor.NOOP);
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
        given(ghPullRequest.getComments()).willReturn(Lists.<GHIssueComment>newArrayList());

        given(ghPullRequest.getNumber()).willReturn(2).willReturn(2).willReturn(3).willReturn(3);

        // Also verify that uniquely different builds do not get commingled
        project.setQuietPeriod(4);

        GhprbTestUtil.triggerRunsAtOnceThenWait(10, trigger, project);

        assertThat(project.getBuilds().toArray().length).isEqualTo(2);
    }


    @Test
    public void shouldBuildTriggersOnUpdatingRetestMessagePR() throws Exception {
        // GIVEN
        given(ghPullRequest.getCreatedAt()).willReturn(new DateTime().toDate());
        GhprbTestUtil.triggerRunAndWait(10, trigger, project);
        assertThat(project.getBuilds().toArray().length).isEqualTo(1);

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

        Mockito.verify(ghRepository, Mockito.times(0))
                .createCommitStatus(any(String.class), any(GHCommitState.class), any(String.class), any(String.class));
    }

    @Test
    public void shouldContainParamsWhenDone() throws Exception {
        // GIVEN
        // This test confirms env vars are populated. It only tests one env var
        // under the premise that if one is populated then all are populated.

        String canaryVar = "ghprbActualCommit";

        given(ghPullRequest.getNumber()).willReturn(1);

        GhprbTestUtil.triggerRunAndWait(10, trigger, project);

        assertThat(project.getBuilds().toArray().length).isEqualTo(1);

        hudson.util.RunList builds = project.getBuilds();
        Run build = builds.getLastBuild();
        Map envVars = build.getEnvVars();

        // Ensure that the var is contained in the environment
        assertThat(envVars.get(canaryVar)).isNotNull();


        ArrayList<String> paramsList = newArrayList();
        List<? extends Action> actions = build.getAllActions();
        for (Action a : actions) { // SECURITY-170
            if (a instanceof GhprbParametersAction) {
                List<ParameterValue> parameterValues = ((GhprbParametersAction) a).getParameters();
                for (ParameterValue pv : parameterValues) {
                    paramsList.add(pv.getName());
                }
            }
        }

        // Ensure that the var is contained in the parameters
        assertThat(paramsList).contains(canaryVar);

    }

    @Test
    public void triggerIsRemovedFromListWhenProjectChanges() {

    }

}
