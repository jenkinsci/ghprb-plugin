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
import java.util.Map;
import java.util.Map.Entry;

import org.joda.time.DateTime;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.stapler.BindInterceptor;
import org.kohsuke.stapler.RequestImpl;
import org.mockito.Mock;
import org.mockito.Mockito;

import hudson.model.AbstractProject;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import net.sf.json.JSONObject;
import static org.mockito.Matchers.any;

public class GhprbTestUtil {

    public static final int INITIAL_RATE_LIMIT = 5000;
    public static final String GHPRB_PLUGIN_NAME = "ghprb";
    
    private static RequestImpl req;

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
    
    private static final String apiUrl = "https://api.github.com";
    
    private static String setUpCredentials() throws Exception {
        String credentialsId = Ghprb.createCredentials(apiUrl, "accessToken");
        return credentialsId;
    }
    
    private static String credentialsId;
    
    private static String getCredentialsId() throws Exception {
        if (credentialsId == null) {
            credentialsId = setUpCredentials();
        }
        return credentialsId;
    }

    public static void setupGhprbTriggerDescriptor(Map<String, Object> config) throws Exception {
        setupReq();
        if (config == null) {
            config = new HashMap<String, Object>();
        }
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("serverAPIUrl", "https://api.github.com");
        jsonObject.put("username", "user");
        jsonObject.put("password", "1111");
        jsonObject.put("accessToken", "accessToken");
        jsonObject.put("adminlist", "user");
        jsonObject.put("allowMembersOfWhitelistedOrgsAsAdmin", "false");
        jsonObject.put("publishedURL", "defaultPublishedURL");
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
        
        JSONObject githubAuth = new JSONObject();
        githubAuth.put("credentialsId", getCredentialsId());
        githubAuth.put("serverAPIUrl", apiUrl);
        
        jsonObject.put("githubAuth", githubAuth);
        

        for ( Entry<String, Object> next: config.entrySet()) {
            jsonObject.put(next.getKey(), next.getValue());
        }
        

        GhprbTrigger.getDscp().configure(req, jsonObject);
        
    }
    
    @SuppressWarnings("unchecked")
    private static void setupReq() {
        req = Mockito.mock(RequestImpl.class);
        given(req.bindJSON(any(Class.class), any(JSONObject.class))).willCallRealMethod();
        given(req.bindJSON(any(Class.class), any(Class.class), any(JSONObject.class))).willCallRealMethod();
        given(req.setBindListener(any(BindInterceptor.class))).willCallRealMethod();
        req.setBindListener(BindInterceptor.NOOP);

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
    
    public static GhprbTrigger getTrigger(Map<String, Object> values) throws Exception {
        setupReq();
        if (values == null) {
            values = new HashMap<String, Object>();
        }
        JSONObject defaults = new JSONObject();
        defaults.put("adminlist", "user");
        defaults.put("whitelist", "user");
        defaults.put("orgslist", "");
        defaults.put("cron", "0 0 31 2 0");
        defaults.put("triggerPhrase", "retest this please");
        defaults.put("onlyTriggerPhrase", false);
        defaults.put("useGitHubHooks", false);
        defaults.put("permitAll", false);
        defaults.put("autoCloseFailedPullRequests", false);
        defaults.put("displayBuildErrorsOnDownstreamBuilds", false);
        defaults.put("allowMembersOfWhitelistedOrgsAsAdmin", false);
        defaults.put("gitHubApi", "https://api.github.com");

        for ( Entry<String, Object> next: values.entrySet()) {
            defaults.put(next.getKey(), next.getValue());
        }
        
        GhprbTrigger trigger = req.bindJSON(GhprbTrigger.class, defaults);
        
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