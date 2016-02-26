package org.jenkinsci.plugins.ghprb;

import com.google.common.base.Joiner;

import hudson.model.AbstractBuild;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitUser;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maintains state about a Pull Request for a particular Jenkins job. This is what understands the current state of a PR
 * for a particular job.
 *
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbPullRequest {

    private static final Logger logger = Logger.getLogger(GhprbPullRequest.class.getName());

    @Deprecated
    @SuppressWarnings("unused")
    private transient GHUser author;
    @Deprecated
    @SuppressWarnings("unused")
    private transient String title;
    @Deprecated
    @SuppressWarnings("unused")
    private transient String reponame;
    @Deprecated
    @SuppressWarnings("unused")
    private transient URL url;
    @Deprecated
    @SuppressWarnings("unused")
    private transient String description;
    @Deprecated
    @SuppressWarnings("unused")
    private transient String target;
    @Deprecated
    @SuppressWarnings("unused")
    private transient String source;
    @Deprecated
    @SuppressWarnings("unused")
    private transient String authorRepoGitUrl;
    @Deprecated
    @SuppressWarnings("unused")
    private transient Boolean changed = true;

    private transient String authorEmail;
    private transient Ghprb helper; // will be refreshed each time GhprbRepository.init() is called
    private transient GhprbRepository repo; // will be refreshed each time GhprbRepository.init() is called

    private transient GHPullRequest pr;

    private transient GHUser triggerSender; // Only needed for a single build
    private transient GitUser commitAuthor; // Only needed for a single build
    private transient String commentBody;

    private transient boolean shouldRun = false; // Declares if we should run the build this time.
    private transient boolean triggered = false; // Only lets us know if the trigger phrase was used for this run
    private transient boolean mergeable = false; // Only works as an easy way to pass the value around for the start of
                                                 // this build

    private final int id;
    private Date updated; // Needed to track when the PR was updated
    private String head;
    private String base;
    private boolean accepted = false; // Needed to see if the PR has been added to the accepted list
    private String lastBuildId;

    private void setUpdated(Date lastUpdateTime) {
        updated = lastUpdateTime;
    }

    private void setHead(String newHead) {
        this.head = StringUtils.isEmpty(newHead) ? head : newHead;
    }

    private void setBase(String newBase) {
        this.base = StringUtils.isEmpty(newBase) ? base : newBase;
    }

    private void setAccepted(boolean shouldRun) {
        accepted = true;
        this.shouldRun = shouldRun;
    }

    public GhprbPullRequest(GHPullRequest pr,
                            Ghprb ghprb,
                            GhprbRepository repo,
                            boolean isNew) {

        id = pr.getNumber();
        setPullRequest(pr);

        this.helper = ghprb;

        this.repo = repo;

        GHUser author = pr.getUser();
        String reponame = repo.getName();

        try {
            if (ghprb.isWhitelisted(getPullRequestAuthor())) {
                setAccepted(true);
            } else {
                logger.log(Level.INFO,
                           "Author of #{0} {1} on {2} not in whitelist!",
                           new Object[] { id, author.getLogin(), reponame });
                repo.addComment(id, GhprbTrigger.getDscp().getRequestForTestingPhrase());
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to get pull request author", e);
        }

        logger.log(Level.INFO,
                   "Created Pull Request #{0} on {1} by {2} ({3}) updated at: {4} SHA: {5}",
                   new Object[] { id, reponame, author.getLogin(), getAuthorEmail(), updated, this.head });
    }

    public void init(Ghprb helper,
                     GhprbRepository repo) {
        this.helper = helper;
        this.repo = repo;
    }

    /**
     * Checks this Pull Request representation against a GitHub version of the Pull Request, and triggers a build if
     * necessary.
     *
     * @param ghpr
     */
    public void check(GHPullRequest ghpr) {
        setPullRequest(ghpr);
        if (helper.isProjectDisabled()) {
            logger.log(Level.FINE, "Project is disabled, ignoring pull request");
            return;
        }

        try {
            getPullRequest(false);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to get the latest copy of the PR from github", e);
            return;
        }

        updatePR(pr, pr.getUser());

        tryBuild();
    }

    private void checkSkipBuild() {
        synchronized (this) {
            String skipBuildPhrase = helper.checkSkipBuild(this.pr);
            if (!StringUtils.isEmpty(skipBuildPhrase)) {
                logger.log(Level.INFO,
                           "Pull request commented with {0} skipBuildPhrase. Hence skipping the build.",
                           skipBuildPhrase);
                shouldRun = false;
            }
        }
    }

    public void check(GHIssueComment comment) {
        if (helper.isProjectDisabled()) {
            logger.log(Level.FINE, "Project is disabled, ignoring comment");
            return;
        }

        synchronized (this) {
            try {
                checkComment(comment);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Couldn't check comment #" + comment.getId(), ex);
                return;
            }

            try {
                GHUser user = null;
                try {
                    user = comment.getUser();
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Couldn't get the user that made the comment", e);
                }
                updatePR(getPullRequest(true), user);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Unable to get a new copy of the pull request!");
            }

            tryBuild();
        }
    }

    private void updatePR(GHPullRequest pr,
                          GHUser user) {
        setPullRequest(pr);

        synchronized (this) {
            Date lastUpdateTime = updated;
            if (isUpdated(pr)) {
                logger.log(Level.INFO,
                           "Pull request #{0} was updated on {1} at {2} by {3}",
                           new Object[] { id, repo.getName(), updated, user });

                // the author of the PR could have been whitelisted since its creation
                if (!accepted && helper.isWhitelisted(pr.getUser())) {
                    logger.log(Level.INFO, "Pull request #{0}'s author has been whitelisted", new Object[] { id });
                    setAccepted(true);
                }

                int commentsChecked = checkComments(pr, lastUpdateTime);
                boolean newCommit = checkCommit(pr);

                if (!newCommit && commentsChecked == 0) {
                    logger.log(Level.INFO,
                               "Pull request #{0} was updated on repo {1} but there aren''t any new comments nor commits; "
                                           + "that may mean that commit status was updated.",
                               new Object[] { id, repo.getName() });
                }
            }
        }
    }

    public boolean isWhiteListedTargetBranch() {
        List<GhprbBranch> branches = helper.getWhiteListTargetBranches();
        if (branches.isEmpty() || (branches.size() == 1 && branches.get(0).getBranch().equals(""))) {
            // no branches in white list means we should test all
            return true;
        }

        String target = getTarget();
        for (GhprbBranch b : branches) {
            if (b.matches(target)) {
                // the target branch is in the whitelist!
                return true;
            }
        }
        logger.log(Level.FINEST,
                   "PR #{0} target branch: {1} isn''t in our whitelist of target branches: {2}",
                   new Object[] { id, target, Joiner.on(',').skipNulls().join(branches) });
        return false;
    }

    private boolean isUpdated(GHPullRequest pr) {
        synchronized (this) {
            Date lastUpdated = new Date();
            boolean ret = false;
            try {
                lastUpdated = pr.getUpdatedAt();
                ret = updated.compareTo(lastUpdated) < 0;
                setUpdated(lastUpdated);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Unable to update last updated date", e);
            }
            GHCommitPointer pointer = pr.getHead();
            String pointerSha = pointer.getSha();
            ret |= !pointerSha.equals(head);

            pointer = pr.getBase();
            pointerSha = pointer.getSha();
            ret |= !pointerSha.equals(base);

            return ret;
        }
    }

    private void tryBuild() {
        synchronized (this) {
            checkSkipBuild();
            if (helper.isProjectDisabled()) {
                logger.log(Level.FINEST, "Project is disabled, not trying to build");
                shouldRun = false;
                triggered = false;
            }
            if (helper.ifOnlyTriggerPhrase() && !triggered) {
                logger.log(Level.FINEST, "Trigger only phrase but we are not triggered");
                shouldRun = false;
            }
            triggered = false; // Once we have decided that we are triggered then the flag should be set to false.

            if (!isWhiteListedTargetBranch()) {
                logger.log(Level.FINEST, "Branch is not whitelisted, skipping the build");
                return;
            }
            if (shouldRun) {
                shouldRun = false; // Change the shouldRun flag as soon as we decide to build.
                logger.log(Level.FINEST, "Running the build");

                if (pr != null) {
                    logger.log(Level.FINEST, "PR is not null, checking if mergable");
                    checkMergeable();
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
            }
        }
    }

    private void build() {
        GhprbBuilds builder = helper.getBuilds();
        builder.build(this, triggerSender, commentBody);
    }

    // returns false if no new commit
    private boolean checkCommit(GHPullRequest pr) {
        GHCommitPointer head = pr.getHead();
        GHCommitPointer base = pr.getBase();

        String headSha = head.getSha();
        String baseSha = base.getSha();

        if (StringUtils.equals(headSha, this.head) && StringUtils.equals(baseSha, this.base)) {
            return false;
        }

        logger.log(Level.FINE,
                   "New commit. Sha: Head[{0} => {1}] Base[{2} => {3}]",
                   new Object[] { this.head, headSha, this.base, baseSha });

        setHead(headSha);
        setBase(baseSha);

        if (accepted) {
            shouldRun = true;
        }
        return true;
    }

    private void checkComment(GHIssueComment comment) throws IOException {
        GHUser sender = comment.getUser();
        String body = comment.getBody();

        logger.log(Level.FINEST, "[{0}] Added comment: {1}", new Object[] { sender.getName(), body });

        // Disabled until more advanced configs get set up
        // ignore comments from bot user, this fixes an issue where the bot would auto-whitelist
        // a user or trigger a build when the 'request for testing' phrase contains the
        // whitelist/trigger phrase and the bot is a member of a whitelisted organisation
        // if (helper.isBotUser(sender)) {
        // logger.log(Level.INFO, "Comment from bot user {0} ignored.", sender);
        // return;
        // }

        if (helper.isWhitelistPhrase(body) && helper.isAdmin(sender)) { // add to whitelist
            GHIssue parent = comment.getParent();
            GHUser author = parent.getUser();
            if (!helper.isWhitelisted(author)) {
                logger.log(Level.FINEST, "Author {0} not whitelisted, adding to whitelist.", author);
                helper.addWhitelist(author.getLogin());
            }
            setAccepted(true);
        } else if (helper.isOktotestPhrase(body) && helper.isAdmin(sender)) { // ok to test
            logger.log(Level.FINEST, "Admin {0} gave OK to test", sender);
            setAccepted(true);
        } else if (helper.isRetestPhrase(body)) { // test this please
            logger.log(Level.FINEST, "Retest phrase");
            if (helper.isAdmin(sender)) {
                logger.log(Level.FINEST, "Admin {0} gave retest phrase", sender);
                shouldRun = true;
            } else if (accepted && helper.isWhitelisted(sender)) {
                logger.log(Level.FINEST, "Retest accepted and user {0} is whitelisted", sender);
                shouldRun = true;
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

    private int checkComments(GHPullRequest ghpr,
                              Date lastUpdatedTime) {
        int count = 0;
        logger.log(Level.FINEST, "Checking for comments after: {0}", lastUpdatedTime);
        try {
            for (GHIssueComment comment : ghpr.getComments()) {
                logger.log(Level.FINEST, "Comment was made at: {0}", comment.getUpdatedAt());
                if (lastUpdatedTime.compareTo(comment.getUpdatedAt()) < 0) {
                    logger.log(Level.FINEST, "Comment was made after last update time, {0}", comment.getBody());
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

    public boolean checkMergeable() {
        try {
            getPullRequest(false);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to get a copy of the PR from github", e);
            return mergeable;
        }
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
            }
            mergeable = isMergeable != null && isMergeable;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Couldn't obtain mergeable status.", e);
        }
        return mergeable;
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

    public String getAuthorRepoGitUrl() {
        GHCommitPointer prHead = pr.getHead();
        String authorRepoGitUrl = "";

        if (prHead != null && prHead.getRepository() != null) {
            authorRepoGitUrl = prHead.getRepository().gitHttpTransportUrl();
        }
        return authorRepoGitUrl;
    }

    public boolean isMergeable() {
        return mergeable;
    }

    /**
     * Base and Ref are part of the PullRequest object
     * 
     * @return
     */
    public String getTarget() {
        try {
            return getPullRequest(false).getBase().getRef();
        } catch (IOException e) {
            return "UNKNOWN";
        }
    }

    /**
     * Head and Ref are part of the PullRequest object
     * 
     * @return
     */
    public String getSource() {
        try {
            return getPullRequest(false).getHead().getRef();
        } catch (IOException e) {
            return "UNKNOWN";
        }
    }

    /**
     * Title is part of the PullRequest object
     * 
     * @return
     */
    public String getTitle() {
        try {
            return getPullRequest(false).getTitle();
        } catch (IOException e) {
            return "UNKNOWN";
        }
    }

    /**
     * Returns the URL to the Github Pull Request.
     * This URL is part of the pull request object
     *
     * @return the Github Pull Request URL
     */
    public URL getUrl() throws IOException {
        return getPullRequest(false).getHtmlUrl();
    }

    /**
     * The description body is part of the PullRequest object
     * 
     * @return
     */
    public String getDescription() {
        try {
            return getPullRequest(false).getBody();
        } catch (IOException e) {
            return "UNKNOWN";
        }
    }

    public GitUser getCommitAuthor() {
        return commitAuthor;
    }

    /**
     * Author is part of the PullRequest Object
     * 
     * @return
     * @throws IOException
     */
    public GHUser getPullRequestAuthor() throws IOException {
        return getPullRequest(false).getUser();
    }

    /**
     * Get the PullRequest object for this PR
     * 
     * @param force - forces the code to go get the PullRequest from GitHub now
     * @return
     * @throws IOException
     */
    public GHPullRequest getPullRequest(boolean force) throws IOException {
        if (this.pr == null || force) {
            setPullRequest(repo.getActualPullRequest(this.id));
        }
        return pr;
    }

    private void setPullRequest(GHPullRequest pr) {
        if (pr == null) {
            return;
        }
        synchronized (this) {
            this.pr = pr;

            try {
                if (updated == null) {
                    setUpdated(pr.getCreatedAt());
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Unable to get date for new PR", e);
                setUpdated(new Date());
            }

            if (StringUtils.isEmpty(this.head)) {
                GHCommitPointer prHead = pr.getHead();
                setHead(prHead.getSha());
            }

            if (StringUtils.isEmpty(this.base)) {
                GHCommitPointer prBase = pr.getBase();
                setBase(prBase.getSha());
            }
        }
    }

    /**
     * Email address is collected from GitHub as extra information, so lets cache it.
     * 
     * @return
     */
    public String getAuthorEmail() {
        if (StringUtils.isEmpty(authorEmail)) {
            try {
                GHUser user = getPullRequestAuthor();
                authorEmail = user.getEmail();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Unable to fetch author info for " + id);
            }
        }
        authorEmail = StringUtils.isEmpty(authorEmail) ? "" : authorEmail;
        return authorEmail;
    }

    public void setBuild(AbstractBuild<?, ?> build) {
        lastBuildId = build.getId();
    }

    public String getLastBuildId() {
        return lastBuildId;
    }
}
