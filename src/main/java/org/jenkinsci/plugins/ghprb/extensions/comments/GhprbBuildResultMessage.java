package org.jenkinsci.plugins.ghprb.extensions.comments;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.ghprb.Ghprb;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommentAppender;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildStatus.DescriptorImpl;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class GhprbBuildResultMessage extends AbstractDescribableImpl<GhprbBuildResultMessage> implements GhprbCommentAppender {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    
    private final String message;
    private final GHCommitState result;

    @DataBoundConstructor
    public GhprbBuildResultMessage(GHCommitState result, String message) {
        this.result = result;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public GHCommitState getResult() {
        return result;
    }

    public String postBuildComment(AbstractBuild<?, ?> build, TaskListener listener) {
        StringBuilder msg = new StringBuilder();

        GHCommitState state = Ghprb.getState(build);
        String buildMessage = null;
        if (state == result) {
            buildMessage = message;
            if (StringUtils.isEmpty(buildMessage)) {
                return "";
            }
            String message = Ghprb.replaceMacros(build, buildMessage);
            // Only Append the build's custom message if it has been set.
            if (!StringUtils.isEmpty(message)) {
                // When the msg is not empty, append a newline first, to seperate it from the rest of the String
                if (msg.length() > 0) {
                    msg.append("\n");
                }
                msg.append(message);
                msg.append("\n");
            }
        }
        return msg.toString();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }
    
    public static class DescriptorImpl extends Descriptor<GhprbBuildResultMessage> {

        public boolean isApplicable(Class<?> type) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Add message on Build Status";
        }

        public ListBoxModel doFillResultItems(@QueryParameter String result) {
            ListBoxModel items = new ListBoxModel();
            GHCommitState[] results = new GHCommitState[] { GHCommitState.SUCCESS, GHCommitState.ERROR, GHCommitState.FAILURE };
            for (GHCommitState nextResult : results) {

                items.add(nextResult.toString(), nextResult.toString());
                if (result.toString().equals(nextResult)) {
                    items.get(items.size() - 1).selected = true;
                }
            }

            return items;
        }
    }
}
