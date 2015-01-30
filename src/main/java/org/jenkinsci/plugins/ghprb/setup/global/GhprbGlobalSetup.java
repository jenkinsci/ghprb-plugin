package org.jenkinsci.plugins.ghprb.setup.global;

import org.kohsuke.github.GHIssueComment;

import hudson.model.AbstractDescribableImpl;

public abstract class GhprbGlobalSetup extends AbstractDescribableImpl<GhprbGlobalSetup> {
    
    /**
     * Check if the pull request should be built off of comment.
     * Assuming that if any of these methods return true
     * we should build.
     * @param comment
     * @return
     */
    public boolean shouldBuild(GHIssueComment comment, boolean isAdmin, boolean isWhiteListed){
        return false;
    }

    public boolean isTriggered(GHIssueComment comment, boolean isAdmin, boolean isWhiteListed) {
        return false;
    }

}
