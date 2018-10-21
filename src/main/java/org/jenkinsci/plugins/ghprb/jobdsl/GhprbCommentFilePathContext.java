package org.jenkinsci.plugins.ghprb.jobdsl;

import javaposse.jobdsl.dsl.Context;

class GhprbCommentFilePathContext implements Context {
    private String commentFilePath;

    public String getCommentFilePath() {
        return commentFilePath;
    }

    /**
     * sets the path to the comment file
     */
    public void commentFilePath(String commentFilePath) {
        this.commentFilePath = commentFilePath;
    }
}
