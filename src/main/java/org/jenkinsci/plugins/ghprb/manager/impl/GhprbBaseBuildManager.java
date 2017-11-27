package org.jenkinsci.plugins.ghprb.manager.impl;

import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.AggregatedTestResultAction;
import hudson.tasks.test.AggregatedTestResultAction.ChildReport;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.ghprb.manager.GhprbBuildManager;
import org.jenkinsci.plugins.ghprb.manager.configuration.JobConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public abstract class GhprbBaseBuildManager implements GhprbBuildManager {

    private static final Logger LOGGER = Logger.getLogger(GhprbBaseBuildManager.class.getName());

    public GhprbBaseBuildManager(Run<?, ?> build) {
        this.build = build;
        this.jobConfiguration = buildDefaultConfiguration();
    }

    public GhprbBaseBuildManager(Run<?, ?> build, JobConfiguration jobConfiguration) {
        this.build = build;
        this.jobConfiguration = jobConfiguration;
    }

    private JobConfiguration buildDefaultConfiguration() {
        return JobConfiguration.builder().printStackTrace(false).build();
    }

    /**
     * Calculate the build URL of a build of default type. This will be overriden by specific build types.
     *
     * @return the build URL of a build of default type
     */
    public String calculateBuildUrl(String publishedURL) {
        return publishedURL + "/" + build.getUrl();
    }

    /**
     * Return a downstream iterator of a build of default type. This will be overriden by specific build types.
     * <p>
     * If the receiver of the call has no child projects, it will return an iterator over itself
     *
     * @return the downstream builds as an iterator
     */
    public Iterator<?> downstreamProjects() {
        List<Run<?, ?>> downstreamList = new ArrayList<Run<?, ?>>();

        downstreamList.add(build);

        return downstreamList.iterator();
    }

    public JobConfiguration getJobConfiguration() {
        return this.jobConfiguration;
    }

    /**
     * Return the tests results of a build of default type. This will be overriden by specific build types.
     *
     * @return the tests result of a build of default type
     */
    public String getTestResults() {
        return getAggregatedTestResults(build);
    }

    protected String getAggregatedTestResults(Run<?, ?> build) {
        AggregatedTestResultAction testResultAction = build.getAction(AggregatedTestResultAction.class);

        if (testResultAction == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        if (build.getResult() != Result.UNSTABLE) {
            sb.append("<h2>Build result: ");
            sb.append(build.getResult().toString());
            sb.append("</span></h2>");

            try {
                List<String> buildLog = build.getLog(MAX_LINES_COUNT);

                for (String buildLogLine : buildLog) {
                    sb.append(buildLogLine);
                }
            } catch (IOException ioe) {
                LOGGER.log(Level.WARNING, ioe.getMessage());
            }

            return sb.toString();
        }

        sb.append("<h2>Failed Tests: ");
        sb.append("<span class='status-failure'>");
        sb.append(testResultAction.getFailCount());
        sb.append("</span></h2>");

        List<ChildReport> childReports = testResultAction.getChildReports();

        for (ChildReport report : childReports) {
            TestResult result = (TestResult) report.result;

            if (result.getFailCount() < 1) {
                continue;
            }

            Job<?, ?> project = (Job<?, ?>) report.child.getProject();

            String baseUrl = Jenkins.getInstance().getRootUrl() + build.getUrl() + project.getShortUrl() + "testReport";

            sb.append("<h3>");
            sb.append("<a name='");
            sb.append(project.getFullName());
            sb.append("' />");
            sb.append("<a href='");
            sb.append(baseUrl);
            sb.append("'>");
            sb.append(project.getFullName());
            sb.append("</a>");
            sb.append(": ");
            sb.append("<span class='status-failure'>");
            sb.append(result.getFailCount());
            sb.append("</span></h3>");

            sb.append("<ul>");

            List<CaseResult> failedTests = result.getFailedTests();

            for (CaseResult failedTest : failedTests) {
                sb.append("<li>");
                sb.append("<a href='");
                sb.append(baseUrl);
                sb.append("/");
                sb.append(failedTest.getRelativePathFrom(result));
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
        }

        return sb.toString();
    }

    public String getOneLineTestResults() {

        AbstractTestResultAction testResultAction = build.getAction(AbstractTestResultAction.class);

        if (testResultAction == null) {
            return "No test results found.";
        }
        return String.format(
                "%d tests run, %d skipped, %d failed.",
                testResultAction.getTotalCount(),
                testResultAction.getSkipCount(),
                testResultAction.getFailCount()
        );
    }

    protected Run<?, ?> build;

    private static final int MAX_LINES_COUNT = 25;

    private JobConfiguration jobConfiguration;

}
