package org.jenkinsci.plugins.ghprb.extensions.comments;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Build;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.ghprb.Ghprb;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommentAppender;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.GhprbProjectExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;

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
        return false;
    }

    public String postBuildComment(Run<?, ?> build, TaskListener listener) {
        StringBuilder msg = new StringBuilder();
        if (commentFilePath != null && !commentFilePath.isEmpty()) {
            String scriptFilePathResolved = Ghprb.replaceMacros(build, listener, commentFilePath);

            try {
                String content = null;
                if (build instanceof Build<?, ?>) {
                    final FilePath workspace = ((Build<?, ?>) build).getWorkspace();
                    final FilePath path = workspace.child(scriptFilePathResolved);

                    if (path.exists()) {
                        content = path.readToString();
                    } else {
                        listener.getLogger().println(
                            "Didn't find comment file in workspace at " + path.absolutize().getRemote()
                            + ", falling back to file operations on master."
                        );
                    }
                }

                if (content == null) {
                    content = FileUtils.readFileToString(new File(scriptFilePathResolved));
                }

                if (content.length() > 0) {
                    msg.append(content);
                }

            } catch (IOException e) {
                msg.append("\n!!! Couldn't read commit file !!!\n");
                listener.getLogger().println("Couldn't read comment file at " + scriptFilePathResolved);
                e.printStackTrace(listener.getLogger());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  // set interrupt flag
                msg.append("\n!!! Couldn't read commit file !!!\n");
                listener.getLogger().println("Reading comment file at " + scriptFilePathResolved + " was interrupted");
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
