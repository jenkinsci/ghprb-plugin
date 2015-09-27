package org.jenkinsci.plugins.ghprb;

import com.google.common.base.Joiner;

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
 * Maintains state about a Pull Request for a particular Jenkins job. This is what understands the current state of a PR for a particular job.
 *
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbPullRequest {

    private static final Logger logger = Logger.getLogger(GhprbPullRequest.class.getName());
    
    @Deprecated @SuppressWarnings("unused") private transient GHUser author;
    @Deprecated @SuppressWarnings("unused") private transient String title;
    @Deprecated @SuppressWarnings("unused") private transient String reponame;
    @Deprecated @SuppressWarnings("unused") private transient String authorEmail;
    @Deprecated @SuppressWarnings("unused") private transient URL url;
    @Deprecated @SuppressWarnings("unused") private transient String description;

    private final int id;
   
    private Date updated; // Needed to track when the PR was updated
    private String target;
    private String source;
    private String head;
    
    private boolean accepted = false; // Needed to see if the PR has been added to the accepted list


    private transient String authorRepoGitUrl; // Can be refreshed as needed.
    private transient Ghprb helper; // will be refreshed each time GhprbRepository.init() is called
    private transient GhprbRepository repo; // will be refreshed each time GhprbRepository.init() is called
    private transient GHPullRequest pr; // will be refreshed each time GhprbRepository.init() is called

    private transient GHUser triggerSender; // Only needed for a single build
    private transient GitUser commitAuthor; // Only needed for a single build
    
    private transient boolean shouldRun = false; // Declares if we should run the build this time.
    private transient boolean triggered = false; // Only lets us know if the trigger phrase was used for this run
    private transient boolean mergeable = false; // Only works as an easy way to pass the value around for the start of this build

    private String commentBody;

    public GhprbPullRequest(GHPullRequest pr, Ghprb helper, GhprbRepository repo) {
        id = pr.getNumber();
        try {
            updated = pr.getUpdatedAt();
        } catch (IOException e) {
            e.printStackTrace();
            updated = new Date();
        }
        GHCommitPointer prHead = pr.getHead();
        head = prHead.getSha();
        source = prHead.getRef();
        target = pr.getBase().getRef();
        
        this.pr = pr;

        this.helper = helper;
        this.repo = repo;
        
        GHUser author = pr.getUser();
        String reponame = repo.getName();
        

        if (prHead != null && prHead.getRepository() != null) {
            authorRepoGitUrl = prHead.getRepository().gitHttpTransportUrl();
        }

        if (helper.isWhitelisted(getPullRequestAuthor())) {
            accepted = true;
            shouldRun = true;
        } else {
            logger.log(Level.INFO, "Author of #{0} {1} on {2} not in whitelist!", new Object[] { id, author.getLogin(), reponame });
            repo.addComment(id, GhprbTrigger.getDscp().getRequestForTestingPhrase());
        }

        logger.log(Level.INFO, "Created Pull Request #{0} on {1} by {2} ({3}) updated at: {4} SHA: {5}", 
                new Object[] { id, reponame, author.getLogin(), getAuthorEmail(), updated, prHead.getRef() }
        );
    }

    public void init(Ghprb helper, GhprbRepository repo) throws IOException {
        this.helper = helper;
        this.repo = repo;
        pr = repo.getPullRequest(id);
        
        GHCommitPointer prHead = pr.getHead();

        if (prHead != null && prHead.getRepository() != null) {
            authorRepoGitUrl = prHead.getRepository().gitHttpTransportUrl();
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

        updatePR(pr, pr.getUser());

        checkSkipBuild(pr);
        tryBuild(pr);
    }
    
    private void checkSkipBuild(GHIssue issue) {
        String skipBuildPhrase = helper.checkSkipBuild(issue);
        if (!StringUtils.isEmpty(skipBuildPhrase)) {
            logger.log(Level.INFO, "Pull request commented with {0} skipBuildPhrase. Hence skipping the build.", skipBuildPhrase);
            shouldRun = false;
        }
    }

    public void check(GHIssueComment comment) {
        if (helper.isProjectDisabled()) {
            logger.log(Level.FINE, "Project is disabled, ignoring comment");
            return;
        }
        try {
            checkComment(comment);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Couldn't check comment #" + comment.getId(), ex);
            return;
        }
        

        GHPullRequest pr = null;
        try {
            pr = repo.getPullRequest(id);
            updatePR(pr, comment.getUser());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Couldn't get GHPullRequest for checking mergeable state");
        }
        checkSkipBuild(comment.getParent());
        tryBuild(pr);
    }
    
    private void updatePR(GHPullRequest pr, GHUser user) {
        
        Date lastUpdateTime = updated;
        if (pr != null && isUpdated(pr)) {
            logger.log(Level.INFO, "Pull request #{0} was updated on {1} at {2} by {3}", new Object[] { id, repo.getName(), updated, user });

            // the author of the PR could have been whitelisted since its creation
            if (!accepted && helper.isWhitelisted(pr.getUser())) {
                logger.log(Level.INFO, "Pull request #{0}'s author has been whitelisted", new Object[]{id});
                accepted = true;
            }
            
            int commentsChecked = checkComments(pr, lastUpdateTime);
            boolean newCommit = checkCommit(pr.getHead());

            if (!newCommit && commentsChecked == 0) {
                logger.log(Level.INFO, "Pull request #{0} was updated on repo {1} but there aren''t any new comments nor commits; "
                        + "that may mean that commit status was updated.", 
                        new Object[] { id, repo.getName() }
                );
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
        logger.log(Level.FINEST, "PR #{0} target branch: {1} isn''t in our whitelist of target branches: {2}", 
                new Object[] { id, target, Joiner.on(',').skipNulls().join(branches) }
        );
        return false;
    }

    private boolean isUpdated(GHPullRequest pr) {
        if (pr == null) {
            return false;
        }
        Date lastUpdated = new Date();
        boolean ret = false;
        try {
            lastUpdated = pr.getUpdatedAt();
            ret = updated.compareTo(lastUpdated) < 0;
            updated = lastUpdated;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Unable to update last updated date", e);
        }
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
        helper.getBuilds().build(this, triggerSender, commentBody);
    }

    // returns false if no new commit
    private boolean checkCommit(GHCommitPointer sha) {
        if (head.equals(sha.getSha())) {
            return false;
        }
        logger.log(Level.FINE, "New commit. Sha: {0} => {1}", new Object[] { head, sha.getSha() });
        head = sha.getSha();
        if (accepted) {
            shouldRun = true;
        }
        return true;
    }

    private void checkComment(GHIssueComment comment) throws IOException {
        GHUser sender = comment.getUser();
        String body = comment.getBody();
        GHUser author = pr.getUser();

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

    private int checkComments(GHPullRequest pr, Date lastUpdatedTime) {
        int count = 0;
        try {
            for (GHIssueComment comment : pr.getComments()) {
                if (lastUpdatedTime.compareTo(comment.getUpdatedAt()) < 0) {
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

    public boolean checkMergeable(GHPullRequest pr) {
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
        return authorRepoGitUrl;
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
        try {
            return pr.getUser().getEmail();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to fetch author info for " + id);
        }
        return "";
    }

    public String getTitle() {
        return pr.getTitle();
    }

    /**
     * Returns the URL to the Github Pull Request.
     *
     * @return the Github Pull Request URL
     */
    public URL getUrl() {
        return pr.getHtmlUrl();
    }

    public GitUser getCommitAuthor() {
        return commitAuthor;
    }

    public GHUser getPullRequestAuthor() {
        return pr.getUser();
    }

    public GHPullRequest getPullRequest() {
        return pr;
    }
    
    public String getDescription() {
        return pr.getBody();
    }
}
