package org.jenkinsci.plugins.ghprb;

import com.google.common.base.Joiner;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitUser;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Maintains state about a Pull Request for a particular Jenkins job. This is what understands the current state of a PR for a particular job.
 *
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbPullRequest {

    private static final Logger logger = Logger.getLogger(GhprbPullRequest.class.getName());

    private final int id;
    private final GHUser author;
    private final GHPullRequest pr;
    private String title;
    private Date updated;
    private String head;
    private boolean mergeable;
    private String reponame;
    private String target;
    private String source;
    private String authorEmail;
    private URL url;

    private GHUser triggerSender;
    private GitUser commitAuthor;

    private boolean shouldRun = false;
    private boolean accepted = false;
    private boolean triggered = false;

    private transient Ghprb helper;
    private transient GhprbRepository repo;

    private String commentBody;

    GhprbPullRequest(GHPullRequest pr, Ghprb helper, GhprbRepository repo) {
        id = pr.getNumber();
        try {
            updated = pr.getUpdatedAt();
        } catch (IOException e) {
            e.printStackTrace();
            updated = new Date();
        }
        head = pr.getHead().getSha();
        title = pr.getTitle();
        author = pr.getUser();
        reponame = repo.getName();
        target = pr.getBase().getRef();
        source = pr.getHead().getRef();
        url = pr.getHtmlUrl();
        this.pr = pr;
        obtainAuthorEmail(pr);

        this.helper = helper;
        this.repo = repo;

        if (helper.isWhitelisted(author)) {
            accepted = true;
            shouldRun = true;
        } else {
            logger.log(Level.INFO, "Author of #{0} {1} on {2} not in whitelist!", new Object[] { id, author.getLogin(), reponame });
            repo.addComment(id, GhprbTrigger.getDscp().getRequestForTestingPhrase());
        }

        logger.log(Level.INFO, "Created Pull Request #{0} on {1} by {2} ({3}) updated at: {4} SHA: {5}", 
                new Object[] { id, reponame, author.getLogin(), authorEmail, updated, head }
        );
    }

    public void init(Ghprb helper, GhprbRepository repo) {
        this.helper = helper;
        this.repo = repo;
        if (reponame == null) {
            reponame = repo.getName(); // If this instance was created before v1.8, it can be null.
        }
    }

    /**
     * Returns skip build phrases from Jenkins global configuration
     * 
     * @return
     */
    public Set<String> getSkipBuildPhrases() {
        return new HashSet<String>(Arrays.asList(GhprbTrigger.getDscp().getSkipBuildPhrase().split("[\\r\\n]+")));
    }

    /**
     * Checks for skip build phrase in pull request comment. If present it updates shouldRun as false.
     * 
     * @param issue
     */
    private void checkSkipBuild(GHIssue issue) {
        // check for skip build phrase.
        String pullRequestBody = issue.getBody();
        if (StringUtils.isNotBlank(pullRequestBody)) {
            pullRequestBody = pullRequestBody.trim();
            Set<String> skipBuildPhrases = getSkipBuildPhrases();
            skipBuildPhrases.remove("");

            for (String skipBuildPhrase : skipBuildPhrases) {
                skipBuildPhrase = skipBuildPhrase.trim();
                Pattern skipBuildPhrasePattern = Pattern.compile(skipBuildPhrase, Pattern.CASE_INSENSITIVE);
                if (skipBuildPhrasePattern.matcher(pullRequestBody).matches()) {
                    logger.log(Level.INFO, "Pull request commented with {0} skipBuildPhrase. Hence skipping the build.", skipBuildPhrase);
                    shouldRun = false;
                    break;
                }
            }
        }
    }

    /**
     * Checks this Pull Request representation against a GitHub version of the Pull Request, and triggers a build if necessary.
     *
     * @param pr
     */
    public void check(GHPullRequest pr) {
        if (helper.isProjectDisabled()) {
            logger.log(Level.FINE, "Project is disabled, ignoring pull request");
            return;
        }
        if (target == null) {
            target = pr.getBase().getRef(); // If this instance was created before target was introduced (before v1.8), it can be null.
        }
        if (source == null) {
            source = pr.getHead().getRef(); // If this instance was created before target was introduced (before v1.8), it can be null.
        }

        if (isUpdated(pr)) {
            logger.log(Level.INFO, "Pull request #{0} was updated on {1} at {2} by {3}", new Object[] { id, reponame, updated, author });

            // the title could have been updated since the original PR was opened
            title = pr.getTitle();
            int commentsChecked = checkComments(pr);
            boolean newCommit = checkCommit(pr.getHead().getSha());

            if (!newCommit && commentsChecked == 0) {
                logger.log(Level.INFO, "Pull request #{0} was updated on repo {1} but there aren''t any new comments nor commits; "
                        + "that may mean that commit status was updated.", 
                        new Object[] { id, reponame }
                );
            }
            try {
                updated = pr.getUpdatedAt();
            } catch (IOException e) {
                e.printStackTrace();
                updated = new Date();
            }
        }
        checkSkipBuild(pr);
        tryBuild(pr);
    }

    public void check(GHIssueComment comment) {
        if (helper.isProjectDisabled()) {
            logger.log(Level.FINE, "Project is disabled, ignoring comment");
            return;
        }
        try {
            checkComment(comment);
            updated = comment.getUpdatedAt();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Couldn't check comment #" + comment.getId(), ex);
            return;
        }

        GHPullRequest pr = null;
        try {
            pr = repo.getPullRequest(id);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Couldn't get GHPullRequest for checking mergeable state");
        }
        checkSkipBuild(comment.getParent());
        tryBuild(pr);
    }

    public boolean isWhiteListedTargetBranch() {
        List<GhprbBranch> branches = helper.getWhiteListTargetBranches();
        if (branches.isEmpty() || (branches.size() == 1 && branches.get(0).getBranch().equals(""))) {
            // no branches in white list means we should test all
            return true;
        }
        for (GhprbBranch b : branches) {
            if (b.matches(target)) {
                // the target branch is in the whitelist!
                return true;
            }
        }
        logger.log(Level.FINEST, "PR #{0} target branch: {1} isn''t in our whitelist of target branches: {2}", 
                new Object[] { id, target, Joiner.on(',').skipNulls().join(branches) }
        );
        return false;
    }

    private boolean isUpdated(GHPullRequest pr) {
        Date lastUpdated = new Date();
        try {
            lastUpdated = pr.getUpdatedAt();
        } catch (IOException e) {
            e.printStackTrace();
        }
        boolean ret = updated.compareTo(lastUpdated) < 0;
        ret = ret || !pr.getHead().getSha().equals(head);
        return ret;
    }

    private void tryBuild(GHPullRequest pr) {
        if (helper.isProjectDisabled()) {
            logger.log(Level.FINEST, "Project is disabled, not trying to build");
            shouldRun = false;
            triggered = false;
        }
        if (helper.ifOnlyTriggerPhrase() && !triggered) {
            logger.log(Level.FINEST, "Trigger only phrase but we are not triggered");
            shouldRun = false;
        }
        if (!isWhiteListedTargetBranch()) {
            return;
        }
        if (shouldRun) {
            logger.log(Level.FINEST, "Running the build");

            if (authorEmail == null) {
                // If this instance was create before authorEmail was introduced (before v1.10), it can be null.
                obtainAuthorEmail(pr);
                logger.log(Level.FINEST, "Author email was not set, trying to set it to {0}", authorEmail);
            }

            if (pr != null) {
                logger.log(Level.FINEST, "PR is not null, checking if mergable");
                checkMergeable(pr);
                try {
                    for (GHPullRequestCommitDetail commitDetails : pr.listCommits()) {
                        if (commitDetails.getSha().equals(getHead())) {
                            commitAuthor = commitDetails.getCommit().getCommitter();
                            break;
                        }
                    }
                } catch (Exception ex) {
                    logger.log(Level.INFO, "Unable to get PR commits: ", ex);
                }

            }

            logger.log(Level.FINEST, "Running build...");
            build();

            shouldRun = false;
            triggered = false;
        }
    }

    private void build() {
        String message = helper.getBuilds().build(this, triggerSender, commentBody);
        String context = helper.getTrigger().getCommitStatusContext();
        repo.createCommitStatus(head, GHCommitState.PENDING, null, message, id, context);
        logger.log(Level.INFO, message);
    }

    // returns false if no new commit
    private boolean checkCommit(String sha) {
        if (head.equals(sha)) {
            return false;
        }
        logger.log(Level.FINE, "New commit. Sha: {0} => {1}", new Object[] { head, sha });
        head = sha;
        if (accepted) {
            shouldRun = true;
        }
        return true;
    }

    private void checkComment(GHIssueComment comment) throws IOException {
        GHUser sender = comment.getUser();
        String body = comment.getBody();

        // Disabled until more advanced configs get set up
        // ignore comments from bot user, this fixes an issue where the bot would auto-whitelist
        // a user or trigger a build when the 'request for testing' phrase contains the
        // whitelist/trigger phrase and the bot is a member of a whitelisted organisation
        // if (helper.isBotUser(sender)) {
        // logger.log(Level.INFO, "Comment from bot user {0} ignored.", sender);
        // return;
        // }

        if (helper.isWhitelistPhrase(body) && helper.isAdmin(sender)) { // add to whitelist
            if (!helper.isWhitelisted(author)) {
                logger.log(Level.FINEST, "Author {0} not whitelisted, adding to whitelist.", author);
                helper.addWhitelist(author.getLogin());
            }
            accepted = true;
            shouldRun = true;
        } else if (helper.isOktotestPhrase(body) && helper.isAdmin(sender)) { // ok to test
            logger.log(Level.FINEST, "Admin {0} gave OK to test", sender);
            accepted = true;
            shouldRun = true;
        } else if (helper.isRetestPhrase(body)) { // test this please
            logger.log(Level.FINEST, "Retest phrase");
            if (helper.isAdmin(sender)) {
                logger.log(Level.FINEST, "Admin {0} gave retest phrase", sender);
                shouldRun = true;
            } else if (accepted && helper.isWhitelisted(sender)) {
                logger.log(Level.FINEST, "Retest accepted and user {0} is whitelisted", sender);
                shouldRun = true;
                triggered = true;
            }
        } else if (helper.isTriggerPhrase(body)) { // trigger phrase
            logger.log(Level.FINEST, "Trigger phrase");
            if (helper.isAdmin(sender)) {
                logger.log(Level.FINEST, "Admin {0} ran trigger phrase", sender);
                shouldRun = true;
                triggered = true;
            } else if (accepted && helper.isWhitelisted(sender)) {
                logger.log(Level.FINEST, "Trigger accepted and user {0} is whitelisted", sender);
                shouldRun = true;
                triggered = true;
            }
        }

        if (shouldRun) {
            triggerSender = sender;
            commentBody = body;
        }
    }

    private int checkComments(GHPullRequest pr) {
        int count = 0;
        try {
            for (GHIssueComment comment : pr.getComments()) {
                if (updated.compareTo(comment.getUpdatedAt()) < 0) {
                    count++;
                    try {
                        checkComment(comment);
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, "Couldn't check comment #" + comment.getId(), ex);
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Couldn't obtain comments.", e);
        }
        return count;
    }

    private void checkMergeable(GHPullRequest pr) {
        try {
            int r = 5;
            Boolean isMergeable = pr.getMergeable();
            while (isMergeable == null && r-- > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    break;
                }
                isMergeable = pr.getMergeable();
                pr = repo.getPullRequest(id);
            }
            mergeable = isMergeable != null && isMergeable;
        } catch (IOException e) {
            mergeable = false;
            logger.log(Level.SEVERE, "Couldn't obtain mergeable status.", e);
        }
    }

    private void obtainAuthorEmail(GHPullRequest pr) {
        try {
            authorEmail = pr.getUser().getEmail();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Couldn't obtain author email.", e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GhprbPullRequest)) {
            return false;
        }
        GhprbPullRequest o = (GhprbPullRequest) obj;
        return o.id == id;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + this.id;
        return hash;
    }

    public int getId() {
        return id;
    }

    public String getHead() {
        return head;
    }

    public boolean isMergeable() {
        return mergeable;
    }

    public String getTarget() {
        return target;
    }

    public String getSource() {
        return source;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public String getTitle() {
        return title;
    }

    /**
     * Returns the URL to the Github Pull Request.
     *
     * @return the Github Pull Request URL
     */
    public URL getUrl() {
        return url;
    }

    public GitUser getCommitAuthor() {
        return commitAuthor;
    }

    public GHPullRequest getPullRequest() {
        return pr;
    }
}
