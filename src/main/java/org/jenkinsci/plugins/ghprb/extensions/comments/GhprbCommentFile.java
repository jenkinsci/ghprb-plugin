package org.jenkinsci.plugins.ghprb.extensions.comments;

import java.io.File;
import java.io.IOException;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.ghprb.Ghprb;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class GhprbCommentFile extends GhprbExtension implements GhprbCommentAppender {
    
    private final String filePath;

    @DataBoundConstructor
    public GhprbCommentFile(String filePath) {
        this.filePath = filePath;
    }

    public String postBuildComment(AbstractBuild<?, ?> build, TaskListener listener) {
        StringBuilder msg = new StringBuilder();
        if (filePath != null && !filePath.isEmpty()) {
            try {
                String scriptFilePathResolved = Ghprb.replaceMacros(build, filePath);
                
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
    


    @Extension
    public static class DescriptorImpl extends GhprbExtensionDescriptor {

        @Override
        public String getDisplayName() {
            return "Comment File";
        }
        
    }
    

}
