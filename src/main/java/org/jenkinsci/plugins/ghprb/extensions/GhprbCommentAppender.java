package org.jenkinsci.plugins.ghprb.extensions;

import hudson.model.Run;
import hudson.model.TaskListener;

public interface GhprbCommentAppender {
    String postBuildComment(Run<?, ?> build, TaskListener listener);
}
