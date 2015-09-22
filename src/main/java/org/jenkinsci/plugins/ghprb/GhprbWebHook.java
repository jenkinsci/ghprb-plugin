package org.jenkinsci.plugins.ghprb;

import java.io.IOException;
import java.io.StringReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public class GhprbWebHook {
    private static final Logger logger = Logger.getLogger(GhprbWebHook.class.getName());
    
    private final GhprbTrigger trigger;
    
    public GhprbWebHook(GhprbTrigger trigger) {
        this.trigger = trigger;
    }
    
    public void handleWebHook(String event, String payload, String body, String signature) {

        GhprbRepository repo = trigger.getRepository();
        String repoName = repo.getName();

        logger.log(Level.INFO, "Got payload event: {0}", event);
        try {
            GitHub gh = trigger.getGitHub();
            
            if ("issue_comment".equals(event)) {
                GHEventPayload.IssueComment issueComment = gh.parseEventPayload(
                        new StringReader(payload), 
                        GHEventPayload.IssueComment.class);
                GHIssueState state = issueComment.getIssue().getState();
                if (state == GHIssueState.CLOSED) {
                    logger.log(Level.INFO, "Skip comment on closed PR");
                    return;
                }
                
                if (matchRepo(repo, issueComment.getRepository())) {
                    if (!checkSignature(body, signature, trigger.getGitHubApiAuth().getSecret())){
                        return;
                    }

                    logger.log(Level.INFO, "Checking issue comment ''{0}'' for repo {1}", 
                            new Object[] { issueComment.getComment(), repoName }
                    );
                    repo.onIssueCommentHook(issueComment);
                }

            } else if ("pull_request".equals(event)) {
                GHEventPayload.PullRequest pr = gh.parseEventPayload(
                        new StringReader(payload), 
                        GHEventPayload.PullRequest.class);
                if (matchRepo(repo, pr.getPullRequest().getRepository())) {
                    if (!checkSignature(body, signature, trigger.getGitHubApiAuth().getSecret())){
                        return;
                    }

                    logger.log(Level.INFO, "Checking PR #{1} for {0}", new Object[] { repoName, pr.getNumber() });
                    repo.onPullRequestHook(pr);
                }
                
            } else {
                logger.log(Level.WARNING, "Request not known");
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to parse github hook payload for " + trigger.getProject(), ex);
        }
    }
    
    public static boolean checkSignature(String body, String signature, String secret) {
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
    
    private boolean matchRepo(GhprbRepository ghprbRepo, GHRepository ghRepo) {
        String jobRepoName = ghprbRepo.getName();
        String hookRepoName = ghRepo.getFullName();
        logger.log(Level.FINE, "Comparing repository names: {0} to {1}, case is ignored", new Object[]{jobRepoName, hookRepoName});
        return jobRepoName.equalsIgnoreCase(hookRepoName);
    }

    public String getProjectName() {
        return trigger.getProject();
    }

}
