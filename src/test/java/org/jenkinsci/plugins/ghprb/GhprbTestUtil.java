/**
 * Copyright (c) 2000-2014 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package org.jenkinsci.plugins.ghprb;

import static com.google.common.collect.Lists.newArrayList;
import static org.mockito.BDDMockito.given;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.mockito.Mockito;

import antlr.ANTLRException;
import hudson.model.AbstractProject;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import net.sf.json.JSONObject;

public class GhprbTestUtil {

    public static final int INITIAL_RATE_LIMIT = 5000;
    public static final String GHPRB_PLUGIN_NAME = "ghprb";
    public static final String PAYLOAD = "{" +
            "  \"action\": \"created\"," +
            "  \"issue\": {" +
            "    \"url\": \"https://api.github.com/repos/user/dropwizard/issues/1\"," +
            "    \"labels_url\": \"https://api.github.com/repos/user/dropwizard/issues/1/labels{/name}\"," +
            "    \"comments_url\": \"https://api.github.com/repos/user/dropwizard/issues/1/comments\"," +
            "    \"events_url\": \"https://api.github.com/repos/user/dropwizard/issues/1/events\"," +
            "    \"html_url\": \"https://github.com/user/dropwizard/pull/1\"," +
            "    \"id\": 44444444," +
            "    \"number\": 1," +
            "    \"title\": \"Adding version command\"," +
            "    \"user\": {" +
            "      \"login\": \"user\"," +
            "      \"id\": 444444," +
            "      \"avatar_url\": \"https://avatars.githubusercontent.com/u/444444?v=3\"," +
            "      \"gravatar_id\": \"\"," +
            "      \"url\": \"https://api.github.com/users/user\"," +
            "      \"html_url\": \"https://github.com/user\"," +
            "      \"followers_url\": \"https://api.github.com/users/user/followers\"," +
            "      \"following_url\": \"https://api.github.com/users/user/following{/other_user}\"," +
            "      \"gists_url\": \"https://api.github.com/users/user/gists{/gist_id}\"," +
            "      \"starred_url\": \"https://api.github.com/users/user/starred{/owner}{/repo}\"," +
            "      \"subscriptions_url\": \"https://api.github.com/users/user/subscriptions\"," +
            "      \"organizations_url\": \"https://api.github.com/users/user/orgs\"," +
            "      \"repos_url\": \"https://api.github.com/users/user/repos\"," +
            "      \"events_url\": \"https://api.github.com/users/user/events{/privacy}\"," +
            "      \"received_events_url\": \"https://api.github.com/users/user/received_events\"," +
            "      \"type\": \"User\"," +
            "      \"site_admin\": false" +
            "    }," +
            "    \"labels\": [" +
            "" +
            "    ]," +
            "    \"state\": \"open\"," +
            "    \"locked\": false," +
            "    \"assignee\": null," +
            "    \"milestone\": null," +
            "    \"comments\": 2," +
            "    \"created_at\": \"2014-09-22T20:05:14Z\"," +
            "    \"updated_at\": \"2015-01-14T14:50:53Z\"," +
            "    \"closed_at\": null," +
            "    \"pull_request\": {" +
            "      \"url\": \"https://api.github.com/repos/user/dropwizard/pulls/1\"," +
            "      \"html_url\": \"https://github.com/user/dropwizard/pull/1\"," +
            "      \"diff_url\": \"https://github.com/user/dropwizard/pull/1.diff\"," +
            "      \"patch_url\": \"https://github.com/user/dropwizard/pull/1.patch\"" +
            "    }," +
            "    \"body\": \"\"" +
            "  }," +
            "  \"comment\": {" +
            "    \"url\": \"https://api.github.com/repos/user/dropwizard/issues/comments/44444444\"," +
            "    \"html_url\": \"https://github.com/user/dropwizard/pull/1#issuecomment-44444444\"," +
            "    \"issue_url\": \"https://api.github.com/repos/user/dropwizard/issues/1\"," +
            "    \"id\": 44444444," +
            "    \"user\": {" +
            "      \"login\": \"user\"," +
            "      \"id\": 444444," +
            "      \"avatar_url\": \"https://avatars.githubusercontent.com/u/444444?v=3\"," +
            "      \"gravatar_id\": \"\"," +
            "      \"url\": \"https://api.github.com/users/user\"," +
            "      \"html_url\": \"https://github.com/user\"," +
            "      \"followers_url\": \"https://api.github.com/users/user/followers\"," +
            "      \"following_url\": \"https://api.github.com/users/user/following{/other_user}\"," +
            "      \"gists_url\": \"https://api.github.com/users/user/gists{/gist_id}\"," +
            "      \"starred_url\": \"https://api.github.com/users/user/starred{/owner}{/repo}\"," +
            "      \"subscriptions_url\": \"https://api.github.com/users/user/subscriptions\"," +
            "      \"organizations_url\": \"https://api.github.com/users/user/orgs\"," +
            "      \"repos_url\": \"https://api.github.com/users/user/repos\"," +
            "      \"events_url\": \"https://api.github.com/users/user/events{/privacy}\"," +
            "      \"received_events_url\": \"https://api.github.com/users/user/received_events\"," +
            "      \"type\": \"User\"," +
            "      \"site_admin\": false" +
            "    }," +
            "    \"created_at\": \"2015-01-14T14:50:53Z\"," +
            "    \"updated_at\": \"2015-01-14T14:50:53Z\"," +
            "    \"body\": \"retest this please\"" +
            "  }," +
            "  \"repository\": {" +
            "    \"id\": 44444444," +
            "    \"name\": \"Testing\"," +
            "    \"full_name\": \"user/dropwizard\"," +
            "    \"owner\": {" +
            "      \"login\": \"user\"," +
            "      \"id\": 444444," +
            "      \"avatar_url\": \"https://avatars.githubusercontent.com/u/444444?v=3\"," +
            "      \"gravatar_id\": \"\"," +
            "      \"url\": \"https://api.github.com/users/user\"," +
            "      \"html_url\": \"https://github.com/user\"," +
            "      \"followers_url\": \"https://api.github.com/users/user/followers\"," +
            "      \"following_url\": \"https://api.github.com/users/user/following{/other_user}\"," +
            "      \"gists_url\": \"https://api.github.com/users/user/gists{/gist_id}\"," +
            "      \"starred_url\": \"https://api.github.com/users/user/starred{/owner}{/repo}\"," +
            "      \"subscriptions_url\": \"https://api.github.com/users/user/subscriptions\"," +
            "      \"organizations_url\": \"https://api.github.com/users/user/orgs\"," +
            "      \"repos_url\": \"https://api.github.com/users/user/repos\"," +
            "      \"events_url\": \"https://api.github.com/users/user/events{/privacy}\"," +
            "      \"received_events_url\": \"https://api.github.com/users/user/received_events\"," +
            "      \"type\": \"User\"," +
            "      \"site_admin\": false" +
            "    }," +
            "    \"private\": false," +
            "    \"html_url\": \"https://github.com/user/dropwizard\"," +
            "    \"description\": \"\"," +
            "    \"fork\": false," +
            "    \"url\": \"https://api.github.com/repos/user/dropwizard\"," +
            "    \"forks_url\": \"https://api.github.com/repos/user/dropwizard/forks\"," +
            "    \"keys_url\": \"https://api.github.com/repos/user/dropwizard/keys{/key_id}\"," +
            "    \"collaborators_url\": \"https://api.github.com/repos/user/dropwizard/collaborators{/collaborator}\"," +
            "    \"teams_url\": \"https://api.github.com/repos/user/dropwizard/teams\"," +
            "    \"hooks_url\": \"https://api.github.com/repos/user/dropwizard/hooks\"," +
            "    \"issue_events_url\": \"https://api.github.com/repos/user/dropwizard/issues/events{/number}\"," +
            "    \"events_url\": \"https://api.github.com/repos/user/dropwizard/events\"," +
            "    \"assignees_url\": \"https://api.github.com/repos/user/dropwizard/assignees{/user}\"," +
            "    \"branches_url\": \"https://api.github.com/repos/user/dropwizard/branches{/branch}\"," +
            "    \"tags_url\": \"https://api.github.com/repos/user/dropwizard/tags\"," +
            "    \"blobs_url\": \"https://api.github.com/repos/user/dropwizard/git/blobs{/sha}\"," +
            "    \"git_tags_url\": \"https://api.github.com/repos/user/dropwizard/git/tags{/sha}\"," +
            "    \"git_refs_url\": \"https://api.github.com/repos/user/dropwizard/git/refs{/sha}\"," +
            "    \"trees_url\": \"https://api.github.com/repos/user/dropwizard/git/trees{/sha}\"," +
            "    \"statuses_url\": \"https://api.github.com/repos/user/dropwizard/statuses/{sha}\"," +
            "    \"languages_url\": \"https://api.github.com/repos/user/dropwizard/languages\"," +
            "    \"stargazers_url\": \"https://api.github.com/repos/user/dropwizard/stargazers\"," +
            "    \"contributors_url\": \"https://api.github.com/repos/user/dropwizard/contributors\"," +
            "    \"subscribers_url\": \"https://api.github.com/repos/user/dropwizard/subscribers\"," +
            "    \"subscription_url\": \"https://api.github.com/repos/user/dropwizard/subscription\"," +
            "    \"commits_url\": \"https://api.github.com/repos/user/dropwizard/commits{/sha}\"," +
            "    \"git_commits_url\": \"https://api.github.com/repos/user/dropwizard/git/commits{/sha}\"," +
            "    \"comments_url\": \"https://api.github.com/repos/user/dropwizard/comments{/number}\"," +
            "    \"issue_comment_url\": \"https://api.github.com/repos/user/dropwizard/issues/comments/{number}\"," +
            "    \"contents_url\": \"https://api.github.com/repos/user/dropwizard/contents/{+path}\"," +
            "    \"compare_url\": \"https://api.github.com/repos/user/dropwizard/compare/{base}...{head}\"," +
            "    \"merges_url\": \"https://api.github.com/repos/user/dropwizard/merges\"," +
            "    \"archive_url\": \"https://api.github.com/repos/user/dropwizard/{archive_format}{/ref}\"," +
            "    \"downloads_url\": \"https://api.github.com/repos/user/dropwizard/downloads\"," +
            "    \"issues_url\": \"https://api.github.com/repos/user/dropwizard/issues{/number}\"," +
            "    \"pulls_url\": \"https://api.github.com/repos/user/dropwizard/pulls{/number}\"," +
            "    \"milestones_url\": \"https://api.github.com/repos/user/dropwizard/milestones{/number}\"," +
            "    \"notifications_url\": \"https://api.github.com/repos/user/dropwizard/notifications{?since,all,participating}\"," +
            "    \"labels_url\": \"https://api.github.com/repos/user/dropwizard/labels{/name}\"," +
            "    \"releases_url\": \"https://api.github.com/repos/user/dropwizard/releases{/id}\"," +
            "    \"created_at\": \"2014-07-23T15:52:14Z\"," +
            "    \"updated_at\": \"2014-09-04T21:10:34Z\"," +
            "    \"pushed_at\": \"2015-01-14T14:13:58Z\"," +
            "    \"git_url\": \"git://github.com/user/dropwizard.git\"," +
            "    \"ssh_url\": \"git@github.com:user/dropwizard.git\"," +
            "    \"clone_url\": \"https://github.com/user/dropwizard.git\"," +
            "    \"svn_url\": \"https://github.com/user/dropwizard\"," +
            "    \"homepage\": null," +
            "    \"size\": 20028," +
            "    \"stargazers_count\": 0," +
            "    \"watchers_count\": 0," +
            "    \"language\": \"JavaScript\"," +
            "    \"has_issues\": true," +
            "    \"has_downloads\": true," +
            "    \"has_wiki\": true," +
            "    \"has_pages\": false," +
            "    \"forks_count\": 0," +
            "    \"mirror_url\": null," +
            "    \"open_issues_count\": 1," +
            "    \"forks\": 0," +
            "    \"open_issues\": 1," +
            "    \"watchers\": 0," +
            "    \"default_branch\": \"master\"" +
            "  }," +
            "  \"sender\": {" +
            "    \"login\": \"user\"," +
            "    \"id\": 444444," +
            "    \"avatar_url\": \"https://avatars.githubusercontent.com/u/444444?v=3\"," +
            "    \"gravatar_id\": \"\"," +
            "    \"url\": \"https://api.github.com/users/user\"," +
            "    \"html_url\": \"https://github.com/user\"," +
            "    \"followers_url\": \"https://api.github.com/users/user/followers\"," +
            "    \"following_url\": \"https://api.github.com/users/user/following{/other_user}\"," +
            "    \"gists_url\": \"https://api.github.com/users/user/gists{/gist_id}\"," +
            "    \"starred_url\": \"https://api.github.com/users/user/starred{/owner}{/repo}\"," +
            "    \"subscriptions_url\": \"https://api.github.com/users/user/subscriptions\"," +
            "    \"organizations_url\": \"https://api.github.com/users/user/orgs\"," +
            "    \"repos_url\": \"https://api.github.com/users/user/repos\"," +
            "    \"events_url\": \"https://api.github.com/users/user/events{/privacy}\"," +
            "    \"received_events_url\": \"https://api.github.com/users/user/received_events\"," +
            "    \"type\": \"User\"," +
            "    \"site_admin\": false" +
            "  }" +
            "}";


    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void mockCommitList(GHPullRequest ghPullRequest) {
        PagedIterator itr = Mockito.mock(PagedIterator.class);
        PagedIterable pagedItr = Mockito.mock(PagedIterable.class);

        Mockito.when(ghPullRequest.listCommits()).thenReturn(pagedItr);
        Mockito.when(pagedItr.iterator()).thenReturn(itr);
        Mockito.when(itr.hasNext()).thenReturn(false);
    }

    public static void mockPR(GHPullRequest prToMock, GHCommitPointer commitPointer, DateTime... updatedDate) throws Exception {

        given(prToMock.getHead()).willReturn(commitPointer);
        given(prToMock.getBase()).willReturn(commitPointer);
        given(prToMock.getUrl()).willReturn(new URL("http://127.0.0.1"));
        given(prToMock.getApiURL()).willReturn(new URL("http://127.0.0.1"));

        if (updatedDate.length > 1) {
            given(prToMock.getUpdatedAt())
            .willReturn(updatedDate[0].toDate())
            .willReturn(updatedDate[0].toDate())
            .willReturn(updatedDate[1].toDate())
            .willReturn(updatedDate[1].toDate())
            .willReturn(updatedDate[1].toDate());
        } else {
            given(prToMock.getUpdatedAt()).willReturn(updatedDate[0].toDate());
        }
    }

    public static JSONObject provideConfiguration() {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("serverAPIUrl", "https://api.github.com");
        jsonObject.put("username", "user");
        jsonObject.put("password", "1111");
        jsonObject.put("accessToken", "accessToken");
        jsonObject.put("adminlist", "user");
        jsonObject.put("allowMembersOfWhitelistedOrgsAsAdmin", "false");
        jsonObject.put("publishedURL", "");
        jsonObject.put("requestForTestingPhrase", "test this");
        jsonObject.put("whitelistPhrase", "");
        jsonObject.put("okToTestPhrase", "ok to test");
        jsonObject.put("retestPhrase", "retest this please");
        jsonObject.put("skipBuildPhrase", "[skip ci]");
        jsonObject.put("cron", "0 0 31 2 0");
        jsonObject.put("useComments", "true");
        jsonObject.put("useDetailedComments", "false");
        jsonObject.put("logExcerptLines", "0");
        jsonObject.put("unstableAs", "");
        jsonObject.put("testMode", "true");
        jsonObject.put("autoCloseFailedPullRequests", "false");
        jsonObject.put("displayBuildErrorsOnDownstreamBuilds", "false");
        jsonObject.put("msgSuccess", "Success");
        jsonObject.put("msgFailure", "Failure");
        jsonObject.put("commitStatusContext", "Status Context");

        return jsonObject;
    }

    public static GitSCM provideGitSCM() {
        return new GitSCM(newArrayList(
                new UserRemoteConfig("https://github.com/user/dropwizard", 
                    "", "+refs/pull/*:refs/remotes/origin/pr/*", "")), 
                newArrayList(new BranchSpec("${sha1}")), 
                false,
                null, 
                null, 
                "", 
                null);
    }
    
    @SuppressWarnings("unchecked")
    public static GhprbTrigger getTrigger(Map<String, Object> values) throws ANTLRException {
        if (values == null) {
            values = new HashMap<String, Object>();
        }
        Map<String, Object> defaultValues = new HashMap<String, Object> (){
            private static final long serialVersionUID = -6720840565156773837L;

        {
            put("adminlist", "user");
            put("whitelist", "user");
            put("orgslist", "");
            put("cron", "0 0 31 2 0");
            put("triggerPhrase", "retest this please");
            put("onlyTriggerPhrase", false);
            put("useGitHubHooks", false);
            put("permitAll", false);
            put("autoCloseFailedPullRequests", false);
            put("displayBuildErrorsOnDownstreamBuilds", false);
            put("commentFilePath", null);
            put("whiteListTargetBranches", null);
            put("allowMembersOfWhitelistedOrgsAsAdmin", false);
            put("msgSuccess", null);
            put("msgFailure", null);
            put("secret", null);
            put("commitStatusContext", null);
        }};
        
        defaultValues.putAll(values);
        GhprbTrigger trigger = new GhprbTrigger(
                (String)defaultValues.get("adminlist"),
                (String)defaultValues.get("whitelist"),
                (String)defaultValues.get("orgslist"),
                (String)defaultValues.get("cron"),
                (String)defaultValues.get("triggerPhrase"),
                (Boolean)defaultValues.get("onlyTriggerPhrase"),
                (Boolean)defaultValues.get("useGitHubHooks"),
                (Boolean)defaultValues.get("permitAll"),
                (Boolean)defaultValues.get("autoCloseFailedPullRequests"),
                (Boolean)defaultValues.get("displayBuildErrorsOnDownstreamBuilds"),
                (String)defaultValues.get("commentFilePath"),
                (List<GhprbBranch>)defaultValues.get("whiteListTargetBranches"),
                (Boolean)defaultValues.get("allowMembersOfWhitelistedOrgsAsAdmin"),
                (String)defaultValues.get("msgSuccess"),
                (String)defaultValues.get("msgFailure"),
                (String)defaultValues.get("secret"),
                (String)defaultValues.get("commitStatusContext"));
        return trigger;
    }
    
    public static void waitForBuildsToFinish(AbstractProject<?, ?> project) throws InterruptedException {
        while (project.isBuilding() || project.isInQueue()) {
            // THEN
            Thread.sleep(500);
        }
    }
    

    public static void triggerRunAndWait(int numOfTriggers, GhprbTrigger trigger, AbstractProject<?, ?> project) throws InterruptedException {
        for (int i = 0; i < numOfTriggers; ++i) {
            trigger.run();
            waitForBuildsToFinish(project);
        }

    }

    private GhprbTestUtil() {}

}
