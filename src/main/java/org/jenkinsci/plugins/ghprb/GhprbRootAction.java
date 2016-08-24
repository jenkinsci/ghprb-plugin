package org.jenkinsci.plugins.ghprb;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import hudson.security.csrf.CrumbExclusion;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * @author Honza Brázdil jbrazdil@redhat.com
 */
@Extension
public class GhprbRootAction implements UnprotectedRootAction {
    static final String URL = "ghprbhook";
    private static final Logger logger = Logger.getLogger(GhprbRootAction.class.getName());
    
    private Set<StartTrigger> triggerThreads;
    private ExecutorService pool;

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return URL;
    }
    
    public int getThreadCount() {
        return triggerThreads == null ? 0 : triggerThreads.size();
    }
    
    public GhprbRootAction() {
        triggerThreads = Collections.newSetFromMap(new WeakHashMap<StartTrigger, Boolean>());
        this.pool = Executors.newCachedThreadPool();
    }

    public void doIndex(StaplerRequest req,
                        StaplerResponse resp) {
        final String event = req.getHeader("X-GitHub-Event");
        final String signature = req.getHeader("X-Hub-Signature");
        final String type = req.getContentType();
        String payload = null;
        String body = null;

        if (type != null && type.toLowerCase().startsWith("application/json")) {
            body = extractRequestBody(req);
            if (body == null) {
                logger.log(Level.SEVERE, "Can't get request body for application/json.");
                resp.setStatus(StaplerResponse.SC_BAD_REQUEST);
                return;
            }
            payload = body;
        } else if (type != null && type.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
            body = extractRequestBody(req);
            if (body == null || body.length() <= 8) {
                logger.log(Level.SEVERE,
                           "Request doesn't contain payload. "
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
            logger.log(Level.SEVERE,
                       "Payload is null, maybe content type ''{0}'' is not supported by this plugin. "
                                     + "Please use 'application/json' or 'application/x-www-form-urlencoded'",
                       new Object[] { type });
            resp.setStatus(StaplerResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        logger.log(Level.FINE, "Got payload event: {0}", event);
        final String threadBody = body;
        final String threadPayload = payload;
        handleAction(event, signature, threadPayload, threadBody);
    }

    private void handleAction(String event,
                              String signature,
                              String payload,
                              String body) {

        // Not sure if this is needed, but it may be to get info about old builds.
        Authentication old = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);

        IssueComment comment = null;
        PullRequest pr = null;
        String repoName = null;

        try {
            GitHub gh = GitHub.connectAnonymously();

            if (StringUtils.equalsIgnoreCase("issue_comment", event)) {

                comment = getIssueComment(payload, gh);
                GHIssueState state = comment.getIssue().getState();

                if (state == GHIssueState.CLOSED) {
                    logger.log(Level.INFO, "Skip comment on closed PR");
                    return;
                }

                if (!comment.getIssue().isPullRequest()) {
                    logger.log(Level.INFO, "Skip comment on Issue");
                    return;
                }

                repoName = comment.getRepository().getFullName();

                logger.log(Level.INFO,
                           "Checking issue comment ''{0}'' for repo {1}",
                           new Object[] { comment.getComment().getBody(), repoName });

            } else if (StringUtils.equalsIgnoreCase("pull_request", event)) {

                pr = getPullRequest(payload, gh);
                repoName = pr.getRepository().getFullName();

                logger.log(Level.INFO, "Checking PR #{1} for {0}", new Object[] { repoName, pr.getNumber() });

            } else {
                logger.log(Level.WARNING, "Request not known for event: {0}", new Object[] { event });
                return;
            }

            Set<GhprbTrigger> triggers = getTriggers(repoName, body, signature);

            handleEvent(triggers, payload, pr, comment);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to connect to GitHub anonymously", e);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(old);
        }
    }

    private class StartTrigger implements Runnable {
        GhprbTrigger trigger;
        PullRequest pr;
        IssueComment comment;

        @Override
        public void run() {
            try {
                if (pr != null) {
                    triggerPr(trigger, pr);
                }

                if (comment != null) {
                    triggerComment(trigger, comment);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to run thread", e);
            } finally {
                triggerThreads.remove(this);
            }
        }
    }

    private void handleEvent(Set<GhprbTrigger> triggers,
                             String payload,
                             PullRequest anonPr,
                             IssueComment anonComment) {

        for (final GhprbTrigger trigger : triggers) {
            try {
                final StartTrigger runner = new StartTrigger();
                runner.trigger = trigger;
                if (anonPr != null) {
                    runner.pr = getPullRequest(payload, trigger.getGitHub());
                }
                if (anonComment != null) {
                    runner.comment = getIssueComment(payload, trigger.getGitHub());
                }

                triggerThreads.add(runner);
                pool.submit(runner);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Unable to get authorized version of event", e);
            }
        }
    }

    private void triggerComment(final GhprbTrigger trigger,
                                final IssueComment comment) {
        new Thread() {
            public void run() {
                try {
                    trigger.handleComment(comment);
                } catch (Exception e) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Unable to handle comment for PR# ");
                    sb.append(comment.getIssue().getId());
                    sb.append(", repo: ");
                    sb.append(comment.getRepository().getFullName());

                    logger.log(Level.SEVERE, sb.toString(), e);
                }
            }
        }.start();
    }

    private void triggerPr(final GhprbTrigger trigger,
                           final PullRequest pr) {
        new Thread() {
            public void run() {
                try {
                    trigger.handlePR(pr);
                } catch (Exception e) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Unable to handle PR# ");
                    sb.append(pr.getNumber());
                    sb.append(" for repo: ");
                    sb.append(pr.getRepository().getFullName());
                    logger.log(Level.SEVERE, sb.toString(), e);
                }
            }
        }.start();
    }

    private PullRequest getPullRequest(String payload,
                                       GitHub gh) throws IOException {
        PullRequest pr = gh.parseEventPayload(new StringReader(payload), PullRequest.class);
        return pr;
    }

    private IssueComment getIssueComment(String payload,
                                         GitHub gh) throws IOException {
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

    private Set<GhprbTrigger> getTriggers(String repoName,
                                          String body,
                                          String signature) {
        Set<GhprbTrigger> triggers = new HashSet<GhprbTrigger>();

        Set<AbstractProject<?, ?>> projects = GhprbTrigger.getDscp().getRepoTriggers(repoName);
        if (projects != null) {
            for (AbstractProject<?, ?> project : projects) {
                GhprbTrigger trigger = Ghprb.extractTrigger(project);
                if (trigger == null) {
                    logger.log(Level.WARNING,
                               "Warning, trigger unexpectedly null for project " + project.getFullName());
                    continue;
                }
                try {
                    if (trigger.matchSignature(body, signature)) {
                        triggers.add(trigger);
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE,
                               "Failed to match signature for trigger on project: " + trigger.getProjectName(),
                               e);
                }
            }
        }
        return triggers;

    }

    @Extension
    public static class GhprbRootActionCrumbExclusion extends CrumbExclusion {

        @Override
        public boolean process(HttpServletRequest req,
                               HttpServletResponse resp,
                               FilterChain chain) throws IOException, ServletException {
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
