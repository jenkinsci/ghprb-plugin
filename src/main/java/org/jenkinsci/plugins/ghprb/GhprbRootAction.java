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
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;

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
        String type = req.getContentType();
        String payload = null;

        if ("application/json".equals(type)) {
            BufferedReader br = null;
            try {
                br = req.getReader();
                payload = IOUtils.toString(req.getReader());
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Can't get request body for application/json.");
                return;
            } finally {
                IOUtils.closeQuietly(br);
            }
        } else if ("application/x-www-form-urlencoded".equals(type)) {
            payload = req.getParameter("payload");
            if (payload == null) {
                logger.log(Level.SEVERE, "Request doesn't contain payload. "
                        + "You're sending url encoded request, so you should pass github payload through 'payload' request parameter");
                return;
            }
        }

        if (payload == null) {
            logger.log(Level.SEVERE, "Payload is null, maybe content type '{0}' is not supported by this plugin. "
                    + "Please use 'application/json' or 'application/x-www-form-urlencoded'",
                    new Object[] { type });
            return;
        }

        GhprbGitHub gh = GhprbTrigger.getDscp().getGitHub();

        logger.log(Level.INFO, "Got payload event: {0}", event);
        try {
            if ("issue_comment".equals(event)) {
                GHEventPayload.IssueComment issueComment = gh.get()
                        .parseEventPayload(new StringReader(payload), GHEventPayload.IssueComment.class);
                GHIssueState state = issueComment.getIssue().getState();
                if (state == GHIssueState.CLOSED) {
                    logger.log(Level.INFO, "Skip comment on closed PR");
                    return;
                }

                for (GhprbRepository repo : getRepos(issueComment.getRepository())) {
                    logger.log(Level.INFO, "Checking issue comment ''{0}'' for repo {1}", 
                            new Object[] { issueComment.getComment(), repo.getName() }
                    );
                    repo.onIssueCommentHook(issueComment);
                }
            } else if ("pull_request".equals(event)) {
                GHEventPayload.PullRequest pr = gh.get().parseEventPayload(new StringReader(payload), GHEventPayload.PullRequest.class);
                for (GhprbRepository repo : getRepos(pr.getPullRequest().getRepository())) {
                    logger.log(Level.INFO, "Checking PR #{1} for {0}", new Object[] { repo.getName(), pr.getNumber() });
                    repo.onPullRequestHook(pr);
                }
            } else {
                logger.log(Level.WARNING, "Request not known");
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to parse github hook payload.", ex);
        }
    }

    private Set<GhprbRepository> getRepos(GHRepository repo) {
        return getRepos(repo.getFullName());
    }

    private Set<GhprbRepository> getRepos(String repo) {
        final Set<GhprbRepository> ret = new HashSet<GhprbRepository>();

        // We need this to get access to list of repositories
        Authentication old = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);

        try {
            for (AbstractProject<?, ?> job : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
                GhprbTrigger trigger = job.getTrigger(GhprbTrigger.class);
                if (trigger == null || trigger.getRepository() == null) {
                    continue;
                }
                GhprbRepository r = trigger.getRepository();
                if (repo.equalsIgnoreCase(r.getName())) {
                    ret.add(r);
                }
            }
        } finally {
            SecurityContextHolder.getContext().setAuthentication(old);
        }

        if (ret.size() == 0) {
            logger.log(Level.WARNING, "No repos with plugin trigger found for GitHub repo named {0}", repo);
        }

        return ret;
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
