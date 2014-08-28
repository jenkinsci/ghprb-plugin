package org.jenkinsci.plugins.ghprb.manager.impl;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import jenkins.model.Jenkins;

import hudson.model.AbstractBuild;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.test.AggregatedTestResultAction;

import org.jenkinsci.plugins.ghprb.GhprbTrigger;
import org.jenkinsci.plugins.ghprb.manager.configuration.JobConfiguration;
import org.jenkinsci.plugins.ghprb.manager.GhprbBuildManager;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public abstract class GhprbBaseBuildManager implements GhprbBuildManager {

	public GhprbBaseBuildManager(AbstractBuild build) {
		this.build = build;
		this.jobConfiguration = buildDefaultConfiguration();
	}

	public GhprbBaseBuildManager(AbstractBuild build, JobConfiguration jobConfiguration) {
		this.build = build;
		this.jobConfiguration = jobConfiguration;
	}

	private JobConfiguration buildDefaultConfiguration() {
		return JobConfiguration.builder()
				.printStackTrace(false)
				.build();
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
	 * If the receiver of the call has no child projects, it will return an
	 * iterator over itself
	 *
	 * @return the downstream builds as an iterator
	 */
	public Iterator downstreamProjects() {
		List downstreamList = new ArrayList();

		downstreamList.add(build);

		return downstreamList.iterator();
	}

	public JobConfiguration getJobConfiguration() {
		return this.jobConfiguration;
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

		sb.append("<h2>Failed Tests: ");
		sb.append("<span class='status-failure'>");
		sb.append(failedTests.size());
		sb.append("</span></h2>");
		sb.append("<ul>");

		for (CaseResult failedTest : failedTests) {
			sb.append("<li>");
			sb.append("<a href='");
			sb.append(Jenkins.getInstance().getRootUrl());
			sb.append(build.getUrl());
			sb.append("org.jenkins-ci.plugins$ghprb/testReport");
			sb.append(failedTest.getUrl());
			sb.append("'>");
			sb.append("<strong>");
			sb.append(failedTest.getFullDisplayName());
			sb.append("</strong>");
			sb.append("</a>");

			if (getJobConfiguration().printStackTrace()) {
				sb.append("\n```\n");
				sb.append(failedTest.getErrorStackTrace());
				sb.append("\n```\n");
			}

			sb.append("</li>");
		}

		sb.append("</ul>");

		return sb.toString();
	}

	protected AbstractBuild build;
	private JobConfiguration jobConfiguration;

}