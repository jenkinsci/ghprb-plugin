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
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;

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
                FilePath workspace;
                FilePath path;

                // On custom pipelines, build will be an instance of WorkflowRun
                if (build instanceof WorkflowRun) {
                    FlowExecution exec = ((WorkflowRun) build).getExecution();
                    if (exec == null) {
                        listener.getLogger().println("build was instanceof WorkflowRun but executor was null");
                    } else {
                        // We walk the execution flow as a run can have multiple workspaces
                        FlowGraphWalker w = new FlowGraphWalker(exec);
                        for (FlowNode n : w) {
                            if (n instanceof BlockStartNode) {
                                WorkspaceAction action = n.getAction(WorkspaceAction.class);
                                if (action != null) {
                                    String node = action.getNode().toString();
                                    String nodepath = action.getPath().toString();
                                    listener.getLogger().println("Remote path is " + node + ":" + nodepath + "\n");

                                    if (action.getWorkspace() == null) {
                                        // if the workspace returns null, the workspace either isn't here or it doesn't
                                        // exist - in that case, we fail over to trying to find the comment file locally.
                                        continue;
                                    }

                                    path = action.getWorkspace().child(scriptFilePathResolved);

                                    if (path.exists()) {
                                        content = path.readToString();
                                    } else {
                                        listener.getLogger().println(
                                          "Didn't find comment file in workspace at " + path.absolutize().getRemote()
                                          + ", falling back to file operations on master.\n"
                                        );
                                    }

                                }
                            }
                        }
                    }
                } else if (build instanceof Build<?, ?>) {
                    // When using workers on hosts other than master, we simply get the workspace here.
                    workspace = ((Build<?, ?>) build).getWorkspace();
                    path = workspace.child(scriptFilePathResolved);

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
