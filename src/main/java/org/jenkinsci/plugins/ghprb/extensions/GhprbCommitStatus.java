package org.jenkinsci.plugins.ghprb.extensions;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.TaskListener;

import org.kohsuke.github.GHRepository;

public interface GhprbCommitStatus {

    void onEnvironmentSetup(AbstractBuild<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException;
    void onBuildTriggered(AbstractProject<?, ?> project, String commitSha, boolean isMergeable, int prId, GHRepository ghRepository) throws GhprbCommitStatusException;
    void onBuildStart(AbstractBuild<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException;
    void onBuildComplete(AbstractBuild<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException;

}
