package org.jenkinsci.plugins.ghprb.extensions.comments;

import hudson.Extension;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

import org.jenkinsci.plugins.ghprb.Ghprb;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommentAppender;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbProjectExtension;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class GhprbBuildStatus extends GhprbExtension implements GhprbCommentAppender, GhprbGlobalExtension, GhprbProjectExtension {

    private final String message;
    private final GHCommitState result;

    @DataBoundConstructor
    public GhprbBuildStatus(String message, GHCommitState result) {
        this.message = message;
        this.result = result;
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
            String descriptorMessage = ((DescriptorImpl)getDescriptor()).getMessage();
            if (message != null && !message.isEmpty()) {
                buildMessage = message;
            } else if (descriptorMessage != null && !descriptorMessage.isEmpty()) {
                buildMessage = descriptorMessage;
            }
            if (buildMessage == null) {
                return "";
            }
            String message =  Ghprb.replaceMacros(build, buildMessage);
            // Only Append the build's custom message if it has been set.
            if (message != null && !message.isEmpty()) {
                // When the msg is not empty, append a newline first, to seperate it from the rest of the String
                if (msg.length() > 0) {
                    msg.append("\n");
                }
                msg.append(message);
            }
        }
        return msg.toString();
    }
    

    @Extension
    public static class DescriptorImpl extends GhprbExtensionDescriptor implements GhprbGlobalExtension, GhprbProjectExtension {

        private String message;
        private Result result;
        

        public DescriptorImpl() {
            load();
        }
        
        public Result getResult() {
            return result;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
            save();
        }
        
        public void getResult(Result result) {
            this.result = result;
            save();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            message = formData.getString("message");
            result = Result.fromString(formData.getString("result"));
            save();
            return super.configure(req, formData);
        }
        
        @Override
        public String getDisplayName() {
            return "Add message on Build Status";
        }
        
        public ListBoxModel doFillResultItems(@QueryParameter String result) {
            ListBoxModel items = new ListBoxModel();
            GHCommitState[] results = new GHCommitState[] {GHCommitState.SUCCESS,GHCommitState.ERROR,GHCommitState.FAILURE};
            for (GHCommitState nextResult : results) {

                items.add(nextResult.toString(), nextResult.toString());
                if (result.toString().equals(nextResult)) {
                    items.get(items.size()-1).selected = true;
                } 
            }

            return items;
        }
    }

}
