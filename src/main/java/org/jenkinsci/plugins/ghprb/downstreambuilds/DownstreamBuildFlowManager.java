package org.jenkinsci.plugins.ghprb.downstreambuilds;

import java.util.Iterator;
import java.util.Set;

import org.jgrapht.DirectedGraph;

import com.cloudbees.plugins.flow.FlowRun;
import com.cloudbees.plugins.flow.FlowRun.JobEdge;

import hudson.model.AbstractBuild;

/**
 * @author mdelapenya (Manuel de la Peña)
 */
public class DownstreamBuildFlowManager extends BaseDownstreamBuildManager {

	public DownstreamBuildFlowManager(AbstractBuild build) {
		super(build);
	}

	@Override
	public String calculateBuildUrl() {
		FlowRun flowRun = (FlowRun) build;

		DirectedGraph directedGraph = flowRun.getJobsGraph();

		Set<JobEdge> edgeSet = directedGraph.edgeSet();

		Iterator<JobEdge> iterator = edgeSet.iterator();

		StringBuilder sb = new StringBuilder();

		while (iterator.hasNext()) {
			JobEdge jobEdge = iterator.next();

			sb.append("\n");
			sb.append("\t");
			sb.append(jobEdge.getTarget().getBuildUrl());
		}

		return sb.toString();
	}

}