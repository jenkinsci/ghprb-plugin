package org.jenkinsci.plugins.ghprb.extensions;

import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.util.List;

import org.kohsuke.github.GHRepository;

public interface GhprbCommitStatus {
    
    void onEnvironmentSetup(Run<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException;
    List<Action> onBuildTriggered(Job<?, ?> project, String commitSha, boolean isMergeable, int prId, GHRepository ghRepository) throws GhprbCommitStatusException;
    void onBuildStart(Run<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException;
    void onBuildComplete(Run<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException;
}
