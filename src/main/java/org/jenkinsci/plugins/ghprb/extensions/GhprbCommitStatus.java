package org.jenkinsci.plugins.ghprb.extensions;

import hudson.model.AbstractBuild;
import hudson.model.TaskListener;

import org.jenkinsci.plugins.ghprb.GhprbPullRequest;
import org.jenkinsci.plugins.ghprb.GhprbTrigger;
import org.kohsuke.github.GHRepository;

public interface GhprbCommitStatus {
    
    public void onBuildTriggered(GhprbTrigger trigger, GhprbPullRequest pr, GHRepository ghRepository) throws GhprbCommitStatusException;
    public void onBuildStart(AbstractBuild<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException;
    public void onBuildComplete(AbstractBuild<?, ?> build, TaskListener listener, GHRepository repo) throws GhprbCommitStatusException;

}
