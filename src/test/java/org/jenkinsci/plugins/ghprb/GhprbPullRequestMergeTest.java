package org.jenkinsci.plugins.ghprb;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.lang.reflect.Field;

import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.ItemGroup;
import hudson.model.StreamBuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.Result;

import org.jenkinsci.plugins.ghprb.GhprbTrigger.DescriptorImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHPullRequestCommitDetail.Commit;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitUser;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.coravy.hudson.plugins.github.GithubProjectProperty;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
    private GHUser prCreator;
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
    
    private final String committerEmail = "committer@mail.com";
    private final String nonCommitterEmail = "noncommitter@mail.com";

    private final String mergeComment = "merge";

    private final Integer pullId = 1;

    private Map<String, Object> triggerValues;

    @Before
    public void beforeTest() throws Exception {
        triggerValues = new HashMap<String, Object>(10);
        triggerValues.put("adminlist", adminList);
        triggerValues.put("triggerPhrase", triggerPhrase);
        
        GhprbTrigger trigger = GhprbTestUtil.getTrigger(triggerValues);

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
    private void setupConditions(String prUserLogin, String triggerLogin, String committerName, String committerEmail, String comment) throws IOException {
        given(triggerSender.getLogin()).willReturn(triggerLogin);
        given(triggerSender.getName()).willReturn(committerName);
        given(triggerSender.getEmail()).willReturn(committerEmail);
        given(committer.getName()).willReturn(this.committerName);
        
        given(prCreator.getLogin()).willReturn(prUserLogin);
        given(pr.getUser()).willReturn(prCreator);

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
    
    private void setupConditions(String triggerLogin, String committerName, String committerEmail, String comment) throws IOException {
        setupConditions(nonCommitterName, triggerLogin, committerName, committerEmail, comment);
    }
    
    private GhprbPullRequestMerge setupMerger(
            boolean onlyAdminsMerge, 
            boolean disallowOwnCode,
            boolean failOnNonMerge,
            boolean deleteOnMerge
            ) {

        GhprbPullRequestMerge merger = spy(new GhprbPullRequestMerge(
                mergeComment, 
                onlyAdminsMerge, 
                disallowOwnCode,
                failOnNonMerge,
                deleteOnMerge));

        merger.setHelper(helper);

        Mockito.reset(pr);
        return merger;
    }

    private GhprbPullRequestMerge setupMerger(
            boolean onlyAdminsMerge, 
            boolean disallowOwnCode) {
        return setupMerger(onlyAdminsMerge, disallowOwnCode, false, false);
    }

    @Test
    public void testApproveMerge() throws Exception {

        boolean onlyAdminsMerge = false;
        boolean disallowOwnCode = false;

        GhprbPullRequestMerge merger = setupMerger(onlyAdminsMerge, disallowOwnCode);

        setupConditions(nonAdminLogin, committerName, committerEmail, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(1)).merge(mergeComment);

        setupConditions(adminLogin, nonCommitterName, nonCommitterEmail, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(2)).merge(mergeComment);

        setupConditions(adminLogin, committerName, committerEmail, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(2)).merge(mergeComment);

        setupConditions(nonAdminLogin, nonCommitterName, nonCommitterEmail, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(3)).merge(mergeComment);

        setupConditions(nonAdminLogin, nonCommitterName, nonCommitterEmail, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(3)).merge(mergeComment);

        setupConditions(adminLogin, nonCommitterName, nonCommitterEmail, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(3)).merge(mergeComment);

        setupConditions(nonAdminLogin, nonCommitterName, nonCommitterEmail, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(3)).merge(mergeComment);

        setupConditions(adminLogin, committerName, committerEmail, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(4)).merge(mergeComment);
    }

    @Test
    public void testAdminMerge() throws Exception {

        boolean onlyAdminsMerge = true;
        boolean disallowOwnCode = false;

        GhprbPullRequestMerge merger = setupMerger(onlyAdminsMerge, disallowOwnCode);

        setupConditions(adminLogin, committerName, committerEmail, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(1)).merge(mergeComment);

        setupConditions(nonAdminLogin, committerName, committerEmail, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(false);
        verify(pr, times(1)).merge(mergeComment);
    }

    @Test
    public void testTriggerMerge() throws Exception {

        boolean onlyAdminsMerge = false;
        boolean disallowOwnCode = false;

        GhprbPullRequestMerge merger = setupMerger(onlyAdminsMerge, disallowOwnCode);

        setupConditions(adminLogin, committerName, committerEmail, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(1)).merge(mergeComment);

        setupConditions(adminLogin, committerName, committerEmail, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(1)).merge(mergeComment);
    }

    @Test
    public void testOwnCodeMerge() throws Exception {

        boolean onlyAdminsMerge = false;
        boolean disallowOwnCode = true;

        GhprbPullRequestMerge merger = setupMerger(onlyAdminsMerge, disallowOwnCode);

        setupConditions(adminLogin, nonCommitterName, nonCommitterEmail, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(1)).merge(mergeComment);

        setupConditions(adminLogin, committerName, committerEmail, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(false);
        verify(pr, times(1)).merge(mergeComment);
    }

    @Test
    public void testDenyMerge() throws Exception {

        boolean onlyAdminsMerge = true;
        boolean disallowOwnCode = true;

        GhprbPullRequestMerge merger = setupMerger(onlyAdminsMerge, disallowOwnCode);

        setupConditions(nonAdminLogin, nonAdminLogin, nonCommitterName, nonCommitterEmail, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(false);
        verify(pr, times(0)).merge(mergeComment);

        setupConditions(nonAdminLogin, adminLogin, committerName, committerEmail, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(false);
        verify(pr, times(0)).merge(mergeComment);

        setupConditions(nonAdminLogin, adminLogin, nonCommitterName, nonCommitterEmail, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(0)).merge(mergeComment);

        setupConditions(nonAdminLogin, nonAdminLogin, nonCommitterName, nonCommitterEmail, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(false);
        verify(pr, times(0)).merge(mergeComment);

        setupConditions(nonAdminLogin, nonAdminLogin, nonCommitterName, nonCommitterEmail, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(0)).merge(mergeComment);

        setupConditions(nonAdminLogin, adminLogin, committerName, committerEmail, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(0)).merge(mergeComment);

        setupConditions(nonAdminLogin, nonAdminLogin, committerName, committerEmail, nonTriggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(0)).merge(mergeComment);
        
        setupConditions(adminLogin, adminLogin, nonCommitterName, nonCommitterEmail, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(false);
        verify(pr, times(0)).merge(mergeComment);

        setupConditions(nonAdminLogin, adminLogin, nonCommitterName, nonCommitterEmail, triggerPhrase);
        assertThat(merger.perform(build, null, listener)).isEqualTo(true);
        verify(pr, times(1)).merge(mergeComment);
        
    }

}
