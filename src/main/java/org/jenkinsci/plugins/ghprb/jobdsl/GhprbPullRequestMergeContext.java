package org.jenkinsci.plugins.ghprb.jobdsl;

import javaposse.jobdsl.dsl.Context;

public class GhprbPullRequestMergeContext implements Context {
    String mergeComment;
    boolean onlyAdminsMerge;
    boolean disallowOwnCode;
    boolean failOnNonMerge;
    boolean deleteOnMerge;
    boolean allowMergeWithoutTriggerPhrase;

    /**
     * @param mergeComment Sets a comment that should show up when the merge command is sent to GitHub.
     */
    public void mergeComment(String mergeComment) {
        this.mergeComment = mergeComment;
    }

    /**
     * @param onlyAdminsMerge Allows only admin users to trigger a pull request merge. Defaults to {@code false}. 
     */
    public void onlyAdminsMerge(boolean onlyAdminsMerge) {
        this.onlyAdminsMerge = onlyAdminsMerge;
    }

    /**
     * Allows only admin users to trigger a pull request merge. Defaults to {@code false}.
     */
    public void onlyAdminsMerge() {
        onlyAdminsMerge(true);
    }

    /**
     * @param disallowOwnCode Disallows a user to merge their own code. Defaults to {@code false}.
     */
    public void disallowOwnCode(boolean disallowOwnCode) {
        this.disallowOwnCode = disallowOwnCode;
    }

    /**
     * Disallows a user to merge their own code. Defaults to {@code false}.
     */
    public void disallowOwnCode() {
        disallowOwnCode(true);
    }

    /**
     * @param failOnNonMerge Fails the build if the pull request can't be merged. Defaults to {@code false}.
     */
    public void failOnNonMerge(boolean failOnNonMerge) {
        this.failOnNonMerge = failOnNonMerge;
    }

    /**
     * Fails the build if the pull request can't be merged. Defaults to {@code false}.
     */
    public void failOnNonMerge() {
        failOnNonMerge(true);
    }

    /**
     * @param deleteOnMerge Deletes the branch after a successful merge. Defaults to {@code false}.
     */
    public void deleteOnMerge(boolean deleteOnMerge) {
        this.deleteOnMerge = deleteOnMerge;
    }

    /**
     * Deletes the branch after a successful merge. Defaults to {@code false}.
     */
    public void deleteOnMerge() {
        deleteOnMerge(true);
    }

    /**
     * Allows merging the PR even if the trigger phrase was not present. Defaults to {@code false}
     * @param allowMergeWithoutTriggerPhrase
     */
    public void allowMergeWithoutTriggerPhrase(boolean allowMergeWithoutTriggerPhrase) {
        this.allowMergeWithoutTriggerPhrase = allowMergeWithoutTriggerPhrase;
    }

    /**
     * Allows merging the PR even if the trigger phrase was not present. Defaults to {@code false}
     */
    public void allowMergeWithoutTriggerPhrase() {
        allowMergeWithoutTriggerPhrase(false);
    }
}
