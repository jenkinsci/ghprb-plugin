package org.jenkinsci.plugins.ghprb.extensions.comments;

import java.io.File;
import java.io.IOException;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.ghprb.Ghprb;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommentAppender;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.GhprbProjectExtension;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildStatus.DescriptorImpl;
import org.kohsuke.stapler.DataBoundConstructor;

public class GhprbCommentFile extends GhprbExtension implements GhprbCommentAppender, GhprbProjectExtension {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    
    private final String commentFilePath;

    @DataBoundConstructor
    public GhprbCommentFile(String commentFilePath) {
        this.commentFilePath = commentFilePath;
    }
    
    public String getCommentFilePath() {
        return commentFilePath != null ? commentFilePath : "";
    }
    
    public boolean ignorePublishedUrl() {
        // TODO Auto-generated method stub
        return false;
    }

    public String postBuildComment(AbstractBuild<?, ?> build, TaskListener listener) {
        StringBuilder msg = new StringBuilder();
        if (commentFilePath != null && !commentFilePath.isEmpty()) {
            try {
                String scriptFilePathResolved = Ghprb.replaceMacros(build, commentFilePath);
                
                String content = FileUtils.readFileToString(new File(scriptFilePathResolved));
                msg.append("Build comment file: \n--------------\n");
                msg.append(content);
                msg.append("\n--------------\n");
            } catch (IOException e) {
                msg.append("\n!!! Couldn't read commit file !!!\n");
                listener.getLogger().println("Couldn't read comment file");
                e.printStackTrace(listener.getLogger());
            }
        }
        return msg.toString();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }


    public static final class DescriptorImpl extends GhprbExtensionDescriptor implements GhprbProjectExtension {

        @Override
        public String getDisplayName() {
            return "Comment File";
        }
        
    }

}
