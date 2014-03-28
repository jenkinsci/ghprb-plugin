package org.jenkinsci.plugins.ghprb;

import com.coravy.hudson.plugins.github.GithubProjectProperty;
import com.google.common.base.Preconditions;
import hudson.model.AbstractProject;
import jenkins.model.Jenkins;
import org.kohsuke.github.GHUser;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.*;

/**
 * @author janinko
 */
public class Ghprb {
    private static final Logger logger = Logger.getLogger(Ghprb.class.getName());
    private static final Pattern githubUserRepoPattern = Pattern.compile("^(http[s]?://[^/]*)/([^/]*)/([^/]*).*");

    private final Set<String> admins;
    private final Set<String> whitelisted;
    private final Set<String> organisations;
    private final String triggerPhrase;
    private final GhprbTrigger trigger;
    private final AbstractProject<?, ?> project;
    private final Pattern retestPhrasePattern;
    private final Pattern whitelistPhrasePattern;
    private final Pattern oktotestPhrasePattern;
    private GhprbRepository repository;
    private GhprbBuilds builds;

    public Ghprb(AbstractProject<?, ?> project, GhprbTrigger trigger, ConcurrentMap<Integer, GhprbPullRequest> pulls) {
        this.project = project;

        final GithubProjectProperty ghpp = project.getProperty(GithubProjectProperty.class);
        if (ghpp == null || ghpp.getProjectUrl() == null) {
            throw new IllegalStateException("A GitHub project url is required.");
        }
        String baseUrl = ghpp.getProjectUrl().baseUrl();
        Matcher m = githubUserRepoPattern.matcher(baseUrl);
        if (!m.matches()) {
            throw new IllegalStateException(String.format("Invalid GitHub project url: %s", baseUrl));
        }
        final String user = m.group(2);
        final String repo = m.group(3);

        this.trigger = trigger;
        this.admins = new HashSet<String>(Arrays.asList(trigger.getAdminlist().split("\\s+")));
        this.admins.remove("");
        this.whitelisted = new HashSet<String>(Arrays.asList(trigger.getWhitelist().split("\\s+")));
        this.whitelisted.remove("");
        this.organisations = new HashSet<String>(Arrays.asList(trigger.getOrgslist().split("\\s+")));
        this.organisations.remove("");
        this.triggerPhrase = trigger.getTriggerPhrase();

        retestPhrasePattern = Pattern.compile(trigger.getDescriptor().getRetestPhrase());
        whitelistPhrasePattern = Pattern.compile(trigger.getDescriptor().getWhitelistPhrase());
        oktotestPhrasePattern = Pattern.compile(trigger.getDescriptor().getOkToTestPhrase());

        this.repository = new GhprbRepository(user, repo, this, pulls);
        this.builds = new GhprbBuilds(trigger, repository);
    }

    public void init() {
        this.repository.init();
        if (trigger.getUseGitHubHooks()) {
            this.repository.createHook();
        }
    }

    public void addWhitelist(String author) {
        logger.log(Level.INFO, "Adding {0} to whitelist", author);
        whitelisted.add(author);
        trigger.addWhitelist(author);
    }

    public GhprbBuilds getBuilds() {
        return builds;
    }

    public GhprbRepository getRepository() {
        return repository;
    }

    public GhprbGitHub getGitHub() {
        return trigger.getDescriptor().getGitHub();
    }

    void run() {
        repository.check();
    }

    void stop() {
        repository = null;
        builds = null;
    }

    public boolean isRetestPhrase(String comment) {
        return retestPhrasePattern.matcher(comment).matches();
    }

    public boolean isWhitelistPhrase(String comment) {
        return whitelistPhrasePattern.matcher(comment).matches();
    }

    public boolean isOktotestPhrase(String comment) {
        return oktotestPhrasePattern.matcher(comment).matches();
    }

    public boolean isTriggerPhrase(String comment) {
        return !triggerPhrase.equals("") && comment.contains(triggerPhrase);
    }

    public boolean ifOnlyTriggerPhrase() {
        return trigger.getOnlyTriggerPhrase();
    }

    public boolean isWhitelisted(GHUser user) {
        return trigger.getPermitAll()
                || whitelisted.contains(user.getLogin())
                || admins.contains(user.getLogin())
                || isInWhitelistedOrganisation(user);
    }

    public boolean isAdmin(String username) {
        return admins.contains(username);
    }

    private boolean isInWhitelistedOrganisation(GHUser user) {
        for (String organisation : organisations) {
            if (getGitHub().isUserMemberOfOrganization(organisation, user)) {
                return true;
            }
        }
        return false;
    }

    List<GhprbBranch> getWhiteListTargetBranches() {
        return trigger.getWhiteListTargetBranches();
    }

}
