package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractProject;
import hudson.triggers.Trigger;

import java.util.List;

import antlr.ANTLRException;

public class GhprbTriggerBackwardsCompatibility extends Trigger<AbstractProject<?, ?>> {
    
    protected transient String adminlist;
    protected transient Boolean allowMembersOfWhitelistedOrgsAsAdmin;
    protected transient String orgslist;
    protected transient String cron;
    protected transient String triggerPhrase;
    protected transient Boolean onlyTriggerPhrase;
    protected transient Boolean useGitHubHooks;
    protected transient Boolean permitAll;
    protected transient String commentFilePath;
    protected transient String whitelist;
    protected transient Boolean autoCloseFailedPullRequests;
    protected transient Boolean displayBuildErrorsOnDownstreamBuilds;
    protected transient List<GhprbBranch> whiteListTargetBranches;
    protected transient String msgSuccess;
    protected transient String msgFailure;
    protected transient String commitStatusContext;
    protected transient Ghprb helper;
    protected transient String project;
    
    public GhprbTriggerBackwardsCompatibility(String cron) throws ANTLRException {
        super(cron);
    }


}
