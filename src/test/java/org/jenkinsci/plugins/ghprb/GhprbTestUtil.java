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

import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
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
        jsonObject.put("unstableAs", "FAILURE");
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
            put("commitStatusContext", null);
            put("extensions", null);
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
                (String)defaultValues.get("commitStatusContext"),
                (List<GhprbExtension>)defaultValues.get("extensions"));
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