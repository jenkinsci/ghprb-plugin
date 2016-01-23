package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

import org.jenkinsci.plugins.ghprb.extensions.GhprbCommentAppender;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommitStatusException;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildStatus;
import org.kohsuke.github.*;
import org.kohsuke.github.GHEventPayload.IssueComment;
import org.kohsuke.github.GHEventPayload.PullRequest;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbRepository {

    private static final Logger logger = Logger.getLogger(GhprbRepository.class.getName());
    private static final EnumSet<GHEvent> HOOK_EVENTS = EnumSet.of(GHEvent.ISSUE_COMMENT, GHEvent.PULL_REQUEST);

    private final String reponame;

    private GHRepository ghRepository;
    private GhprbTrigger trigger;

    public GhprbRepository(String user, String repository, GhprbTrigger trigger) {
        this.reponame = user + "/" + repository;
        this.trigger = trigger;
    }

    public void init() {
        // make the initial check call to populate our data structures
        initGhRepository();
        
        for (Entry<Integer, GhprbPullRequest> next : trigger.getPullRequests().entrySet()) {
            GhprbPullRequest pull = next.getValue();
            pull.init(trigger.getHelper(), this);
        }
    }

    private boolean initGhRepository() {
        if (ghRepository != null) {
            return true;
        }
        
        GitHub gitHub = null;
        
        try {
            gitHub = trigger.getGitHub();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error while accessing rate limit API", ex);
            return false;
        }
        
        if (gitHub == null) {
            logger.log(Level.SEVERE, "No connection returned to GitHub server!");
            return false;
        }

        try {
            if (gitHub.getRateLimit().remaining == 0) {
                logger.log(Level.INFO, "Exceeded rate limit for repository");
                return false;
            }
        } catch (FileNotFoundException ex) {
            logger.log(Level.INFO, "Rate limit API not found.");
            return false;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error while accessing rate limit API", ex);
            return false;
        }
        

        try {
            ghRepository = gitHub.getRepository(reponame);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Could not retrieve GitHub repository named " + reponame + " (Do you have properly set 'GitHub project' field in job configuration?)", ex);
            return false;
        }
        return true;
    }

    public void check() {
        
        if (!trigger.isActive()) {
            logger.log(Level.FINE, "Project is not active, not checking github state");
            return;
        }
        
        if (!initGhRepository()) {
            return;
        }

        List<GHPullRequest> openPulls;
        try {
            openPulls = getGitHubRepo().getPullRequests(GHIssueState.OPEN);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Could not retrieve open pull requests.", ex);
            return;
        }
        
        Map<Integer, GhprbPullRequest> pulls = trigger.getPullRequests();
        
        Set<Integer> closedPulls = new HashSet<Integer>(pulls.keySet());

        for (GHPullRequest pr : openPulls) {
            if (pr.getHead() == null) { // Not sure if we need this, but leaving it for now.
                try {
                    pr = getGitHubRepo().getPullRequest(pr.getNumber());
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Could not retrieve pr " + pr.getNumber(), ex);
                    return;
                }
            }
            check(pr);
            closedPulls.remove(pr.getNumber());
        }
        

        // remove closed pulls so we don't check them again
        for (Integer id : closedPulls) {
            pulls.remove(id);
        }
        trigger.save();
    }

    private void check(GHPullRequest pr) {
        Map<Integer, GhprbPullRequest> pulls = trigger.getPullRequests();

        final Integer id = pr.getNumber();
        GhprbPullRequest pull;
        if (pulls.containsKey(id)) {
            pull = pulls.get(id);
        } else {
            pulls.put(id, new GhprbPullRequest(pr, trigger.getHelper(), this));
            pull = pulls.get(id);
        }
        pull.check(pr);
        trigger.save();
    }

    public void commentOnFailure(AbstractBuild<?, ?> build, TaskListener listener, GhprbCommitStatusException ex) {
        PrintStream stream = null;
        if (listener != null) {
            stream = listener.getLogger();
        }
        GHCommitState state = ex.getState();
        Exception baseException = ex.getException();
        String newMessage;
        if (baseException instanceof FileNotFoundException) {
            newMessage = "FileNotFoundException means that the credentials Jenkins is using is probably wrong. Or the user account does not have write access to the repo.";
        } else {
            newMessage = "Could not update commit status of the Pull Request on GitHub.";
        }
        if (stream != null) {
            stream.println(newMessage);
            baseException.printStackTrace(stream);
        } else {
            logger.log(Level.INFO, newMessage, baseException);
        }
        if (GhprbTrigger.getDscp().getUseComments()) {

            StringBuilder msg = new StringBuilder(ex.getMessage());

            if (build != null) {
                msg.append("\n");
                GhprbTrigger trigger = Ghprb.extractTrigger(build);
                for (GhprbExtension ext : Ghprb.matchesAll(trigger.getExtensions(), GhprbBuildStatus.class)) {
                    if (ext instanceof GhprbCommentAppender) {
                        msg.append(((GhprbCommentAppender) ext).postBuildComment(build, null));
                    }
                }
            }

            if (GhprbTrigger.getDscp().getUseDetailedComments() || (state == GHCommitState.SUCCESS || state == GHCommitState.FAILURE)) {
                logger.log(Level.INFO, "Trying to send comment.", baseException);
                addComment(ex.getId(), msg.toString());
            }
        } else {
            logger.log(Level.SEVERE, "Could not update commit status of the Pull Request on GitHub.");
        }
    }

    public String getName() {
        return reponame;
    }

    public void addComment(int id, String comment) {
        addComment(id, comment, null, null);
    }

    public void addComment(int id, String comment, AbstractBuild<?, ?> build, TaskListener listener) {
        if (comment.trim().isEmpty())
            return;

        if (build != null && listener != null) {
            try {
                comment = build.getEnvironment(listener).expand(comment);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error", e);
            }
        }

        try {
            GHRepository repo = getGitHubRepo();
            GHPullRequest pr = repo.getPullRequest(id);
            pr.comment(comment);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Couldn't add comment to pull request #" + id + ": '" + comment + "'", ex);
        }
    }

    public void closePullRequest(int id) {
        try {
            GHRepository repo = getGitHubRepo();
            GHPullRequest pr = repo.getPullRequest(id);
            pr.close();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Couldn't close the pull request #" + id + ": '", ex);
        }
    }

    private boolean hookExist() throws IOException {
        GHRepository ghRepository = getGitHubRepo();
        for (GHHook h : ghRepository.getHooks()) {
            if (!"web".equals(h.getName())) {
                continue;
            }
            if (!getHookUrl().equals(h.getConfig().get("url"))) {
                continue;
            }
            return true;
        }
        return false;
    }

    public boolean createHook() {
        if (ghRepository == null) {
            logger.log(Level.INFO, "Repository not available, cannot set pull request hook for repository {0}", reponame);
            return false;
        }
        try {
            if (hookExist()) {
                return true;
            }
            Map<String, String> config = new HashMap<String, String>();
            String secret = getSecret();
            config.put("url", new URL(getHookUrl()).toExternalForm());
            config.put("insecure_ssl", "1");
            if (secret != "") {
             config.put("secret",secret);
            }
            ghRepository.createHook("web", config, HOOK_EVENTS, true);
            return true;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Couldn''t create web hook for repository {0}. Does the user (from global configuration) have admin rights to the repository?", reponame);
            return false;
        }
    }

    private String getSecret() {
        return trigger.getGitHubApiAuth().getSecret();
    }

    private String getHookUrl() {
        String baseUrl = trigger.getGitHubApiAuth().getJenkinsUrl();
        if (baseUrl == null) {
          baseUrl = Jenkins.getInstance().getRootUrl();
        }
        return baseUrl + GhprbRootAction.URL + "/";
    }

    public GHPullRequest getPullRequest(int id) throws IOException {
        return getGitHubRepo().getPullRequest(id);
    }

    void onIssueCommentHook(IssueComment issueComment) throws IOException {
        if (!trigger.isActive()) {
            logger.log(Level.FINE, "Not checking comments since build is disabled");
            return;
        }
        int id = issueComment.getIssue().getNumber();
        logger.log(Level.FINER, "Comment on issue #{0} from {1}: {2}",
                new Object[] { id, issueComment.getComment().getUser(), issueComment.getComment().getBody() });
        if (!"created".equals(issueComment.getAction())) {
            return;
        }

        Map<Integer, GhprbPullRequest> pulls = trigger.getPullRequests();

        GhprbPullRequest pull = pulls.get(id);
        if (pull == null) {
            GHRepository repo = getGitHubRepo();
            GHPullRequest pr = repo.getPullRequest(id);
            pull = new GhprbPullRequest(pr, trigger.getHelper(), this);
            pulls.put(id, pull);
        }
        pull.check(issueComment.getComment());
        trigger.save();
    }

    void onPullRequestHook(PullRequest pr) throws IOException {
        GHPullRequest ghpr = pr.getPullRequest();
        int number = pr.getNumber();
        String action = pr.getAction();

        Map<Integer, GhprbPullRequest> pulls = trigger.getPullRequests();

        if ("closed".equals(action)) {
            pulls.remove(number);
        } else if (!trigger.isActive()) {
            logger.log(Level.FINE, "Not processing Pull request since the build is disabled");
        } else if ("opened".equals(action) || "reopened".equals(action) || "synchronize".equals(action)) {
            GhprbPullRequest pull = pulls.get(number);
            if (pull == null) {
                pull = new GhprbPullRequest(ghpr, trigger.getHelper(), this);
                pulls.put(number, pull);
            }
            pull.check(ghpr);
        } else {
            logger.log(Level.WARNING, "Unknown Pull Request hook action: {0}", action);
        }
    }
    
    public GHRepository getGitHubRepo() {
        if (ghRepository == null) {
            initGhRepository();
        }
        return ghRepository;
    }
}