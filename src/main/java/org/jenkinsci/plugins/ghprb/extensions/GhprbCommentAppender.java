package org.jenkinsci.plugins.ghprb.extensions;

import hudson.model.AbstractBuild;
import hudson.model.TaskListener;

public interface GhprbCommentAppender {

    String postBuildComment(AbstractBuild<?, ?> build, TaskListener listener);
    
}
