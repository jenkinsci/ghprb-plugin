package org.jenkinsci.plugins.ghprb;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitUser;

import hudson.model.Cause;

import java.net.URL;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbCause extends Cause {
    private final String commit;
    private final int pullID;
    private final boolean merged;
    private final String targetBranch;
    private final String sourceBranch;
    private final String authorEmail;
    private final String title;
    private final URL url;
    private final GHUser triggerSender;
    private final String commentBody;
    private final GitUser commitAuthor;

    public GhprbCause(String commit, 
            int pullID, 
            boolean merged, 
            String targetBranch, 
            String sourceBranch, 
            String authorEmail, 
            String title, 
            URL url, 
            GHUser triggerSender, 
            String commentBody,
            GitUser commitAuthor) {

        this.commit = commit;
        this.pullID = pullID;
        this.merged = merged;
        this.targetBranch = targetBranch;
        this.sourceBranch = sourceBranch;
        this.authorEmail = authorEmail;
        this.title = title;
        this.url = url;

        this.triggerSender = triggerSender;
        this.commentBody = commentBody;
        this.commitAuthor = commitAuthor;
    }

    @Override
    public String getShortDescription() {
        return "GitHub pull request #" + pullID + " of commit " + commit + (merged ? ", no merge conflicts." : ", has merge conflicts.");
    }

    public String getCommit() {
        return commit;
    }

    public boolean isMerged() {
        return merged;
    }

    public int getPullID() {
        return pullID;
    }

    public String getTargetBranch() {
        return targetBranch;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public URL getUrl() {
        return url;
    }

    public GHUser getTriggerSender() {
        return triggerSender;
    }

    public String getCommentBody() {
        return commentBody;
    }

    /**
     * Returns the title of the cause, not null.
     * 
     * @return
     */
    public String getTitle() {
        return title != null ? title : "";
    }

    /**
     * Returns at most the first 30 characters of the title, or
     * 
     * @return
     */
    public String getAbbreviatedTitle() {
        return StringUtils.abbreviate(getTitle(), 30);
    }

    public GitUser getCommitAuthor() {
        return commitAuthor;
    }

}
