package org.jenkinsci.plugins.ghprb;

import hudson.BulkChange;
import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Items;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.model.listeners.SaveableListener;
import hudson.util.Secret;
import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommentAppender;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommitStatusException;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildStatus;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload.IssueComment;
import org.kohsuke.github.GHEventPayload.PullRequest;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Honza Brázdil jbrazdil@redhat.com
 */
public class GhprbRepository implements Saveable{

    private static final transient Logger logger = Logger.getLogger(GhprbRepository.class.getName());
    private static final transient EnumSet<GHEvent> HOOK_EVENTS = EnumSet.of(GHEvent.ISSUE_COMMENT, GHEvent.PULL_REQUEST);

    private final String reponame;
    private final Map<Integer, GhprbPullRequest> pullRequests;

    private transient GHRepository ghRepository;
    private transient GhprbTrigger trigger;

    public GhprbRepository(String reponame, GhprbTrigger trigger) {
        this.pullRequests = new ConcurrentHashMap<Integer, GhprbPullRequest>();
        this.reponame = reponame;
        this.trigger = trigger;
    }
    
    public void addPullRequests(Map<Integer, GhprbPullRequest> prs) {
        pullRequests.putAll(prs);
    }
    
    public void init() {
        for (Entry<Integer, GhprbPullRequest> next : pullRequests.entrySet()) {
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

    // This method is used when not running with webhooks.  We pull in the
    // active PRs for the repo associated with the trigger and check the
    // comments/hashes that have been added since the last time we checked.
    public void check() {
        
        if (!trigger.isActive()) {
            logger.log(Level.FINE, "Project is not active, not checking github state");
            return;
        }
        
        if (!initGhRepository()) {
            return;
        }
        
        GHRepository repo = getGitHubRepo();

        List<GHPullRequest> openPulls;
        try {
            openPulls = repo.getPullRequests(GHIssueState.OPEN);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Could not retrieve open pull requests.", ex);
            return;
        }
        
        
        Set<Integer> closedPulls = new HashSet<Integer>(pullRequests.keySet());

        for (GHPullRequest pr : openPulls) {
            if (pr.getHead() == null) { // Not sure if we need this, but leaving it for now.
                try {
                    pr = getActualPullRequest(pr.getNumber());
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
            pullRequests.remove(id);
        }
        try {
            this.save();
        } catch (IOException e) {
           logger.log(Level.SEVERE, "Unable to save repository!", e);
        }
    }

    private void check(GHPullRequest pr) {
        int number = pr.getNumber();
        try {
            GhprbPullRequest pull = getPullRequest(null, number);
            pull.check(pr, false);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to check pr: " + number, e);
        }
        try {
            this.save();
        } catch (IOException e) {
           logger.log(Level.SEVERE, "Unable to save repository!", e);
        }
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
    
    public static Object createHookLock = new Object();

    public boolean createHook() {
        try {
            // Avoid a race to update the hooks in a repo (we could end up with
            // multiple hooks).  Lock on before we try this
            synchronized (createHookLock) {
                if (hookExist()) {
                    return true;
                }
                Map<String, String> config = new HashMap<String, String>();
                String secret = getSecret();
                config.put("url", new URL(getHookUrl()).toExternalForm());
                config.put("insecure_ssl", "1");
                if (!StringUtils.isEmpty(secret)) {
                 config.put("secret",secret);
                }
                getGitHubRepo().createHook("web", config, HOOK_EVENTS, true);
                return true;
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Couldn''t create web hook for repository {0}. Does the user (from global configuration) have admin rights to the repository?", reponame);
            return false;
        }
    }

    private String getSecret() {
        Secret secret = trigger.getGitHubApiAuth().getSecret();
        return secret == null ? "" : secret.getPlainText();
    }

    private String getHookUrl() {
        String baseUrl = trigger.getGitHubApiAuth().getJenkinsUrl();
        if (baseUrl == null) {
          baseUrl = Jenkins.getInstance().getRootUrl();
        }
        return baseUrl + GhprbRootAction.URL + "/";
    }

    public GhprbPullRequest getPullRequest(int id) {
        return pullRequests.get(id);
    }
    
    public GHPullRequest getActualPullRequest(int id) throws IOException {
        return getGitHubRepo().getPullRequest(id);
    }

    void onIssueCommentHook(IssueComment issueComment) throws IOException {
        if (!trigger.isActive()) {
            logger.log(Level.FINE, "Not checking comments since build is disabled");
            return;
        }
        int number = issueComment.getIssue().getNumber();
        logger.log(Level.FINER, "Comment on issue #{0} from {1}: {2}",
                new Object[] { number, issueComment.getComment().getUser(), issueComment.getComment().getBody() });
        
        if (!"created".equals(issueComment.getAction())) {
            return;
        }

        GhprbPullRequest pull = getPullRequest(null, number);
        pull.check(issueComment.getComment());
        try {
            this.save();
        } catch (IOException e) {
           logger.log(Level.SEVERE, "Unable to save repository!", e);
        }
    }
    
    private GhprbPullRequest getPullRequest(GHPullRequest ghpr, Integer number) throws IOException {
        if (number == null) {
            number = ghpr.getNumber();
        }
        synchronized (this) {
            GhprbPullRequest pr = pullRequests.get(number);
            if (pr == null) {
                if (ghpr == null) {
                    GHRepository repo = getGitHubRepo();
                    ghpr = repo.getPullRequest(number);
                }
                pr = new GhprbPullRequest(ghpr, trigger.getHelper(), this);
                pullRequests.put(number, pr);
            }
            
            return pr;
        }
    }

    void onPullRequestHook(PullRequest pr) throws IOException {
        GHPullRequest ghpr = pr.getPullRequest();
        int number = pr.getNumber();
        String action = pr.getAction();

        boolean doSave = false;
        if ("closed".equals(action)) {
            pullRequests.remove(number);
            doSave = true;
        } else if (!trigger.isActive()) {
            logger.log(Level.FINE, "Not processing Pull request since the build is disabled");
        } else if ("opened".equals(action) || "reopened".equals(action) || "synchronize".equals(action)) {
            GhprbPullRequest pull = getPullRequest(ghpr, number);
            pull.check(ghpr, true);
            doSave = true;
        } else {
            logger.log(Level.WARNING, "Unknown Pull Request hook action: {0}", action);
        }
        if (doSave) {
            try {
                this.save();
            } catch (IOException e) {
               logger.log(Level.SEVERE, "Unable to save repository!", e);
            }
        }
    }
    
    public GHRepository getGitHubRepo() {
        if (ghRepository == null) {
            initGhRepository();
        }
        return ghRepository;
    }

    public void load() throws IOException {
        XmlFile xml = getConfigXml(trigger.getActualProject());
        if(xml.exists()){
            xml.unmarshal(this);
        }
        save();
    }

    public void save() throws IOException {
        if(BulkChange.contains(this)) {
            return;
        }
        XmlFile config = getConfigXml(trigger.getActualProject());
        config.write(this);
        SaveableListener.fireOnChange(this, config);
    }

    protected XmlFile getConfigXml(AbstractProject<?, ?> project) throws IOException {
        try {
            String escapedRepoName = URLEncoder.encode(reponame, "UTF8");
            File file = new File(project.getBuildDir() + "/pullrequests", escapedRepoName);
            return Items.getConfigFile(file);
        } catch (UnsupportedEncodingException e) {
            throw new IOException(e);
        }
    }
}