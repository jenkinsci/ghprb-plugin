package org.jenkinsci.plugins.ghprb.manager.impl.downstreambuilds;

import com.cloudbees.plugins.flow.FlowRun;
import com.cloudbees.plugins.flow.JobInvocation;
import hudson.model.Run;
import hudson.tasks.test.AggregatedTestResultAction;
import org.jenkinsci.plugins.ghprb.manager.configuration.JobConfiguration;
import org.jenkinsci.plugins.ghprb.manager.impl.GhprbBaseBuildManager;
import org.jgrapht.DirectedGraph;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public class BuildFlowBuildManager extends GhprbBaseBuildManager {

    private static final Logger LOGGER = Logger.getLogger(BuildFlowBuildManager.class.getName());

    public BuildFlowBuildManager(Run<?, ?> build) {
        super(build);
    }

    public BuildFlowBuildManager(Run<?, ?> build, JobConfiguration jobConfiguration) {
        super(build, jobConfiguration);
    }

    /**
     * Calculate the build URL of a build of BuildFlow type, traversing its downstream builds graph
     *
     * @return the build URL of a BuildFlow build, with all its downstream builds
     */
    @SuppressWarnings("unchecked")
    @Override
    public String calculateBuildUrl(String publishedURL) {
        Iterator<JobInvocation> iterator = (Iterator<JobInvocation>) downstreamProjects();

        StringBuilder sb = new StringBuilder();

        while (iterator.hasNext()) {
            JobInvocation jobInvocation = iterator.next();

            sb.append("\n");
            sb.append("<a href='");
            sb.append(jobInvocation.getBuildUrl());
            sb.append("'>");
            sb.append(jobInvocation.getBuildUrl());
            sb.append("</a>");
        }

        return sb.toString();
    }

    /**
     * Return a downstream iterator of a build of default type. This will be overriden by specific build types.
     *
     * @return the downstream builds as an iterator
     */
    @Override
    public Iterator<?> downstreamProjects() {
        FlowRun flowRun = (FlowRun) build;

        DirectedGraph<JobInvocation, FlowRun.JobEdge> directedGraph = flowRun.getJobsGraph();

        return directedGraph.vertexSet().iterator();
    }

    /**
     * Return the tests results of a build of default type. This will be overriden by specific build types.
     *
     * @return the tests result of a build of default type
     */
    @SuppressWarnings("unchecked")
    @Override
    public String getTestResults() {
        Iterator<JobInvocation> iterator = (Iterator<JobInvocation>) downstreamProjects();

        StringBuilder sb = new StringBuilder();

        while (iterator.hasNext()) {
            JobInvocation jobInvocation = iterator.next();

            try {
                Run<?, ?> build = (Run<?, ?>) jobInvocation.getBuild();

                AggregatedTestResultAction testResultAction = build.getAction(AggregatedTestResultAction.class);

                if (testResultAction != null) {
                    sb.append("\n");
                    sb.append(jobInvocation.getBuildUrl());
                    sb.append("\n");
                    sb.append(getAggregatedTestResults(build));
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Job execution has failed", e);
            }
        }

        return sb.toString();
    }

}
