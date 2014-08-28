package org.jenkinsci.plugins.ghprb.downstreambuilds;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.jenkinsci.plugins.ghprb.GhprbBaseBuildManager;
import org.jgrapht.DirectedGraph;

import com.cloudbees.plugins.flow.FlowRun;
import com.cloudbees.plugins.flow.JobInvocation;
import com.cloudbees.plugins.flow.FlowRun.JobEdge;

import hudson.model.AbstractBuild;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public class BuildFlowBuildManager extends GhprbBaseBuildManager {

	public BuildFlowBuildManager(AbstractBuild build) {
		super(build);
	}

	/**
	 * Calculate the build URL of a build of BuildFlow type, traversing its
	 * downstream builds graph
	 * 
	 * @return the build URL of a BuildFlow build, with all its downstream builds
	 */
	@Override
	public String calculateBuildUrl() {
		Iterator<JobInvocation> iterator = downstreamProjects();

		StringBuilder sb = new StringBuilder();

		while (iterator.hasNext()) {
			JobInvocation jobInvocation = iterator.next();

			sb.append("\n");
			sb.append("\t");
			sb.append(jobInvocation.getBuildUrl());
		}

		return sb.toString();
	}

	/**
	 * Return a downstream iterator of a build of default type. This will be overriden
	 * by specific build types.
	 * 
	 * @return the downstream builds as an iterator
	 */
	@Override
	public Iterator downstreamProjects() {
		FlowRun flowRun = (FlowRun) build;

		DirectedGraph directedGraph = flowRun.getJobsGraph();

		Set<JobEdge> edgeSet = directedGraph.edgeSet();

		Iterator<JobEdge> iterator = edgeSet.iterator();

		Set nodes = new HashSet();

		while (iterator.hasNext()) {
			JobEdge jobEdge = iterator.next();

			JobInvocation target = jobEdge.getTarget();

			nodes.add(target);
		}

		return nodes.iterator();
	}

	/**
	 * Return the tests results of a build of default type. This will be overriden
	 * by specific build types.
	 * 
	 * @param printStackTraces wether to print or not the stacktraces associated to each test
	 * @return the tests result of a build of default type
	 */
	@Override
	public String getTestResults(boolean printStackTraces) {
		Iterator<JobInvocation> iterator = downstreamProjects();

		StringBuilder sb = new StringBuilder();

		while (iterator.hasNext()) {
			JobInvocation jobInvocation = iterator.next();

			try {
				AbstractBuild build = (AbstractBuild)jobInvocation.getBuild();

				if (build.getAggregatedTestResultAction() != null) {
					sb.append("\n");
					sb.append(jobInvocation.getBuildUrl());
					sb.append("\n");
					sb.append(getAggregatedTestResults(build, printStackTraces));
				}
			} catch (ExecutionException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		return sb.toString();
	}

}