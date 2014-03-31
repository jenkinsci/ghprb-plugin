package org.jenkinsci.plugins.ghprb;

import com.google.common.base.Joiner;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maintains state about a Pull Request for a particular Jenkins job.  This is what understands the current state
 * of a PR for a particular job.
 *
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbPullRequest {

    private static final Logger logger = Logger.getLogger(GhprbPullRequest.class.getName());

    private final int id;
    private final GHUser author;
    private String title;
    private Date updated;
    private String head;
    private boolean mergeable;
    private String reponame;
    private String target;
    private String source;
    private String authorEmail;
    private URL url;

    private boolean shouldRun = false;
    private boolean accepted = false;
    private boolean triggered = false;

    private transient Ghprb helper;
    private transient GhprbRepository repo;

    GhprbPullRequest(GHPullRequest pr, Ghprb helper, GhprbRepository repo) {
        id = pr.getNumber();
        updated = pr.getUpdatedAt();
        head = pr.getHead().getSha();
        title = pr.getTitle();
        author = pr.getUser();
        reponame = repo.getName();
        target = pr.getBase().getRef();
        source = pr.getHead().getRef();
        url = pr.getUrl();
        obtainAuthorEmail(pr);

        this.helper = helper;
        this.repo = repo;

        if (helper.isWhitelisted(author)) {
            accepted = true;
            shouldRun = true;
        } else {
            logger.log(Level.INFO, "Author of #{0} {1} on {2} not in whitelist!", new Object[]{id, author.getLogin(), reponame});
            repo.addComment(id, GhprbTrigger.getDscp().getRequestForTestingPhrase());
        }

        logger.log(Level.INFO, "Created Pull Request #{0} on {1} by {2} ({3}) updated at: {4} SHA: {5}", new Object[]{id, reponame, author.getLogin(), authorEmail, updated, head});
    }

    public void init(Ghprb helper, GhprbRepository repo) {
        this.helper = helper;
        this.repo = repo;
        if (reponame == null) {
            reponame = repo.getName(); // If this instance was created before v1.8, it can be null.
        }
    }

    /**
     * Checks this Pull Request representation against a GitHub version of the Pull Request, and triggers
     * a build if necessary.
     *
     * @param pr
     */
    public void check(GHPullRequest pr) {
        if (target == null) {
            target = pr.getBase().getRef(); // If this instance was created before target was introduced (before v1.8), it can be null.
        }
        if (source == null) {
            source = pr.getHead().getRef(); // If this instance was created before target was introduced (before v1.8), it can be null.
        }

        if (isUpdated(pr)) {
            logger.log(Level.INFO, "Pull request #{0} was updated on {1} at {2} by {3}", new Object[]{id, reponame, updated, author});

            // the title could have been updated since the original PR was opened
            title = pr.getTitle();
            int commentsChecked = checkComments(pr);
            boolean newCommit = checkCommit(pr.getHead().getSha());

            if (!newCommit && commentsChecked == 0) {
                logger.log(Level.INFO, "Pull request #{0} was updated on repo {1} but there aren't any new comments nor commits; that may mean that commit status was updated.", new Object[] {id, reponame});
            }
            updated = pr.getUpdatedAt();
        }
        tryBuild(pr);
    }

    public void check(GHIssueComment comment) {
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

        tryBuild(pr);
    }

    public boolean isWhiteListedTargetBranch() {
        List<GhprbBranch> branches = helper.getWhiteListTargetBranches();
        if (branches.isEmpty() || (branches.size() == 1 && branches.get(0).equals(""))) {
            // no branches in white list means we should test all
            return true;
        }
        for (GhprbBranch b : branches) {
            if (b.equals(target)) {
                // the target branch is in the whitelist!
                return true;
            }
        }
        logger.log(Level.FINEST, "PR #{0} target branch: {1} isn't in our whitelist of target branches: {2}", new Object[]{id, target, Joiner.on(',').skipNulls().join(branches)});
        return false;
    }

    private boolean isUpdated(GHPullRequest pr) {
        boolean ret = updated.compareTo(pr.getUpdatedAt()) < 0;
        ret = ret || !pr.getHead().getSha().equals(head);
        return ret;
    }

    private void tryBuild(GHPullRequest pr) {
        if (helper.ifOnlyTriggerPhrase() && !triggered) {
            shouldRun = false;
        }
        if (!isWhiteListedTargetBranch()) {
            return;
        }
        if (shouldRun) {

            if (authorEmail == null) {
                // If this instance was create before authorEmail was introduced (before v1.10), it can be null.
                obtainAuthorEmail(pr);
            }

            if (pr != null) {
                checkMergeable(pr);
            }

            build();

            shouldRun = false;
            triggered = false;
        }
    }

    private void build() {
        String message = helper.getBuilds().build(this);
		repo.createCommitStatus(head, GHCommitState.PENDING, null, message,id);
        logger.log(Level.INFO, message);
    }

    // returns false if no new commit
    private boolean checkCommit(String sha) {
        if (head.equals(sha)) {
            return false;
        }
        logger.log(Level.FINE, "New commit. Sha: {0} => {1}", new Object[]{head, sha});
        head = sha;
        if (accepted) {
            shouldRun = true;
        }
        return true;
    }

    private void checkComment(GHIssueComment comment) throws IOException {
        String sender = comment.getUser().getLogin();
        GHUser senderUser = comment.getUser();
        String body = comment.getBody();

        if (helper.isWhitelistPhrase(body) && helper.isAdmin(sender)) {       // add to whitelist
            if (!helper.isWhitelisted(author)) {
                helper.addWhitelist(author.getLogin());
            }
            accepted = true;
            shouldRun = true;
        } else if (helper.isOktotestPhrase(body) && helper.isAdmin(sender)) {       // ok to test
            accepted = true;
            shouldRun = true;
        } else if (helper.isRetestPhrase(body)) {        // test this please
            if (helper.isAdmin(sender)) {
                shouldRun = true;
            } else if (accepted && helper.isWhitelisted(senderUser)) {
                shouldRun = true;
            }
        } else if (helper.isTriggerPhrase(body)) {      // trigger phrase
            if (helper.isAdmin(sender)) {
                shouldRun = true;
                triggered = true;
            } else if (accepted && helper.isWhitelisted(senderUser)) {
                shouldRun = true;
                triggered = true;
            }
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
}
