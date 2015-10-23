package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractProject;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHEventPayload.IssueComment;
import org.kohsuke.github.GHEventPayload.PullRequest;
import org.kohsuke.github.GitHub;

public class GhprbWebHook {
    private static final Logger logger = Logger.getLogger(GhprbWebHook.class.getName());
    
    private final GhprbTrigger trigger;
    
    public GhprbWebHook(GhprbTrigger trigger) {
        this.trigger = trigger;
    }
    
    public GitHub getGitHub() throws IOException {
        return trigger.getGitHub();
    }

    public boolean matchRepo(String hookRepoName) {
        GhprbRepository ghprbRepo = trigger.getRepository();
        String jobRepoName = ghprbRepo.getName();
        logger.log(Level.FINE, "Comparing repository names: {0} to {1}, case is ignored", new Object[] { jobRepoName, hookRepoName });
        return jobRepoName.equalsIgnoreCase(hookRepoName);
    }

    public String getProjectName() {
        AbstractProject<?, ?> project = trigger.getActualProject();
        if (project != null) {
            return project.getFullName();
        } else {
            return "NOT STARTED";
        }
    }

    public void handleComment(IssueComment issueComment) throws IOException {
        GhprbRepository repo = trigger.getRepository();
        
        logger.log(Level.INFO, "Checking comment on PR #{0} for job {1}", new Object[] {issueComment.getIssue().getNumber(), getProjectName()});

        repo.onIssueCommentHook(issueComment);
    }

    public void handlePR(PullRequest pr) throws IOException {
        GhprbRepository repo = trigger.getRepository();

        logger.log(Level.INFO, "Checking PR #{0} for job {1}", new Object[] {pr.getNumber(), getProjectName()});

        repo.onPullRequestHook(pr);
    }

    public boolean checkSignature(String body, String signature) {
        String secret = trigger.getGitHubApiAuth().getSecret();
        if (StringUtils.isEmpty(secret)) {
            return true;
        }
        
        if (signature != null && signature.startsWith("sha1=")) {
            String expected = signature.substring(5);
            String algorithm = "HmacSHA1";
            try {
                SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(), algorithm);
                Mac mac = Mac.getInstance(algorithm);
                mac.init(keySpec);
                byte[] localSignatureBytes = mac.doFinal(body.getBytes("UTF-8"));
                String localSignature = Hex.encodeHexString(localSignatureBytes);
                if (! localSignature.equals(expected)) {
                    logger.log(Level.SEVERE, "Local signature {0} does not match external signature {1}",
                            new Object[] {localSignature, expected});
                    return false;
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Couldn't match both signatures");
                return false;
            }
        } else {
            logger.log(Level.SEVERE, "Request doesn't contain a signature. Check that github has a secret that should be attached to the hook");
            return false;
        }

        logger.log(Level.INFO, "Signatures checking OK");
        return true;
    }

}
