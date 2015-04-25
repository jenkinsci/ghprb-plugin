package org.jenkinsci.plugins.ghprb;

import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

import net.sf.json.JSONObject;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.coravy.hudson.plugins.github.GithubProjectProperty;

import static com.google.common.collect.Lists.newArrayList;
import static org.fest.assertions.Assertions.assertThat;
import static org.kohsuke.github.GHIssueState.OPEN;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class GhprbRootActionTest {
    

    @Mock
    protected GHCommitPointer commitPointer;
    @Mock
    protected GHPullRequest ghPullRequest;
    @Mock
    protected GhprbGitHub ghprbGitHub;
    @Mock
    protected GHRepository ghRepository;
    @Mock
    protected GHUser ghUser;
    
    protected GitHub gitHub;
    // Stubs
    protected GHRateLimit ghRateLimit = new GHRateLimit();
    

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Mock
    private StaplerRequest req;

    private BufferedReader br;

    @Before
    public void setup() throws Exception {
        gitHub = spy(GitHub.connectAnonymously());
        given(ghprbGitHub.get()).willReturn(gitHub);
        given(gitHub.getRateLimit()).willReturn(ghRateLimit);
        doReturn(ghRepository).when(gitHub).getRepository(anyString());
        given(commitPointer.getRef()).willReturn("ref");
        given(ghRepository.getName()).willReturn("dropwizard");

        GhprbTestUtil.mockPR(ghPullRequest, commitPointer, new DateTime(), new DateTime().plusDays(1));

        given(ghRepository.getPullRequests(eq(OPEN))).willReturn(newArrayList(ghPullRequest)).willReturn(newArrayList(ghPullRequest));

        given(ghPullRequest.getUser()).willReturn(ghUser);
        given(ghUser.getEmail()).willReturn("email@email.com");
        given(ghUser.getLogin()).willReturn("user");

        ghRateLimit.remaining = GhprbTestUtil.INITIAL_RATE_LIMIT;

        GhprbTestUtil.mockCommitList(ghPullRequest);
        ghprbGitHub.setGitHub(gitHub);
    }

    @Test
    public void testUrlEncoded() throws Exception {
        // GIVEN
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("testUrlEncoded");
        GhprbTrigger trigger = GhprbTestUtil.getTrigger(null);
        given(commitPointer.getSha()).willReturn("sha1");
        JSONObject jsonObject = GhprbTestUtil.provideConfiguration();
        GhprbTrigger.getDscp().configure(null, jsonObject);
        project.addProperty(new GithubProjectProperty("https://github.com/user/dropwizard"));
        given(ghPullRequest.getNumber()).willReturn(1);
        Ghprb ghprb = spy(trigger.createGhprb(project));
        doReturn(ghprbGitHub).when(ghprb).getGitHub();
        trigger.start(project, true);
        trigger.setHelper(ghprb);
        ghprb.getRepository().setHelper(ghprb);
        project.addTrigger(trigger);
        project.getTriggers().keySet().iterator().next().configure(null, jsonObject);
        GitSCM scm = GhprbTestUtil.provideGitSCM();
        project.setScm(scm);

        GhprbTestUtil.triggerRunAndWait(10, trigger, project);

        assertThat(project.getBuilds().toArray().length).isEqualTo(1);
        
        GhprbTrigger.getDscp().setGitHub(ghprbGitHub);
        doReturn(gitHub).when(ghprbGitHub).get();
        
        given(req.getContentType()).willReturn("application/x-www-form-urlencoded");
        given(req.getParameter("payload")).willReturn(payload);
        given(req.getHeader("X-GitHub-Event")).willReturn("issue_comment");

        GhprbRootAction ra = new GhprbRootAction();
        ra.doIndex(req, null);
        GhprbTestUtil.waitForBuildsToFinish(project);
        
        assertThat(project.getBuilds().toArray().length).isEqualTo(2);
    }
    
    @Test
    public void disabledJobsDontBuild() throws Exception {
        // GIVEN
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("disabledJobsDontBuild");
        GhprbTrigger trigger = GhprbTestUtil.getTrigger(null);
        given(commitPointer.getSha()).willReturn("sha1");
        JSONObject jsonObject = GhprbTestUtil.provideConfiguration();
        GhprbTrigger.getDscp().configure(null, jsonObject);
        project.addProperty(new GithubProjectProperty("https://github.com/user/dropwizard"));
        given(ghPullRequest.getNumber()).willReturn(1);
        Ghprb ghprb = spy(trigger.createGhprb(project));
        doReturn(ghprbGitHub).when(ghprb).getGitHub();
        trigger.start(project, true);
        trigger.setHelper(ghprb);
        ghprb.getRepository().setHelper(ghprb);
        project.addTrigger(trigger);
        project.getTriggers().keySet().iterator().next().configure(null, jsonObject);
        GitSCM scm = GhprbTestUtil.provideGitSCM();
        project.setScm(scm);

        GhprbTestUtil.triggerRunAndWait(10, trigger, project);

        assertThat(project.getBuilds().toArray().length).isEqualTo(1);
        
        project.disable();

        GhprbTrigger.getDscp().setGitHub(ghprbGitHub);
        doReturn(gitHub).when(ghprbGitHub).get();
        
        given(req.getContentType()).willReturn("application/x-www-form-urlencoded");
        given(req.getParameter("payload")).willReturn(payload);
        given(req.getHeader("X-GitHub-Event")).willReturn("issue_comment");

        GhprbRootAction ra = new GhprbRootAction();
        ra.doIndex(req, null);
        GhprbTestUtil.waitForBuildsToFinish(project);
        
        assertThat(project.getBuilds().toArray().length).isEqualTo(1);
    }

    @Test
    public void testJson() throws Exception {
        given(req.getContentType()).willReturn("application/json");
        given(req.getHeader("X-GitHub-Event")).willReturn("issue_comment");

        // convert String into InputStream
        InputStream is = new ByteArrayInputStream(payload.getBytes());

        // read it with BufferedReader
        br = spy(new BufferedReader(new InputStreamReader(is)));

        given(req.getReader()).willReturn(br);

        GhprbRootAction ra = new GhprbRootAction();
        ra.doIndex(req, null);

        verify(br, times(1)).close();
    }
    

    private final String payload = "{"
    + "  \"action\": \"created\","
    + "  \"issue\": {"
    + "    \"url\": \"https://api.github.com/repos/user/dropwizard/issues/1\","
    + "    \"labels_url\": \"https://api.github.com/repos/user/dropwizard/issues/1/labels{/name}\","
    + "    \"comments_url\": \"https://api.github.com/repos/user/dropwizard/issues/1/comments\","
    + "    \"events_url\": \"https://api.github.com/repos/user/dropwizard/issues/1/events\","
    + "    \"html_url\": \"https://github.com/user/dropwizard/pull/1\","
    + "    \"id\": 44444444,"
    + "    \"number\": 1,"
    + "    \"title\": \"Adding version command\","
    + "    \"user\": {"
    + "      \"login\": \"user\","
    + "      \"id\": 444444,"
    + "      \"avatar_url\": \"https://avatars.githubusercontent.com/u/444444?v=3\","
    + "      \"gravatar_id\": \"\","
    + "      \"url\": \"https://api.github.com/users/user\","
    + "      \"html_url\": \"https://github.com/user\","
    + "      \"followers_url\": \"https://api.github.com/users/user/followers\","
    + "      \"following_url\": \"https://api.github.com/users/user/following{/other_user}\","
    + "      \"gists_url\": \"https://api.github.com/users/user/gists{/gist_id}\","
    + "      \"starred_url\": \"https://api.github.com/users/user/starred{/owner}{/repo}\","
    + "      \"subscriptions_url\": \"https://api.github.com/users/user/subscriptions\","
    + "      \"organizations_url\": \"https://api.github.com/users/user/orgs\","
    + "      \"repos_url\": \"https://api.github.com/users/user/repos\","
    + "      \"events_url\": \"https://api.github.com/users/user/events{/privacy}\","
    + "      \"received_events_url\": \"https://api.github.com/users/user/received_events\","
    + "      \"type\": \"User\","
    + "      \"site_admin\": false"
    + "    },"
    + "    \"labels\": ["
    + ""
    + "    ],"
    + "    \"state\": \"open\","
    + "    \"locked\": false,"
    + "    \"assignee\": null,"
    + "    \"milestone\": null,"
    + "    \"comments\": 2,"
    + "    \"created_at\": \"2014-09-22T20:05:14Z\","
    + "    \"updated_at\": \"2015-01-14T14:50:53Z\","
    + "    \"closed_at\": null,"
    + "    \"pull_request\": {"
    + "      \"url\": \"https://api.github.com/repos/user/dropwizard/pulls/1\","
    + "      \"html_url\": \"https://github.com/user/dropwizard/pull/1\","
    + "      \"diff_url\": \"https://github.com/user/dropwizard/pull/1.diff\","
    + "      \"patch_url\": \"https://github.com/user/dropwizard/pull/1.patch\""
    + "    },"
    + "    \"body\": \"\""
    + "  },"
    + "  \"comment\": {"
    + "    \"url\": \"https://api.github.com/repos/user/dropwizard/issues/comments/44444444\","
    + "    \"html_url\": \"https://github.com/user/dropwizard/pull/1#issuecomment-44444444\","
    + "    \"issue_url\": \"https://api.github.com/repos/user/dropwizard/issues/1\","
    + "    \"id\": 44444444,"
    + "    \"user\": {"
    + "      \"login\": \"user\","
    + "      \"id\": 444444,"
    + "      \"avatar_url\": \"https://avatars.githubusercontent.com/u/444444?v=3\","
    + "      \"gravatar_id\": \"\","
    + "      \"url\": \"https://api.github.com/users/user\","
    + "      \"html_url\": \"https://github.com/user\","
    + "      \"followers_url\": \"https://api.github.com/users/user/followers\","
    + "      \"following_url\": \"https://api.github.com/users/user/following{/other_user}\","
    + "      \"gists_url\": \"https://api.github.com/users/user/gists{/gist_id}\","
    + "      \"starred_url\": \"https://api.github.com/users/user/starred{/owner}{/repo}\","
    + "      \"subscriptions_url\": \"https://api.github.com/users/user/subscriptions\","
    + "      \"organizations_url\": \"https://api.github.com/users/user/orgs\","
    + "      \"repos_url\": \"https://api.github.com/users/user/repos\","
    + "      \"events_url\": \"https://api.github.com/users/user/events{/privacy}\","
    + "      \"received_events_url\": \"https://api.github.com/users/user/received_events\","
    + "      \"type\": \"User\","
    + "      \"site_admin\": false"
    + "    },"
    + "    \"created_at\": \"2015-01-14T14:50:53Z\","
    + "    \"updated_at\": \"2015-01-14T14:50:53Z\","
    + "    \"body\": \"retest this please\""
    + "  },"
    + "  \"repository\": {"
    + "    \"id\": 44444444,"
    + "    \"name\": \"Testing\","
    + "    \"full_name\": \"user/dropwizard\","
    + "    \"owner\": {"
    + "      \"login\": \"user\","
    + "      \"id\": 444444,"
    + "      \"avatar_url\": \"https://avatars.githubusercontent.com/u/444444?v=3\","
    + "      \"gravatar_id\": \"\","
    + "      \"url\": \"https://api.github.com/users/user\","
    + "      \"html_url\": \"https://github.com/user\","
    + "      \"followers_url\": \"https://api.github.com/users/user/followers\","
    + "      \"following_url\": \"https://api.github.com/users/user/following{/other_user}\","
    + "      \"gists_url\": \"https://api.github.com/users/user/gists{/gist_id}\","
    + "      \"starred_url\": \"https://api.github.com/users/user/starred{/owner}{/repo}\","
    + "      \"subscriptions_url\": \"https://api.github.com/users/user/subscriptions\","
    + "      \"organizations_url\": \"https://api.github.com/users/user/orgs\","
    + "      \"repos_url\": \"https://api.github.com/users/user/repos\","
    + "      \"events_url\": \"https://api.github.com/users/user/events{/privacy}\","
    + "      \"received_events_url\": \"https://api.github.com/users/user/received_events\","
    + "      \"type\": \"User\","
    + "      \"site_admin\": false"
    + "    },"
    + "    \"private\": false,"
    + "    \"html_url\": \"https://github.com/user/dropwizard\","
    + "    \"description\": \"\","
    + "    \"fork\": false,"
    + "    \"url\": \"https://api.github.com/repos/user/dropwizard\","
    + "    \"forks_url\": \"https://api.github.com/repos/user/dropwizard/forks\","
    + "    \"keys_url\": \"https://api.github.com/repos/user/dropwizard/keys{/key_id}\","
    + "    \"collaborators_url\": \"https://api.github.com/repos/user/dropwizard/collaborators{/collaborator}\","
    + "    \"teams_url\": \"https://api.github.com/repos/user/dropwizard/teams\","
    + "    \"hooks_url\": \"https://api.github.com/repos/user/dropwizard/hooks\","
    + "    \"issue_events_url\": \"https://api.github.com/repos/user/dropwizard/issues/events{/number}\","
    + "    \"events_url\": \"https://api.github.com/repos/user/dropwizard/events\","
    + "    \"assignees_url\": \"https://api.github.com/repos/user/dropwizard/assignees{/user}\","
    + "    \"branches_url\": \"https://api.github.com/repos/user/dropwizard/branches{/branch}\","
    + "    \"tags_url\": \"https://api.github.com/repos/user/dropwizard/tags\","
    + "    \"blobs_url\": \"https://api.github.com/repos/user/dropwizard/git/blobs{/sha}\","
    + "    \"git_tags_url\": \"https://api.github.com/repos/user/dropwizard/git/tags{/sha}\","
    + "    \"git_refs_url\": \"https://api.github.com/repos/user/dropwizard/git/refs{/sha}\","
    + "    \"trees_url\": \"https://api.github.com/repos/user/dropwizard/git/trees{/sha}\","
    + "    \"statuses_url\": \"https://api.github.com/repos/user/dropwizard/statuses/{sha}\","
    + "    \"languages_url\": \"https://api.github.com/repos/user/dropwizard/languages\","
    + "    \"stargazers_url\": \"https://api.github.com/repos/user/dropwizard/stargazers\","
    + "    \"contributors_url\": \"https://api.github.com/repos/user/dropwizard/contributors\","
    + "    \"subscribers_url\": \"https://api.github.com/repos/user/dropwizard/subscribers\","
    + "    \"subscription_url\": \"https://api.github.com/repos/user/dropwizard/subscription\","
    + "    \"commits_url\": \"https://api.github.com/repos/user/dropwizard/commits{/sha}\","
    + "    \"git_commits_url\": \"https://api.github.com/repos/user/dropwizard/git/commits{/sha}\","
    + "    \"comments_url\": \"https://api.github.com/repos/user/dropwizard/comments{/number}\","
    + "    \"issue_comment_url\": \"https://api.github.com/repos/user/dropwizard/issues/comments/{number}\","
    + "    \"contents_url\": \"https://api.github.com/repos/user/dropwizard/contents/{+path}\","
    + "    \"compare_url\": \"https://api.github.com/repos/user/dropwizard/compare/{base}...{head}\","
    + "    \"merges_url\": \"https://api.github.com/repos/user/dropwizard/merges\","
    + "    \"archive_url\": \"https://api.github.com/repos/user/dropwizard/{archive_format}{/ref}\","
    + "    \"downloads_url\": \"https://api.github.com/repos/user/dropwizard/downloads\","
    + "    \"issues_url\": \"https://api.github.com/repos/user/dropwizard/issues{/number}\","
    + "    \"pulls_url\": \"https://api.github.com/repos/user/dropwizard/pulls{/number}\","
    + "    \"milestones_url\": \"https://api.github.com/repos/user/dropwizard/milestones{/number}\","
    + "    \"notifications_url\": \"https://api.github.com/repos/user/dropwizard/notifications{?since,all,participating}\","
    + "    \"labels_url\": \"https://api.github.com/repos/user/dropwizard/labels{/name}\","
    + "    \"releases_url\": \"https://api.github.com/repos/user/dropwizard/releases{/id}\","
    + "    \"created_at\": \"2014-07-23T15:52:14Z\","
    + "    \"updated_at\": \"2014-09-04T21:10:34Z\","
    + "    \"pushed_at\": \"2015-01-14T14:13:58Z\","
    + "    \"git_url\": \"git://github.com/user/dropwizard.git\","
    + "    \"ssh_url\": \"git@github.com:user/dropwizard.git\","
    + "    \"clone_url\": \"https://github.com/user/dropwizard.git\","
    + "    \"svn_url\": \"https://github.com/user/dropwizard\","
    + "    \"homepage\": null,"
    + "    \"size\": 20028,"
    + "    \"stargazers_count\": 0,"
    + "    \"watchers_count\": 0,"
    + "    \"language\": \"JavaScript\","
    + "    \"has_issues\": true,"
    + "    \"has_downloads\": true,"
    + "    \"has_wiki\": true,"
    + "    \"has_pages\": false,"
    + "    \"forks_count\": 0,"
    + "    \"mirror_url\": null,"
    + "    \"open_issues_count\": 1,"
    + "    \"forks\": 0,"
    + "    \"open_issues\": 1,"
    + "    \"watchers\": 0,"
    + "    \"default_branch\": \"master\""
    + "  },"
    + "  \"sender\": {"
    + "    \"login\": \"user\","
    + "    \"id\": 444444,"
    + "    \"avatar_url\": \"https://avatars.githubusercontent.com/u/444444?v=3\","
    + "    \"gravatar_id\": \"\","
    + "    \"url\": \"https://api.github.com/users/user\","
    + "    \"html_url\": \"https://github.com/user\","
    + "    \"followers_url\": \"https://api.github.com/users/user/followers\","
    + "    \"following_url\": \"https://api.github.com/users/user/following{/other_user}\","
    + "    \"gists_url\": \"https://api.github.com/users/user/gists{/gist_id}\","
    + "    \"starred_url\": \"https://api.github.com/users/user/starred{/owner}{/repo}\","
    + "    \"subscriptions_url\": \"https://api.github.com/users/user/subscriptions\","
    + "    \"organizations_url\": \"https://api.github.com/users/user/orgs\","
    + "    \"repos_url\": \"https://api.github.com/users/user/repos\","
    + "    \"events_url\": \"https://api.github.com/users/user/events{/privacy}\","
    + "    \"received_events_url\": \"https://api.github.com/users/user/received_events\","
    + "    \"type\": \"User\","
    + "    \"site_admin\": false"
    + "  }"
    + "}";

}
