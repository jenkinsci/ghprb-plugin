package org.jenkinsci.plugins.ghprb;

import com.google.common.annotations.VisibleForTesting;

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
    private Ghprb helper;

    public GhprbRepository(String user, String repository, Ghprb helper) {
        this.reponame = user + "/" + repository;
        this.helper = helper;
    }

    public void init() {
        // make the initial check call to populate our data structures
        initGhRepository();
    }

    private boolean initGhRepository() {
        GitHub gitHub = null;
        try {
            GhprbGitHub repo = helper.getGitHub();
            if (repo == null) {
                return false;
            }
            gitHub = repo.get();
            if (gitHub == null) {
                logger.log(Level.SEVERE, "No connection returned to GitHub server!");
                return false;
            }
            if (gitHub.getRateLimit().remaining == 0) {
                return false;
            }
        } catch (FileNotFoundException ex) {
            logger.log(Level.INFO, "Rate limit API not found.");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error while accessing rate limit API", ex);
            return false;
        }

        if (ghRepository == null) {
            try {
                ghRepository = gitHub.getRepository(reponame);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Could not retrieve GitHub repository named " + reponame + " (Do you have properly set 'GitHub project' field in job configuration?)", ex);
                return false;
            }
        }
        return true;
    }

    public void check() {
        if (!initGhRepository()) {
            return;
        }

        if (helper.isProjectDisabled()) {
            logger.log(Level.FINE, "Project is disabled, not checking github state");
            return;
        }

        List<GHPullRequest> openPulls;
        try {
            openPulls = ghRepository.getPullRequests(GHIssueState.OPEN);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Could not retrieve open pull requests.", ex);
            return;
        }
        
        ConcurrentMap<Integer, GhprbPullRequest> pulls = helper.getTrigger().getPulls();
        
        Set<Integer> closedPulls = new HashSet<Integer>(pulls.keySet());

        for (GHPullRequest pr : openPulls) {
            if (pr.getHead() == null) {
                try {
                    pr = ghRepository.getPullRequest(pr.getNumber());
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
    }

    private void check(GHPullRequest pr) {
        ConcurrentMap<Integer, GhprbPullRequest> pulls = helper.getTrigger().getPulls();
        
        final Integer id = pr.getNumber();
        GhprbPullRequest pull;
        if (pulls.containsKey(id)) {
            pull = pulls.get(id);
        } else {
            pulls.putIfAbsent(id, new GhprbPullRequest(pr, helper, this));
            pull = pulls.get(id);
        }
        pull.check(pr);
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
            getGitHubRepo().getPullRequest(id).comment(comment);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Couldn't add comment to pull request #" + id + ": '" + comment + "'", ex);
        }
    }

    public void closePullRequest(int id) {
        try {
            getGitHubRepo().getPullRequest(id).close();
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
            config.put("url", new URL(getHookUrl()).toExternalForm());
            config.put("insecure_ssl", "1");
            ghRepository.createHook("web", config, HOOK_EVENTS, true);
            return true;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Couldn''t create web hook for repository {0}. Does the user (from global configuration) have admin rights to the repository?", reponame);
            return false;
        }
    }

    private static String getHookUrl() {
        return Jenkins.getInstance().getRootUrl() + GhprbRootAction.URL + "/";
    }

    public GHPullRequest getPullRequest(int id) throws IOException {
        return getGitHubRepo().getPullRequest(id);
    }

    void onIssueCommentHook(IssueComment issueComment) {
        if (helper.isProjectDisabled()) {
            logger.log(Level.FINE, "Not checking comments since build is disabled");
            return;
        }

        int id = issueComment.getIssue().getNumber();
        try {
            logger.log(Level.FINER, "Comment on issue #{0} from {1}: {2}", new Object[] { id, issueComment.getComment().getUser().getLogin(), issueComment.getComment().getBody() });
        } catch (IOException e) {
            logger.log(Level.FINER, "Comment on issue #{0} unable to get user: {2}", new Object[] { id, issueComment.getComment().getBody() });
        }
        if (!"created".equals(issueComment.getAction())) {
            return;
        }

        ConcurrentMap<Integer, GhprbPullRequest> pulls = helper.getTrigger().getPulls();

        try {
            GhprbPullRequest pull = pulls.get(id);

            if (pull == null) {
                pull = new GhprbPullRequest(getGitHubRepo().getPullRequest(id), helper, this);
                pulls.put(id, pull);
            }
            pull.check(issueComment.getComment());
            GhprbTrigger.getDscp().save();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to get pull request #{0} from GitHub for repo {1}!!", new Object[] {id, issueComment.getRepository().getFullName()});
        }
    }

    void onPullRequestHook(PullRequest pr) {

        ConcurrentMap<Integer, GhprbPullRequest> pulls = helper.getTrigger().getPulls();
        
        if ("closed".equals(pr.getAction())) {
            pulls.remove(pr.getNumber());
        } else if (helper.isProjectDisabled()) {
            logger.log(Level.FINE, "Not processing Pull request since the build is disabled");
        } else if ("opened".equals(pr.getAction()) || "reopened".equals(pr.getAction())) {
            GhprbPullRequest pull = pulls.get(pr.getNumber());
            if (pull == null) {
                pulls.putIfAbsent(pr.getNumber(), new GhprbPullRequest(pr.getPullRequest(), helper, this));
                pull = pulls.get(pr.getNumber());
            }
            pull.check(pr.getPullRequest());
        } else if ("synchronize".equals(pr.getAction())) {
            GhprbPullRequest pull = pulls.get(pr.getNumber());
            if (pull == null) {
                pulls.putIfAbsent(pr.getNumber(), new GhprbPullRequest(pr.getPullRequest(), helper, this));
                pull = pulls.get(pr.getNumber());
            }
            if (pull == null) {
                logger.log(Level.SEVERE, "Pull Request #{0} doesn''t exist", pr.getNumber());
                return;
            }
            pull.check(pr.getPullRequest());
        } else {
            logger.log(Level.WARNING, "Unknown Pull Request hook action: {0}", pr.getAction());
        }
        GhprbTrigger.getDscp().save();
    }

    @VisibleForTesting
    void setHelper(Ghprb helper) {
        this.helper = helper;
    }

    public GHRepository getGitHubRepo() {
        if (ghRepository == null) {
            init();
        }
        return ghRepository;
    }
}