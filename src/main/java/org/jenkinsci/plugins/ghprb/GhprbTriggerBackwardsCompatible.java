package org.jenkinsci.plugins.ghprb;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildResultMessage;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildStatus;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbCommentFile;
import org.kohsuke.github.GHCommitState;

import antlr.ANTLRException;
import hudson.model.AbstractProject;
import hudson.triggers.Trigger;
import hudson.util.DescribableList;


public abstract class GhprbTriggerBackwardsCompatible extends Trigger<AbstractProject<?, ?>> {
    
    public abstract DescribableList<GhprbExtension, GhprbExtensionDescriptor> getExtensions();

    public GhprbTriggerBackwardsCompatible(String cron) throws ANTLRException {
        super(cron);
    }
    

    @Deprecated
    protected transient String commentFilePath;
    @Deprecated
    protected transient String msgSuccess;
    @Deprecated
    protected transient String msgFailure;
    
    
    protected void convertPropertiesToExtensions() {
        checkCommentsFile();
        checkBuildStatusMessages();
    }
    
    private void checkBuildStatusMessages() {
        if (!StringUtils.isEmpty(msgFailure) || !StringUtils.isEmpty(msgSuccess)) {
            List<GhprbBuildResultMessage> messages = new ArrayList<GhprbBuildResultMessage>(2);
            if (!StringUtils.isEmpty(msgFailure)) {
                messages.add(new GhprbBuildResultMessage(GHCommitState.FAILURE, msgFailure));
                msgFailure = null;
            }
            if (!StringUtils.isEmpty(msgSuccess)) {
                messages.add(new GhprbBuildResultMessage(GHCommitState.SUCCESS, msgSuccess));
                msgSuccess = null;
            }
            addIfMissing(new GhprbBuildStatus(messages));
        }
    }

    private void checkCommentsFile() {
        if (commentFilePath != null && !commentFilePath.isEmpty()) {
            GhprbCommentFile comments = new GhprbCommentFile(commentFilePath);
            addIfMissing(comments);
//            commentFilePath = null; // TODO: Disable once satisfied with changes.
        }
    }
    
    private void addIfMissing(GhprbExtension ext) {
        if (getExtensions().get(ext.getClass()) == null) {
            getExtensions().add(ext);
        }
    }

    
}
