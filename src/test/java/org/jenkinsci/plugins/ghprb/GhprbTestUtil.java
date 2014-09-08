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

import java.net.MalformedURLException;
import java.net.URL;

import org.joda.time.DateTime;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.mockito.Mockito;

import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import net.sf.json.JSONObject;

public class GhprbTestUtil {

	public static final int INITIAL_RATE_LIMIT = 5000;
	public static final String GHPRB_PLUGIN_NAME = "ghprb";

	public static void mockCommitList(GHPullRequest ghPullRequest) {
		PagedIterator itr = Mockito.mock(PagedIterator.class);
		PagedIterable pagedItr = Mockito.mock(PagedIterable.class);

		Mockito.when(ghPullRequest.listCommits()).thenReturn(pagedItr);
		Mockito.when(pagedItr.iterator()).thenReturn(itr);
		Mockito.when(itr.hasNext()).thenReturn(false);
	}

	public static void mockPR(
			GHPullRequest prToMock, GHCommitPointer commitPointer,
			DateTime... updatedDate)
		throws MalformedURLException {

		given(prToMock.getHead()).willReturn(commitPointer);
		given(prToMock.getBase()).willReturn(commitPointer);
		given(prToMock.getUrl()).willReturn(new URL("http://127.0.0.1"));
		given(prToMock.getApiURL()).willReturn(new URL("http://127.0.0.1"));

		if (updatedDate.length > 1) {
			given(prToMock.getUpdatedAt()).willReturn(updatedDate[0].toDate())
				.willReturn(updatedDate[0].toDate())
				.willReturn(updatedDate[1].toDate())
				.willReturn(updatedDate[1].toDate())
				.willReturn(updatedDate[1].toDate());
		}
		else {
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
		jsonObject.put("cron", "*/1 * * * *");
		jsonObject.put("useComments", "true");
		jsonObject.put("logExcerptLines", "0");
		jsonObject.put("unstableAs", "");
		jsonObject.put("testMode", "true");
		jsonObject.put("autoCloseFailedPullRequests", "false");
		jsonObject.put("displayBuildErrorsOnDownstreamBuilds", "false");
		jsonObject.put("msgSuccess", "Success");
		jsonObject.put("msgFailure", "Failure");

		return jsonObject;
	}

	public static GitSCM provideGitSCM() {
		return new GitSCM(
			newArrayList(
				new UserRemoteConfig(
					"https://github.com/user/dropwizard", "",
					"+refs/pull/*:refs/remotes/origin/pr/*", "")
			),
			newArrayList(new BranchSpec("${sha1}")),
			false,
			null,
			null,
			"",
			null
		);
	}

	private GhprbTestUtil() {
	}

}