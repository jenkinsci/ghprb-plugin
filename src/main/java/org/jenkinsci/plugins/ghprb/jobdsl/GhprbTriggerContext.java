package org.jenkinsci.plugins.ghprb.jobdsl;

import javaposse.jobdsl.dsl.Context;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import org.jenkinsci.plugins.ghprb.GhprbBranch;

import java.util.ArrayList;
import java.util.List;

class GhprbTriggerContext implements Context {
    List<String> admins = new ArrayList<String>();
    List<String> userWhitelist = new ArrayList<String>();
    List<String> orgWhitelist = new ArrayList<String>();
    List<GhprbBranch> whiteListTargetBranches = new ArrayList<GhprbBranch>();
    List<GhprbBranch> blackListTargetBranches = new ArrayList<GhprbBranch>();
    String cron = "H/5 * * * *";
    String triggerPhrase;
    String skipBuildPhrase;
    boolean onlyTriggerPhrase;
    boolean useGitHubHooks;
    boolean permitAll;
    boolean autoCloseFailedPullRequests;
    boolean allowMembersOfWhitelistedOrgsAsAdmin;
    boolean displayBuildErrorsOnDownstreamBuilds;
    String buildDescriptionTemplate;
    GhprbExtensionContext extensionContext = new GhprbExtensionContext();

    /**
     * Adds admins for this job.
     */
    public void admin(String admin) {
        admins.add(admin);
    }

    /**
     * Adds admins for this job.
     */
    public void admins(Iterable<String> admins) {
        for (String admin : admins) {
            admin(admin);
        }
    }

    /**
     * Adds whitelisted users for this job.
     */
    public void userWhitelist(String user) {
        userWhitelist.add(user);
    }

    /**
     * Adds whitelisted users for this job.
     */
    public void userWhitelist(Iterable<String> users) {
        for (String user : users) {
            userWhitelist(user);
        }
    }

    /**
     * Adds organisation names whose members are considered whitelisted for this specific job.
     */
    public void orgWhitelist(String organization) {
        orgWhitelist.add(organization);
    }

    /**
     * Adds organisation names whose members are considered whitelisted for this specific job.
     */
    public void orgWhitelist(Iterable<String> organizations) {
        for (String organization : organizations) {
            orgWhitelist(organization);
        }
    }

    /**
     * Add branch names whose they are considered whitelisted for this specific job
     */
    public void whiteListTargetBranch(String branch) {
        whiteListTargetBranches.add(new GhprbBranch(branch));
    }

    /**
     * Add branch names whose they are considered blacklisted for this specific job
     */
    public void blackListTargetBranch(String branch) {
        blackListTargetBranches.add(new GhprbBranch(branch));
    }

    /**
     * Add branch names whose they are considered whitelisted for this specific job
     */
    public void whiteListTargetBranches(Iterable<String> branches) {
        for (String branch : branches) {
            whiteListTargetBranches.add(new GhprbBranch(branch));
        }
    }

    /**
     * Add branch names whose they are considered blacklisted for this specific job
     */
    public void blackListTargetBranches(Iterable<String> branches) {
        for (String branch : branches) {
            blackListTargetBranches.add(new GhprbBranch(branch));
        }
    }

    /**
     * This schedules polling to GitHub for new changes in pull requests.
     */
    public void cron(String cron) {
        this.cron = cron;
    }

    /**
     * When filled, commenting this phrase in the pull request will trigger a build.
     */
    public void triggerPhrase(String triggerPhrase) {
        this.triggerPhrase = triggerPhrase;
    }

    /**
     * When filled, adding this phrase to the pull request title or body will skip the build.
     */
    public void skipBuildPhrase(String skipBuildPhrase) {
        this.skipBuildPhrase = skipBuildPhrase;
    }

    /**
     * When set, only commenting the trigger phrase in the pull request will trigger a build.
     */
    public void onlyTriggerPhrase(boolean onlyTriggerPhrase) {
        this.onlyTriggerPhrase = onlyTriggerPhrase;
    }

    /**
     * When set, only commenting the trigger phrase in the pull request will trigger a build.
     */
    public void onlyTriggerPhrase() {
        onlyTriggerPhrase(true);
    }

    /**
     * Checking this option will disable regular polling for changes in GitHub and will try to create a GitHub hook.
     */
    public void useGitHubHooks(boolean useGitHubHooks) {
        this.useGitHubHooks = useGitHubHooks;
    }

    /**
     * Checking this option will disable regular polling for changes in GitHub and will try to create a GitHub hook.
     */
    public void useGitHubHooks() {
        useGitHubHooks(true);
    }

    /**
     * Build every pull request automatically without asking.
     */
    public void permitAll(boolean permitAll) {
        this.permitAll = permitAll;
    }

    /**
     * Build every pull request automatically without asking.
     */
    public void permitAll() {
        permitAll(true);
    }

    /**
     * Close pull request automatically when the build fails.
     */
    public void autoCloseFailedPullRequests(boolean autoCloseFailedPullRequests) {
        this.autoCloseFailedPullRequests = autoCloseFailedPullRequests;
    }

    /**
     * Close pull request automatically when the build fails.
     */
    public void autoCloseFailedPullRequests() {
        autoCloseFailedPullRequests(true);
    }

    /**
     * Allows members of whitelisted organisations to behave like admins.
     */
    public void allowMembersOfWhitelistedOrgsAsAdmin(boolean allowMembersOfWhitelistedOrgsAsAdmin) {
        this.allowMembersOfWhitelistedOrgsAsAdmin = allowMembersOfWhitelistedOrgsAsAdmin;
    }

    /**
     * Allows members of whitelisted organisations to behave like admins.
     */
    public void allowMembersOfWhitelistedOrgsAsAdmin() {
        allowMembersOfWhitelistedOrgsAsAdmin(true);
    }

    /**
     * Allow this upstream job to get commit statuses from downstream builds
     */
    public void displayBuildErrorsOnDownstreamBuilds(boolean displayBuildErrorsOnDownstreamBuilds) {
        this.displayBuildErrorsOnDownstreamBuilds = displayBuildErrorsOnDownstreamBuilds;
    }

    /**
     * Allow this upstream job to get commit statuses from downstream builds
     */
    public void displayBuildErrorsOnDownstreamBuilds() {
        displayBuildErrorsOnDownstreamBuilds(true);
    }

    /**
     * When filled, changes the default build description template
     */
    public void buildDescriptionTemplate(String template) {
       this.buildDescriptionTemplate = template;
    }

    /**
     * Adds additional trigger options.
     */
    public void extensions(Runnable closure) {
        ContextExtensionPoint.executeInContext(closure, extensionContext);
    }
}
