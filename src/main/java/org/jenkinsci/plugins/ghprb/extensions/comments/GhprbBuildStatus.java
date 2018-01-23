package org.jenkinsci.plugins.ghprb.extensions.comments;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.ghprb.Ghprb;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommentAppender;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbProjectExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.List;

public class GhprbBuildStatus extends GhprbExtension implements GhprbCommentAppender, GhprbGlobalExtension, GhprbProjectExtension {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private final List<GhprbBuildResultMessage> messages;

    @DataBoundConstructor
    public GhprbBuildStatus(List<GhprbBuildResultMessage> messages) {
        this.messages = messages;
    }

    public List<GhprbBuildResultMessage> getMessages() {
        return messages == null ? new ArrayList<GhprbBuildResultMessage>(0) : messages;
    }

    public String postBuildComment(Run<?, ?> build, TaskListener listener) {
        StringBuilder msg = new StringBuilder();

        List<GhprbBuildResultMessage> messages = getDescriptor().getMessagesDefault(this);

        for (GhprbBuildResultMessage message : messages) {
            msg.append(message.postBuildComment(build, listener));
        }

        return msg.toString();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public static class DescriptorImpl extends GhprbExtensionDescriptor implements GhprbGlobalExtension, GhprbProjectExtension {

        @Override
        public String getDisplayName() {
            return "Build Status Messages";
        }

        public List<GhprbBuildResultMessage> getMessagesDefault(GhprbBuildStatus local) {
            return Ghprb.getDefaultValue(local, GhprbBuildStatus.class, "getMessages");
        }
    }
}
