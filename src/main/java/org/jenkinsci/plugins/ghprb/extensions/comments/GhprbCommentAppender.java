package org.jenkinsci.plugins.ghprb.extensions.comments;

import hudson.model.AbstractBuild;
import hudson.model.TaskListener;

public interface GhprbCommentAppender {

    public String postBuildComment(AbstractBuild<?, ?> build, TaskListener listener);
    
}
