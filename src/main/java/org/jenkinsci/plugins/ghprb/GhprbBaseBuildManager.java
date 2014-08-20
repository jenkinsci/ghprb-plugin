package org.jenkinsci.plugins.ghprb;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import hudson.model.AbstractBuild;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.test.AggregatedTestResultAction;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public abstract class GhprbBaseBuildManager implements GhprbBuildManager {

	public GhprbBaseBuildManager(AbstractBuild build) {
		this.build = build;
	}

	/**
	 * Calculate the build URL of a build of default type. This will be overriden
	 * by specific build types.
	 * 
	 * @return the build URL of a build of default type
	 */
	public String calculateBuildUrl() {
		String publishedURL = GhprbTrigger.getDscp().getPublishedURL();

		return publishedURL + "/" + build.getUrl();
	}

	/**
	 * Return a downstream iterator of a build of default type. This will be overriden
	 * by specific build types.
	 * 
	 * @return the downstream builds as an iterator
	 */
	public Iterator downstreamIterator() {
		List downstreamList = new ArrayList();

		downstreamList.add(build);

		return downstreamList.iterator();
	}

	/**
	 * Return the tests results of a build of default type. This will be overriden
	 * by specific build types.
	 * 
	 * @return the tests result of a build of default type
	 */
	public String getTestResults() {
		return getAggregatedTestResults(build);
	}

	protected String getAggregatedTestResults(AbstractBuild build) {
		AggregatedTestResultAction aggregatedTestResultAction =
			build.getAggregatedTestResultAction();

		List<CaseResult> failedTests =
			aggregatedTestResultAction.getFailedTests();

		StringBuilder sb = new StringBuilder();

		sb.append("<h2>Failed Tests:</h2>");
		sb.append("<ul>");

		for (CaseResult failedTest : failedTests) {
			sb.append("<li>");
			sb.append("<a href='");
			sb.append(failedTest.getUrl());
			sb.append("'>");
			sb.append("<strong>");
			sb.append(failedTest.getFullDisplayName());
			sb.append("</strong>");
			sb.append("</a>");
			sb.append(failedTest.getErrorStackTrace());
			sb.append("</li>");
		}

		sb.append("</ul>");

		return sb.toString();
	}

	protected AbstractBuild build;

}