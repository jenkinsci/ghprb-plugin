package org.jenkinsci.plugins.ghprb;

import java.io.IOException;
import java.io.StringReader;
import java.util.logging.Level;
import java.util.logging.Logger;

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
                    logger.log(Level.INFO, "Checking issue comment ''{0}'' for repo {1}", 
                            new Object[] { issueComment.getComment(), repo.getName() }
                    );
                    if (repo.checkSignature(body, signature))
                        repo.onIssueCommentHook(issueComment);
                }

            } else if ("pull_request".equals(event)) {
                GHEventPayload.PullRequest pr = gh.parseEventPayload(
                        new StringReader(payload), 
                        GHEventPayload.PullRequest.class);
                if (matchRepo(repo, pr.getPullRequest().getRepository())) {
                    logger.log(Level.INFO, "Checking PR #{1} for {0}", new Object[] { repo.getName(), pr.getNumber() });
                    if (repo.checkSignature(body, signature))
                        repo.onPullRequestHook(pr);
                }
                
            } else {
                logger.log(Level.WARNING, "Request not known");
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to parse github hook payload for " + trigger.getProject(), ex);
        }
    }
    
    private boolean matchRepo(GhprbRepository ghprbRepo, GHRepository ghRepo) {
        return ghprbRepo.getName().equals(ghRepo.getFullName());
    }

    public String getProjectName() {
        return trigger.getProject();
    }

}
