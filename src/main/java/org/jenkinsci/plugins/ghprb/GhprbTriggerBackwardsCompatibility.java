package org.jenkinsci.plugins.ghprb;

import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbCommentFile;

import antlr.ANTLRException;
import hudson.model.AbstractProject;
import hudson.triggers.Trigger;
import hudson.util.DescribableList;

public abstract class GhprbTriggerBackwardsCompatibility extends Trigger<AbstractProject<?, ?>> {

    public abstract DescribableList<GhprbExtension, GhprbExtensionDescriptor> getExtensions();

    @Deprecated
    protected transient String commentFilePath;
    
    public GhprbTriggerBackwardsCompatibility(String cron) throws ANTLRException {
        super(cron);
    }
    
    

    protected void readBackExtensionsFromLegacy() {
        checkCommentsFile();
        
    }
    
    private void checkCommentsFile() {
        if (commentFilePath != null && !commentFilePath.isEmpty()) {
            GhprbCommentFile comments = new GhprbCommentFile(commentFilePath);
            addIfMissing(comments);
            commentFilePath = null;
        }
    }
    

    private void addIfMissing(GhprbExtension ext) {
        if (getExtensions().get(ext.getClass()) == null) {
            getExtensions().add(ext);
        }
    }
}
