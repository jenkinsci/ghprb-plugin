package org.jenkinsci.plugins.ghprb;

import antlr.ANTLRException;
import com.coravy.hudson.plugins.github.GithubProjectProperty;
import com.google.common.collect.Lists;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.util.DefaultBuildChooser;
import net.sf.json.JSONObject;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import static com.google.common.collect.Lists.newArrayList;
import static org.fest.assertions.Assertions.assertThat;
import static org.kohsuke.github.GHIssueState.OPEN;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

@RunWith(MockitoJUnitRunner.class)
public class GhprbIT {

    private static final int INITIAL_RATE_LIMIT = 5000;

    private static final String GHPRB_PLUGIN_NAME = "ghprb";

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Mock
    private GhprbGitHub ghprbGitHub;
    @Mock
    private GitHub gitHub;
    @Mock
    private GHRepository ghRepository;
    @Mock
    private GHPullRequest ghPullRequest;
    @Mock
    private GHUser ghUser;
    @Mock
    private GHCommitPointer commitPointer;

    // Stubs
    private GHRateLimit ghRateLimit = new GHRateLimit();

    @Before
    public void beforeTest() throws IOException, ANTLRException {

        given(ghprbGitHub.get()).willReturn(gitHub);
        given(gitHub.getRateLimit()).willReturn(ghRateLimit);
        given(gitHub.getRepository(anyString())).willReturn(ghRepository);
        given(commitPointer.getRef()).willReturn("ref");
        given(ghRepository.getName()).willReturn("dropwizard");
        mockPR(ghPullRequest, commitPointer, new DateTime(), new DateTime().plusDays(1));
        given(ghRepository.getPullRequests(eq(OPEN)))
                .willReturn(newArrayList(ghPullRequest))
                .willReturn(newArrayList(ghPullRequest));

        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghUser.getEmail()).willReturn("email@email.com");
        given(ghUser.getLogin()).willReturn("user");
        ghRateLimit.remaining = INITIAL_RATE_LIMIT;
    }

    private void mockPR(GHPullRequest prToMock,
                        GHCommitPointer commitPointer,
                        DateTime... updatedDate) throws MalformedURLException {
        given(prToMock.getHead()).willReturn(commitPointer);
        given(prToMock.getBase()).willReturn(commitPointer);
        given(prToMock.getUrl()).willReturn(new URL("http://127.0.0.1"));
        if (updatedDate.length > 1) {
            given(prToMock.getUpdatedAt()).willReturn(updatedDate[0].toDate())
                    .willReturn(updatedDate[0].toDate())
                    .willReturn(updatedDate[1].toDate())
                    .willReturn(updatedDate[1].toDate())
                    .willReturn(updatedDate[1].toDate());
        } else {
            given(prToMock.getUpdatedAt()).willReturn(updatedDate[0].toDate());
        }
    }

    @Test
    public void shouldBuildTriggersOnNewPR() throws Exception {
        // GIVEN
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("PRJ");
        GhprbTrigger trigger = new GhprbTrigger(
                "user", "user", "", "*/1 * * * *", "retest this please", false, false, false, false, null
        );
        given(commitPointer.getSha()).willReturn("sha");
        JSONObject jsonObject = provideConfiguration();
        jenkinsRule.getPluginManager().getPlugin(GHPRB_PLUGIN_NAME).getPlugin().configure(null, jsonObject);
        project.addProperty(new GithubProjectProperty("https://github.com/user/dropwizard"));
         given(ghPullRequest.getNumber()).willReturn(1);

        // Creating spy on ghprb, configuring repo
        Ghprb ghprb = spy(trigger.createGhprb(project));
        doReturn(ghprbGitHub).when(ghprb).getGitHub();
        ghprb.getRepository().setMl(ghprb);

        // Configuring and adding Ghprb trigger
        project.addTrigger(trigger);
        project.getTriggers().keySet().iterator().next().configure(null, jsonObject);

        // Configuring Git SCM
        GitSCM scm = provideGitSCM();
        project.setScm(scm);

        trigger.start(project, true);
        trigger.setMl(ghprb);

        // THEN
        Thread.sleep(65000);
        assertThat(project.getBuilds().size()).isEqualTo(1);
    }

    @Test
    public void shouldBuildTriggersOnUpdatingNewCommitsPR() throws Exception {
        // GIVEN
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("PRJ");
        GhprbTrigger trigger = new GhprbTrigger(
                "user", "user", "", "*/1 * * * *", "retest this please", false, false, false, false, null
        );
        given(commitPointer.getSha()).willReturn("sha").willReturn("sha").willReturn("newOne").willReturn("newOne");
        given(ghPullRequest.getComments()).willReturn(Lists.<GHIssueComment>newArrayList());
        JSONObject jsonObject = provideConfiguration();
        jenkinsRule.getPluginManager().getPlugin(GHPRB_PLUGIN_NAME).getPlugin().configure(null, jsonObject);
        project.addProperty(new GithubProjectProperty("https://github.com/user/dropwizard"));
        given(ghPullRequest.getNumber()).willReturn(2).willReturn(2).willReturn(3).willReturn(3);
        Ghprb ghprb = spy(trigger.createGhprb(project));
        doReturn(ghprbGitHub).when(ghprb).getGitHub();
        trigger.start(project, true);
        trigger.setMl(ghprb);
        ghprb.getRepository().setMl(ghprb);
        project.addTrigger(trigger);
        project.getTriggers().keySet().iterator().next().configure(null, jsonObject);
        GitSCM scm = provideGitSCM();
        project.setScm(scm);

        // THEN
        Thread.sleep(130000);
        assertThat(project.getBuilds().size()).isEqualTo(2);
    }

    @Test
    public void shouldBuildTriggersOnUpdatingRetestMessagePR() throws Exception {
        // GIVEN
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("PRJ");
        GhprbTrigger trigger = new GhprbTrigger(
                "user", "user", "", "*/1 * * * *", "retest this please", false, false, false, false, null
        );

        given(commitPointer.getSha()).willReturn("sha");

        GHIssueComment comment = mock(GHIssueComment.class);
        given(comment.getBody()).willReturn("retest this please");
        given(comment.getUpdatedAt()).willReturn(new DateTime().plusDays(1).toDate());
        given(comment.getUser()).willReturn(ghUser);
        given(ghPullRequest.getComments()).willReturn(newArrayList(comment));
        given(ghPullRequest.getNumber()).willReturn(5).willReturn(5).willReturn(6).willReturn(6);
        JSONObject jsonObject = provideConfiguration();
        jenkinsRule.getPluginManager().getPlugin(GHPRB_PLUGIN_NAME).getPlugin().configure(null, jsonObject);
        project.addProperty(new GithubProjectProperty("https://github.com/user/dropwizard"));

        Ghprb ghprb = spy(trigger.createGhprb(project));
        doReturn(ghprbGitHub).when(ghprb).getGitHub();
        trigger.start(project, true);
        trigger.setMl(ghprb);
        ghprb.getRepository().setMl(ghprb);
        project.addTrigger(trigger);
        project.getTriggers().keySet().iterator().next().configure(null, jsonObject);
        GitSCM scm = provideGitSCM();
        project.setScm(scm);

        // THEN
        Thread.sleep(130000);
        assertThat(project.getBuilds().size()).isEqualTo(2);
    }

    // Utility

    private GitSCM provideGitSCM() {
        return new GitSCM(
                "",
                newArrayList(new UserRemoteConfig("https://github.com/user/dropwizard", "", "+refs/pull/*:refs/remotes/origin/pr/*")),
                newArrayList(new BranchSpec("${sha1}")),
                null,
                false,
                null,
                false,
                false,
                new DefaultBuildChooser(),
                null,
                "",
                false,
                "",
                "",
                "",
                "",
                "",
                false,
                false,
                false,
                false,
                "",
                "",
                false,
                "",
                false
        );
    }

    private JSONObject provideConfiguration() {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("serverAPIUrl", "https://api.github.com");
        jsonObject.put("username", "user");
        jsonObject.put("password", "1111");
        jsonObject.put("accessToken", "accessToken");
        jsonObject.put("adminlist", "user");
        jsonObject.put("publishedURL", "");
        jsonObject.put("requestForTestingPhrase", "test this");
        jsonObject.put("whitelistPhrase", "");
        jsonObject.put("okToTestPhrase", "ok to test");
        jsonObject.put("retestPhrase", "retest this please");
        jsonObject.put("cron", "*/1 * * * *");
        jsonObject.put("useComments", "true");
        jsonObject.put("logExcerptLines", "0");
        jsonObject.put("unstableAs", "");
        jsonObject.put("testMode", "true");
        jsonObject.put("autoCloseFailedPullRequests", "false");
        jsonObject.put("msgSuccess", "Success");
        jsonObject.put("msgFailure", "Failure");

        return jsonObject;
    }
}
