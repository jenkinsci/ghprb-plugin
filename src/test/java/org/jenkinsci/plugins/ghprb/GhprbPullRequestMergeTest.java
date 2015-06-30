package org.jenkinsci.plugins.ghprb;

import com.coravy.hudson.plugins.github.GithubProjectProperty;
import hudson.model.*;
import org.jenkinsci.plugins.ghprb.GhprbTrigger.DescriptorImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.*;
import org.kohsuke.github.GHPullRequestCommitDetail.Commit;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class GhprbPullRequestMergeTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private FreeStyleProject project = mock(FreeStyleProject.class);
    private AbstractBuild<?, ?> build = mock(FreeStyleBuild.class);

    @Mock
    private GhprbPullRequest pullRequest;
    @Mock
    private GHPullRequest pr;

    @Mock
    private GitUser committer;
    @Mock
    private GHUser triggerSender;
    @Mock
    private GhprbCause cause;
    @Mock
    private Ghprb helper;
    @Mock
    private GhprbRepository repo;

    @Mock
    private StreamBuildListener listener;

    @Mock
    private ItemGroup<?> parent;

    private final String triggerPhrase = "ok to merge";
    private final String nonTriggerPhrase = "This phrase is not the trigger phrase";

    private final String adminList = "admin";

    private final String adminLogin = "admin";
    private final String nonAdminLogin = "nonadmin";

    private final String committerName = "committer";
    private final String nonCommitterName = "noncommitter";

    private final String mergeComment = "merge";

    private final Integer pullId = 1;

    private Map<String, Object> triggerValues;

    @Before
    public void beforeTest() throws Exception {
        triggerValues = new HashMap<String, Object>(10);
        triggerValues.put("adminlist", adminList);
        triggerValues.put("triggerPhrase", triggerPhrase);

        GhprbTrigger trigger = spy(GhprbTestUtil.getTrigger(triggerValues));

        ConcurrentMap<Integer, GhprbPullRequest> pulls = new ConcurrentHashMap<Integer, GhprbPullRequest>(1);
        pulls.put(pullId, pullRequest);
        Map<String, ConcurrentMap<Integer, GhprbPullRequest>> jobs = new HashMap<String, ConcurrentMap<Integer, GhprbPullRequest>>(1);
        jobs.put("project", pulls);

        GithubProjectProperty projectProperty = new GithubProjectProperty("https://github.com/jenkinsci/ghprb-plugin");
        DescriptorImpl descriptor = trigger.getDescriptor();

        PrintStream logger = mock(PrintStream.class);

        given(parent.getFullName()).willReturn("");

        given(project.getParent()).willReturn(parent);
        given(project.getTrigger(GhprbTrigger.class)).willReturn(trigger);
        given(project.getName()).willReturn("project");
        given(project.getProperty(GithubProjectProperty.class)).willReturn(projectProperty);
        given(project.isDisabled()).willReturn(false);

        given(build.getCause(GhprbCause.class)).willReturn(cause);
        given(build.getResult()).willReturn(Result.SUCCESS);
        given(build.getParent()).willCallRealMethod();

        given(pullRequest.getPullRequest()).willReturn(pr);

        given(cause.getPullID()).willReturn(pullId);
        given(cause.isMerged()).willReturn(true);
        given(cause.getTriggerSender()).willReturn(triggerSender);
        given(cause.getCommitAuthor()).willReturn(committer);

        given(listener.getLogger()).willReturn(logger);

        doNothing().when(repo).addComment(anyInt(), anyString());
        doNothing().when(logger).println();

        Field parentField = Run.class.getDeclaredField("project");
        parentField.setAccessible(true);
        parentField.set(build, project);

        Field jobsField = descriptor.getClass().getDeclaredField("jobs");
        jobsField.setAccessible(true);
        jobsField.set(descriptor, jobs);

        helper = spy(new Ghprb(project, trigger, pulls));
        trigger.setHelper(helper);
        given(helper.getRepository()).willReturn(repo);
        given(helper.isBotUser(any(GHUser.class))).willReturn(false);
    }

    @After
    public void afterClass() {

    }

    @SuppressWarnings("unchecked")
    private void setupConditions(String triggerLogin, String committerName, String comment) throws IOException {
        given(triggerSender.getLogin()).willReturn(triggerLogin);
        given(triggerSender.getName()).willReturn(committerName);
        given(committer.getName()).willReturn(this.committerName);

        PagedIterator<GHPullRequestCommitDetail> itr = Mockito.mock(PagedIterator.class);
        PagedIterable<GHPullRequestCommitDetail> pagedItr = Mockito.mock(PagedIterable.class);

        Commit commit = mock(Commit.class);
        GHPullRequestCommitDetail commitDetail = mock(GHPullRequestCommitDetail.class);

        given(pr.listCommits()).willReturn(pagedItr);

        given(pagedItr.iterator()).willReturn(itr);

        given(itr.hasNext()).willReturn(true, false);
        given(itr.next()).willReturn(commitDetail);

        given(commitDetail.getCommit()).willReturn(commit);
        given(commit.getCommitter()).willReturn(committer);

        given(cause.getCommentBody()).willReturn(comment);
    }

    private GhprbPullRequestMerge setupMerger(boolean onlyTriggerPhrase, boolean onlyAdminsMerge, boolean disallowOwnCode) {
        GhprbPullRequestMerge merger = spy(new GhprbPullRequestMerge(mergeComment, onlyTriggerPhrase, onlyAdminsMerge, disallowOwnCode));

        merger.setHelper(helper);

        return merger;
    }

    @Test
    public void testApproveMerge() throws Exception {

        boolean onlyTriggerPhrase = false;
        boolean onlyAdminsMerge = false;
        boolean disallowOwnCode = false;

        GhprbPullRequestMerge merger = setupMerger(onlyTriggerPhrase, onlyAdminsMerge, disallowOwnCode);

        setupConditions(nonAdminLogin, committerName, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);

        setupConditions(adminLogin, nonCommitterName, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);

        setupConditions(adminLogin, committerName, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);

        setupConditions(nonAdminLogin, nonCommitterName, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);

        setupConditions(nonAdminLogin, nonCommitterName, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);

        setupConditions(adminLogin, nonCommitterName, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);

        setupConditions(nonAdminLogin, nonCommitterName, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);

        setupConditions(adminLogin, committerName, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
    }

    @Test
    public void testAdminMerge() throws Exception {

        boolean onlyTriggerPhrase = false;
        boolean onlyAdminsMerge = true;
        boolean disallowOwnCode = false;

        GhprbPullRequestMerge merger = setupMerger(onlyTriggerPhrase, onlyAdminsMerge, disallowOwnCode);

        setupConditions(adminLogin, committerName, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);

        setupConditions(nonAdminLogin, committerName, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(false);
    }

    @Test
    public void testTriggerMerge() throws Exception {

        boolean onlyTriggerPhrase = true;
        boolean onlyAdminsMerge = false;
        boolean disallowOwnCode = false;

        GhprbPullRequestMerge merger = setupMerger(onlyTriggerPhrase, onlyAdminsMerge, disallowOwnCode);

        setupConditions(adminLogin, committerName, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);

        setupConditions(adminLogin, committerName, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(false);
    }

    @Test
    public void testOwnCodeMerge() throws Exception {

        boolean onlyTriggerPhrase = false;
        boolean onlyAdminsMerge = false;
        boolean disallowOwnCode = true;

        GhprbPullRequestMerge merger = setupMerger(onlyTriggerPhrase, onlyAdminsMerge, disallowOwnCode);

        setupConditions(adminLogin, nonCommitterName, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);

        setupConditions(adminLogin, committerName, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(false);
    }

    @Test
    public void testDenyMerge() throws Exception {

        boolean onlyTriggerPhrase = true;
        boolean onlyAdminsMerge = true;
        boolean disallowOwnCode = true;

        GhprbPullRequestMerge merger = setupMerger(onlyTriggerPhrase, onlyAdminsMerge, disallowOwnCode);

        setupConditions(nonAdminLogin, nonCommitterName, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(false);

        setupConditions(adminLogin, committerName, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(false);

        setupConditions(adminLogin, nonCommitterName, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(false);

        setupConditions(nonAdminLogin, nonCommitterName, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(false);

        setupConditions(nonAdminLogin, nonCommitterName, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);

        setupConditions(adminLogin, committerName, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);

        setupConditions(nonAdminLogin, committerName, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);

        setupConditions(adminLogin, nonCommitterName, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
    }

}
