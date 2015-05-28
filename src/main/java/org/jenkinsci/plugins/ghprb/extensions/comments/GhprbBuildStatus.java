package org.jenkinsci.plugins.ghprb.extensions.comments;

import java.util.ArrayList;
import java.util.List;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;

import org.jenkinsci.plugins.ghprb.GhprbTrigger;
import org.jenkinsci.plugins.ghprb.GhprbTrigger.DescriptorImpl;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommentAppender;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbProjectExtension;
import org.kohsuke.stapler.DataBoundConstructor;

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
    

    public String postBuildComment(AbstractBuild<?, ?> build, TaskListener listener) {
        StringBuilder msg = new StringBuilder();
        
        for (GhprbBuildResultMessage messager: messages) {
            msg.append(messager.postBuildComment(build, listener));
        }
        
        return msg.toString();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public static class DescriptorImpl extends GhprbExtensionDescriptor implements GhprbGlobalExtension, GhprbProjectExtension {
        
        public final Class<GhprbBuildResultMessage> resultMessageClazz = GhprbBuildResultMessage.class;
        public final GhprbBuildResultMessage.DescriptorImpl messageIt = new GhprbBuildResultMessage.DescriptorImpl();

        @Override
        public String getDisplayName() {
            return "Build Status Messages";
        }
        
        public List<GhprbBuildResultMessage.DescriptorImpl> getBuildResultMessages() {
            List<GhprbBuildResultMessage.DescriptorImpl> list = new ArrayList<GhprbBuildResultMessage.DescriptorImpl>(1);
            list.add(new GhprbBuildResultMessage.DescriptorImpl());
            return list;
        }
        

        public List<GhprbBuildResultMessage> getMessageList(List<GhprbBuildResultMessage> messages) {
            List<GhprbBuildResultMessage> newMessages = new ArrayList<GhprbBuildResultMessage>(10);
            if (messages != null){
                newMessages.addAll(messages);
            } else {
                for(GhprbExtension extension : GhprbTrigger.getDscp().getExtensions()) {
                    if (extension instanceof GhprbBuildStatus) {
                        newMessages.addAll(((GhprbBuildStatus)extension).getMessages());
                    }
                }
            }
            return newMessages;
        }
        
    }
}
