package org.jenkinsci.plugins.ghprb.jobdsl;

import javaposse.jobdsl.dsl.Context;

public class GhprbPullRequestMergeContext implements Context {
    String mergeComment;
    boolean onlyAdminsMerge;
    boolean disallowOwnCode;
    boolean failOnNonMerge;
    boolean deleteOnMerge;

    /**
     * Sets a comment that should show up when the merge command is sent to GitHub.
     */
    public void mergeComment(String mergeComment) {
        this.mergeComment = mergeComment;
    }

    /**
     * Allows only admin users to trigger a pull request merge. Defaults to {@code false}.
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
     * Disallows a user to merge their own code. Defaults to {@code false}.
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
     * Fails the build if the pull request can't be merged. Defaults to {@code false}.
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
     * Deletes the branch after a successful merge. Defaults to {@code false}.
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
}
