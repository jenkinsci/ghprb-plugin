/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.ghprb.extensions.status;

import hudson.Extension;
import hudson.model.Action;
import java.util.logging.Logger;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalDefault;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbProjectExtension;
import org.kohsuke.github.GHRepository;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author mmitche
 */
public class GhprbUpdateQueueStatus extends GhprbExtension implements Action, GhprbGlobalExtension, GhprbProjectExtension, GhprbGlobalDefault {
    @Extension
    public static final GhprbUpdateQueueStatus.DescriptorImpl DESCRIPTOR = new GhprbUpdateQueueStatus.DescriptorImpl();
    private static final Logger logger = Logger.getLogger(GhprbUpdateQueueStatus.class.getName());  
    
    // These fields mark the base state upon which the
    // status update should be constructed (pre-replaced, etc.).  Queue position
    // will be appended.
    private String url;
    private String context;
    private String message;
    private String commitSha;
    private boolean started;
    private transient GHRepository repository;
    
    @DataBoundConstructor
    public GhprbUpdateQueueStatus(String url, String context, String message, String commitSha, GHRepository repository) {
        this.url = url;
        this.context = context;
        this.message = message;
        this.commitSha = commitSha;
        this.repository = repository;
    }
    
    public GHRepository getRepository() {
        return repository;
    }
    
    public String getUrl() {
        return url;
    }
    
    public String getContext() {
        return context;
    }

    public String getMessage() {
        return message;
    }

    public String getCommitSha() {
        return commitSha;
    }
    
    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "Update queue status periodically";
    }

    public String getUrlName() {
        return null;
    }
    
    public boolean getStarted() {
        return started;
    }

    public void setStarted() {
        this.started = true;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public static final class DescriptorImpl extends GhprbExtensionDescriptor
                                             implements GhprbGlobalExtension, GhprbProjectExtension {
        @Override
        public String getDisplayName() {
            return "Update queue status periodically";
        }
    }
}
