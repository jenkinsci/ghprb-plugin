package org.jenkinsci.plugins.ghprb;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import hudson.security.csrf.CrumbExclusion;
import jenkins.model.Jenkins;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.io.IOUtils;
import org.kohsuke.github.GHEventPayload.IssueComment;
import org.kohsuke.github.GHEventPayload.PullRequest;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
@Extension
public class GhprbRootAction implements UnprotectedRootAction {
    static final String URL = "ghprbhook";
    private static final Logger logger = Logger.getLogger(GhprbRootAction.class.getName());

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return URL;
    }

    public void doIndex(StaplerRequest req, StaplerResponse resp) {
        String event = req.getHeader("X-GitHub-Event");
        String signature = req.getHeader("X-Hub-Signature");
        String type = req.getContentType();
        String payload = null;
        String body = null;

        if (type.toLowerCase().startsWith("application/json")) {
            body = extractRequestBody(req);
            if (body == null) {
                logger.log(Level.SEVERE, "Can't get request body for application/json.");
                resp.setStatus(StaplerResponse.SC_BAD_REQUEST);
                return;
            }
            payload = body;
        } else if (type.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
            body = extractRequestBody(req);
            if (body == null || body.length() <= 8) {
                logger.log(Level.SEVERE, "Request doesn't contain payload. "
                        + "You're sending url encoded request, so you should pass github payload through 'payload' request parameter");
                resp.setStatus(StaplerResponse.SC_BAD_REQUEST);
                return;
            }
            try {
                String encoding = req.getCharacterEncoding();
                payload = URLDecoder.decode(body.substring(8), encoding != null ? encoding : "UTF-8");
            } catch (UnsupportedEncodingException e) {
                logger.log(Level.SEVERE, "Error while trying to decode the payload");
                resp.setStatus(StaplerResponse.SC_BAD_REQUEST);
                return;
            }
        }

        if (payload == null) {
            logger.log(Level.SEVERE, "Payload is null, maybe content type ''{0}'' is not supported by this plugin. "
                    + "Please use 'application/json' or 'application/x-www-form-urlencoded'",
                    new Object[] { type });
            resp.setStatus(StaplerResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        logger.log(Level.FINE, "Got payload event: {0}", event);
        
        try {
            GitHub gh = GitHub.connectAnonymously();
            
            if ("issue_comment".equals(event)) {
                IssueComment issueComment = getIssueComment(payload, gh);
                GHIssueState state = issueComment.getIssue().getState();
                if (state == GHIssueState.CLOSED) {
                    logger.log(Level.INFO, "Skip comment on closed PR");
                    return;
                }
                
                String repoName = issueComment.getRepository().getFullName();

                logger.log(Level.INFO, "Checking issue comment ''{0}'' for repo {1}", new Object[] { issueComment.getComment(), repoName });
                
                // Switch over to the system security context so that we can iterate through all the projects.
                // Switch back to the current authentication context when done.
                Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
                SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
                
                try {
                    // Loop through all the jobs and get a pull request trigger for each one
                    for (AbstractProject<?, ?> job : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
                        // Attempt to find the trigger
                        GhprbTrigger trigger = job.getTrigger(GhprbTrigger.class);
                        if (trigger == null) {
                            continue;
                        }

                        try {
                            // Check the applicability of this trigger to the repo that sent the messsage
                            if (!trigger.matchRepo(repoName)) {
                                continue;
                            }

                            // Check the signature
                            if (!trigger.getGitHubApiAuth().checkSignature(body, signature)) {
                                continue;
                            }

                            // Attempt to apply the trigger to the comment
                            IssueComment authedComment = getIssueComment(payload, trigger.getGitHub());
                            logger.log(Level.INFO, "Checking comment on PR #{0} for job {1}", new Object[] {issueComment.getIssue().getNumber(), 
                                trigger.getActualProject()});
                            trigger.getRepository().onIssueCommentHook(authedComment);
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Unable to process web hook for: " + (trigger.getActualProject() != null ? trigger.getActualProject() : "NOT STARTED"), e);
                        }
                    }
                }
                finally {
                    // Restore authentication
                    SecurityContextHolder.getContext().setAuthentication(currentAuth);
                }
            } else if ("pull_request".equals(event)) {
                PullRequest pr = getPullRequest(payload, gh);
                String repoName = pr.getRepository().getFullName();

                logger.log(Level.INFO, "Checking PR #{1} for {0}", new Object[] { repoName, pr.getNumber() });

                // Switch over to the system security context so that we can iterate through all the projects.
                // Switch back to the current authentication context when done.
                Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
                SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
        
                try {
                    // Loop through all the jobs and get a pull request trigger for each one
                    for (AbstractProject<?, ?> job : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
                        // Attempt to find the trigger
                        GhprbTrigger trigger = job.getTrigger(GhprbTrigger.class);

                        if (trigger == null) {
                            continue;
                        }

                        try {
                            // Check the applicability of this trigger to the repo that sent the messsage
                            if (!trigger.matchRepo(repoName)) {
                                continue;
                            }

                            // Check the signature
                            if (!trigger.getGitHubApiAuth().checkSignature(body, signature)) {
                                continue;
                            }

                            // Attempt to apply the trigger to the comment
                            PullRequest authedPr = getPullRequest(payload, trigger.getGitHub());
                            logger.log(Level.INFO, "Checking PR #{0} for job {1}", new Object[] {pr.getNumber(), trigger.getActualProject()});
                            trigger.getRepository().onPullRequestHook(authedPr);
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Unable to process web hook for: " + (trigger.getActualProject() != null ? trigger.getActualProject() : "NOT STARTED"), e);
                        }
                    }
                }
                finally {
                    // Restore authentication
                    SecurityContextHolder.getContext().setAuthentication(currentAuth);
                }
            } else {
                logger.log(Level.WARNING, "Request not known");
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to connect to GitHub anonymously", e);
        }
    }
    
    private PullRequest getPullRequest(String payload, GitHub gh) throws IOException {
        PullRequest pr = gh.parseEventPayload(new StringReader(payload), PullRequest.class);
        return pr;
    }
    
    private IssueComment getIssueComment(String payload, GitHub gh) throws IOException {
        IssueComment issueComment = gh.parseEventPayload(new StringReader(payload), IssueComment.class);
        return issueComment;
    }

    private String extractRequestBody(StaplerRequest req) {
        String body = null;
        BufferedReader br = null;
        try {
            br = req.getReader();
            body = IOUtils.toString(br);
        } catch (IOException e) {
            body = null;
        } finally {
            IOUtils.closeQuietly(br);
        }
        return body;
    }

    @Extension
    public static class GhprbRootActionCrumbExclusion extends CrumbExclusion {

        @Override
        public boolean process(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws IOException, ServletException {
            String pathInfo = req.getPathInfo();
            if (pathInfo != null && pathInfo.equals(getExclusionPath())) {
                chain.doFilter(req, resp);
                return true;
            }
            return false;
        }

        public String getExclusionPath() {
            return "/" + URL + "/";
        }
    }
}
