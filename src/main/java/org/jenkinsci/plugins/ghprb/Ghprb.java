package org.jenkinsci.plugins.ghprb;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import com.cloudbees.plugins.credentials.domains.HostnamePortSpecification;
import com.cloudbees.plugins.credentials.domains.HostnameSpecification;
import com.cloudbees.plugins.credentials.domains.PathSpecification;
import com.cloudbees.plugins.credentials.domains.SchemeSpecification;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.DescribableList;
import hudson.util.Secret;

import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.PredicateUtils;
import org.apache.commons.collections.functors.InstanceofPredicate;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.GhprbProjectExtension;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHUser;

import java.net.URI;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author janinko
 */
public class Ghprb {
    private static final Logger logger = Logger.getLogger(Ghprb.class.getName());
    public static final Pattern githubUserRepoPattern = Pattern.compile("^(http[s]?://[^/]*)/([^/]*/[^/]*).*");

    private final GhprbTrigger trigger;

    public Ghprb(GhprbTrigger trigger) {
        this.trigger = trigger;
    }

    public void addWhitelist(String author) {
        logger.log(Level.INFO, "Adding {0} to whitelist", author);
        trigger.addWhitelist(author);
    }
    
    public boolean isProjectDisabled() {
        return !trigger.isActive();
    }

    public GhprbBuilds getBuilds() {
        return trigger.getBuilds();
    }

    public GhprbTrigger getTrigger() {
        return trigger;
    }


    public GhprbGitHub getGitHub() {
        return trigger.getGhprbGitHub();
    }

    public static Pattern compilePattern(String regex) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to compile pattern "+regex, e);
            return null;
        }
    }
    
    private static boolean checkPattern(Pattern pattern, String comment) {
        return pattern != null && pattern.matcher(comment).matches();
    }

    // These used to be stored on the object in the constructor.
    // But because the object is only instantiated once per PR, configuration would go stale.
    // Some optimization could be done around re-compiling regex/hash sets, but beyond that we still have to re-pull the text.
    private Pattern retestPhrasePattern() {
        return compilePattern(trigger.getDescriptor().getRetestPhrase());
    }
    
    /**
     * Returns skip build phrases from Jenkins global configuration
     * 
     * @return skip build phrases
     */
    public Set<String> getSkipBuildPhrases() {
        return new HashSet<String>(Arrays.asList(getTrigger().getSkipBuildPhrase().split("[\\r\\n]+")));
    }
    
    /**
     * Checks for skip build phrase in pull request title and body. If present it updates shouldRun as false.
     * 
     * @param issue The GitHub issue
     * @return the skip phrase or null if should not skip
     */
    public String checkSkipBuild(GHIssue issue) {
        // check in title
        String pullRequestTitle = issue.getTitle();
        String skipBuildPhrase = checkSkipBuildInString(pullRequestTitle);
        if (StringUtils.isNotBlank(skipBuildPhrase)) {
            return skipBuildPhrase;
        }
        // not found in title, check in body
        String pullRequestBody = issue.getBody();
        skipBuildPhrase = checkSkipBuildInString(pullRequestBody);
        if (StringUtils.isNotBlank(skipBuildPhrase)) {
            return skipBuildPhrase;
        }
        return null;
    }

    /**
     * Checks for skip build phrase in the passed string
     *
     * @param string The string we're looking for the phrase in
     * @return the skip phrase or null if we don't find it
     */
    private String checkSkipBuildInString( String string ) {
        // check for skip build phrase in the passed string
        if (StringUtils.isNotBlank(string)) {
            string = string.trim();
            Set<String> skipBuildPhrases = getSkipBuildPhrases();
            skipBuildPhrases.remove("");

            for (String skipBuildPhrase : skipBuildPhrases) {
                skipBuildPhrase = skipBuildPhrase.trim();
                Pattern skipBuildPhrasePattern = compilePattern(skipBuildPhrase);
                if (skipBuildPhrasePattern != null && skipBuildPhrasePattern.matcher(string).matches()) {
                    return skipBuildPhrase;
                }
            }
        }
        return null;
    }
    
    private Pattern whitelistPhrasePattern() {
        return compilePattern(trigger.getDescriptor().getWhitelistPhrase());
    }

    private Pattern oktotestPhrasePattern() {
        return compilePattern(trigger.getDescriptor().getOkToTestPhrase());
    }

    private Pattern triggerPhrase() {
        return compilePattern(trigger.getTriggerPhrase());
    }

    private HashSet<String> admins() {
        HashSet<String> adminList;
        adminList = new HashSet<String>(Arrays.asList(trigger.getAdminlist().toLowerCase().split("\\s+")));
        adminList.remove("");
        return adminList;
    }

    private HashSet<String> whitelisted() {
        HashSet<String> whitelistedList;
        whitelistedList = new HashSet<String>(Arrays.asList(trigger.getWhitelist().toLowerCase().split("\\s+")));
        whitelistedList.remove("");
        return whitelistedList;
    }

    private HashSet<String> organisations() {
        HashSet<String> organisationsList;
        organisationsList = new HashSet<String>(Arrays.asList(trigger.getOrgslist().split("\\s+")));
        organisationsList.remove("");
        return organisationsList;
    }

    public boolean isRetestPhrase(String comment) {
        return checkPattern(retestPhrasePattern(), comment);
    }

    public boolean isWhitelistPhrase(String comment) {
        return checkPattern(whitelistPhrasePattern(), comment);
    }

    public boolean isOktotestPhrase(String comment) {
        return checkPattern(oktotestPhrasePattern(), comment);
    }

    public boolean isTriggerPhrase(String comment) {
        return checkPattern(triggerPhrase(), comment);
    }

    public boolean ifOnlyTriggerPhrase() {
        return trigger.getOnlyTriggerPhrase();
    }

    public boolean isWhitelisted(GHUser user) {
        return trigger.getPermitAll()
                || whitelisted().contains(user.getLogin().toLowerCase())
                || admins().contains(user.getLogin().toLowerCase())
                || isInWhitelistedOrganisation(user);
    }

    public boolean isAdmin(GHUser user) {
        return admins().contains(user.getLogin().toLowerCase())
                || (trigger.getAllowMembersOfWhitelistedOrgsAsAdmin()
                        && isInWhitelistedOrganisation(user));
    }

    public boolean isBotUser(GHUser user) {
        return user != null && user.getLogin().equals(getGitHub().getBotUserLogin());
    }

    private boolean isInWhitelistedOrganisation(GHUser user) {
        for (String organisation : organisations()) {
            if (getGitHub().isUserMemberOfOrganization(organisation, user)) {
                return true;
            }
        }
        return false;
    }

    List<GhprbBranch> getBlackListTargetBranches() {
        return trigger.getBlackListTargetBranches();
    }

    List<GhprbBranch> getWhiteListTargetBranches() {
        return trigger.getWhiteListTargetBranches();
    }

    public static String replaceMacros(AbstractBuild<?, ?> build, TaskListener listener, String inputString) {
        String returnString = inputString;
        if (build != null && inputString != null) {
            try {
                Map<String, String> messageEnvVars = getEnvVars(build, listener);

                returnString = Util.replaceMacro(inputString, messageEnvVars);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Couldn't replace macros in message: ", e);
            }
        }
        return returnString;
    }
    
    public static Map<String, String> getEnvVars(AbstractBuild<?, ?> build, TaskListener listener) {
        Map<String, String> messageEnvVars = new HashMap<String, String>();
        if (build != null) {
                messageEnvVars.putAll(build.getCharacteristicEnvVars());
                messageEnvVars.putAll(build.getBuildVariables());
                try {
                    messageEnvVars.putAll(build.getEnvironment(listener));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Couldn't get Env Variables: ", e);
                }
        }
        return messageEnvVars;
    }
    

    public static String replaceMacros(AbstractProject<?, ?> project, String inputString) {
        String returnString = inputString;
        if (project != null && inputString != null) {
            try {
                Map<String, String> messageEnvVars = new HashMap<String, String>();

                messageEnvVars.putAll(project.getCharacteristicEnvVars());

                returnString = Util.replaceMacro(inputString, messageEnvVars);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Couldn't replace macros in message: ", e);
            }
        }
        return returnString;
    }
    
    public static GHCommitState getState(AbstractBuild<?, ?> build) {

        GHCommitState state;
        if (build.getResult() == Result.SUCCESS) {
            state = GHCommitState.SUCCESS;
        } else if (build.getResult() == Result.UNSTABLE) {
            state = GhprbTrigger.getDscp().getUnstableAs();
        } else {
            state = GHCommitState.FAILURE;
        }
        return state;
    }

    public static Set<String> createSet(String list) {
        String listString = list == null ? "" : list;
        List<String> listList = Arrays.asList(listString.split("\\s+"));
        Set<String> listSet = new HashSet<String>(listList);
        listSet.remove("");
        return listSet;
    }
    

    public static GhprbCause getCause(Run<?, ?> build) {
        Cause cause = build.getCause(GhprbCause.class);
        if (cause == null || (!(cause instanceof GhprbCause))) {
            return null;
        }
        return (GhprbCause) cause;
    }
    

    public static GhprbTrigger extractTrigger(AbstractBuild<?, ?> build) {
        return extractTrigger(build.getProject());
    }

    public static GhprbTrigger extractTrigger(AbstractProject<?, ?> p) {
        GhprbTrigger trigger = p.getTrigger(GhprbTrigger.class);
        if (trigger == null || (!(trigger instanceof GhprbTrigger))) {
            return null;
        }
        return trigger;
    }
    
    private static List<Predicate> createPredicate(Class<?> ...types) {
        List<Predicate> predicates = new ArrayList<Predicate>(types.length);
        for (Class<?> type : types) {
            predicates.add(InstanceofPredicate.getInstance(type));
        }
        return predicates;
    }
    
    public static void filterList(DescribableList<GhprbExtension, GhprbExtensionDescriptor> descriptors, Predicate predicate) {
        for (GhprbExtension descriptor : descriptors) {
            if (!predicate.evaluate(descriptor)) {
                descriptors.remove(descriptor);
            }
        }
    }
    
    private static DescribableList<GhprbExtension, GhprbExtensionDescriptor> copyExtensions(DescribableList<GhprbExtension, GhprbExtensionDescriptor> ...extensionsList){
        DescribableList<GhprbExtension, GhprbExtensionDescriptor> copiedList = new DescribableList<GhprbExtension, GhprbExtensionDescriptor>(Saveable.NOOP);
        for (DescribableList<GhprbExtension, GhprbExtensionDescriptor> extensions: extensionsList) {
            copiedList.addAll(extensions);
        }
        return copiedList;
    }

    @SuppressWarnings("unchecked")
    public static DescribableList<GhprbExtension, GhprbExtensionDescriptor> getJobExtensions(GhprbTrigger trigger, Class<?> ...types) {
        
        // First get all global extensions
        DescribableList<GhprbExtension, GhprbExtensionDescriptor> copied = copyExtensions(trigger.getDescriptor().getExtensions());
        
        // Remove extensions that are specified by job
        filterList(copied, PredicateUtils.notPredicate(InstanceofPredicate.getInstance(GhprbProjectExtension.class)));
        
        // Then get the rest of the extensions from the job
        copied = copyExtensions(copied, trigger.getExtensions());
        
        // Filter extensions by desired interface
        filterList(copied, PredicateUtils.anyPredicate(createPredicate(types)));
        return copied;
    }
    
    public static DescribableList<GhprbExtension, GhprbExtensionDescriptor> matchesAll(DescribableList<GhprbExtension, GhprbExtensionDescriptor> extensions, Class<?> ...types) {
        Predicate predicate = PredicateUtils.allPredicate(createPredicate(types));
        DescribableList<GhprbExtension, GhprbExtensionDescriptor> copyExtensions = new DescribableList<GhprbExtension, GhprbExtensionDescriptor>(Saveable.NOOP);
        
        copyExtensions.addAll(extensions);
        filterList(copyExtensions, predicate);
        return copyExtensions;
    }
    
    public static DescribableList<GhprbExtension, GhprbExtensionDescriptor> matchesSome(DescribableList<GhprbExtension, GhprbExtensionDescriptor> extensions, Class<?> ...types) {
        Predicate predicate = PredicateUtils.anyPredicate(createPredicate(types));
        DescribableList<GhprbExtension, GhprbExtensionDescriptor> copyExtensions = new DescribableList<GhprbExtension, GhprbExtensionDescriptor>(Saveable.NOOP);
        
        copyExtensions.addAll(extensions);
        filterList(copyExtensions, predicate);
        return copyExtensions;
    }
    
    public static DescribableList<GhprbExtension, GhprbExtensionDescriptor> onlyOneEntry(DescribableList<GhprbExtension, GhprbExtensionDescriptor> extensions, Class<?> ...types) {
        DescribableList<GhprbExtension, GhprbExtensionDescriptor> copyExtensions = new DescribableList<GhprbExtension, GhprbExtensionDescriptor>(Saveable.NOOP);
        
        Set<Class<?>> extSet = new HashSet<Class<?>>(types.length);
        List<Predicate> predicates = createPredicate(types);
        for (GhprbExtension extension: extensions) {
            if (addExtension(extension, predicates, extSet)) {
                copyExtensions.add(extension);
            }
        }
        
        return copyExtensions;
    }
    
    private static boolean addExtension(GhprbExtension extension, List<Predicate> predicates, Set<Class<?>> extSet) {
        for (Predicate predicate: predicates) {
            if (predicate.evaluate(extension)) {
                Class<?> clazz = ((InstanceofPredicate)predicate).getType();
                if (extSet.contains(clazz)) {
                    return false;
                } else {
                    extSet.add(clazz);
                    return true;
                }
            }
        }
        return true;
    }
    
    public static void addIfMissing(DescribableList<GhprbExtension, GhprbExtensionDescriptor> extensions, GhprbExtension ext, Class<?> type) {
        if (ext == null) {
            return;
        }
        Predicate predicate = InstanceofPredicate.getInstance(type);
        for (GhprbExtension extension : extensions) {
            if (predicate.evaluate(extension)){
                return;
            }
        }
        extensions.add(ext);
    }

    public static StandardCredentials lookupCredentials(Item context, String credentialId, String uri) {
        String contextName = "(Jenkins.instance)";
        if (context != null) {
            contextName = context.getFullName();
        }
        logger.log(Level.FINE, "Looking up credentials for {0}, using context {1} for url {2}", new Object[] { credentialId, contextName, uri });
        
        List<StandardCredentials> credentials;
        
        logger.log(Level.FINE, "Using null context because of issues not getting all credentials");
        
        credentials = CredentialsProvider.lookupCredentials(StandardCredentials.class, (Item) null, ACL.SYSTEM,
                URIRequirementBuilder.fromUri(uri).build());
        
        logger.log(Level.FINE, "Found {0} credentials", new Object[]{credentials.size()});
        
        return (credentialId == null) ? null : CredentialsMatchers.firstOrNull(credentials,
                    CredentialsMatchers.withId(credentialId));
    }
    
    public static String createCredentials(String serverAPIUrl, String token) throws Exception {
        String description = serverAPIUrl + " GitHub auto generated token credentials";
        StringCredentialsImpl credentials = new StringCredentialsImpl(
                CredentialsScope.GLOBAL, 
                UUID.randomUUID().toString(), 
                description, 
                Secret.fromString(token));
        return createCredentials(serverAPIUrl, credentials);
    }
    
    public static String createCredentials(String serverAPIUrl, String username, String password) throws Exception {
        String description = serverAPIUrl + " GitHub auto generated Username password credentials";
        UsernamePasswordCredentialsImpl credentials = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, 
                UUID.randomUUID().toString(),
                description,
                username,
                password);
        return createCredentials(serverAPIUrl, credentials);
    }
    
    private static String createCredentials(String serverAPIUrl, StandardCredentials credentials) throws Exception {
        List<DomainSpecification> specifications = new ArrayList<DomainSpecification>(2);
        
        URI serverUri = new URI(serverAPIUrl);
        
        if (serverUri.getPort() > 0) {
            specifications.add(new HostnamePortSpecification(serverUri.getHost() + ":" + serverUri.getPort(), null));
        } else {
            specifications.add(new HostnameSpecification(serverUri.getHost(), null));
        }
        
        specifications.add(new SchemeSpecification(serverUri.getScheme()));
        String path = serverUri.getPath();
        if (StringUtils.isEmpty(path)) {
            path = "/";
        }
        specifications.add(new PathSpecification(path, null, false));
        
        Domain domain = new Domain(serverUri.getHost(), "Auto generated credentials domain", specifications);
        CredentialsStore provider = new SystemCredentialsProvider.StoreImpl();
        provider.addDomain(domain, credentials);
        return credentials.getId();
    }
    
    public static <T extends GhprbExtension> T getGlobal(Class<T> clazz) {
        DescribableList<GhprbExtension, GhprbExtensionDescriptor> copyExtensions = new DescribableList<GhprbExtension, GhprbExtensionDescriptor>(Saveable.NOOP);

        copyExtensions.addAll(GhprbTrigger.DESCRIPTOR.getExtensions());
        
        filterList(copyExtensions, InstanceofPredicate.getInstance(clazz));
        
        return copyExtensions.get(clazz);
    }
    
    

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T, S extends GhprbExtension> T getDefaultValue(S local, Class<S> globalClass, String methodName) {
        T toReturn = null;
        S global = getGlobal(globalClass);
        if (local == null && global == null) {
            return null;
        }
        try {
            if (local == null) {
                return (T) global.getClass().getMethod(methodName).invoke(global);
            } else if (global == null) {
                return (T) local.getClass().getMethod(methodName).invoke(local);
            }
            
            T localValue = (T) local.getClass().getMethod(methodName).invoke(local);
            T globalValue = (T) global.getClass().getMethod(methodName).invoke(global);


            if (localValue instanceof String) {
                if (StringUtils.isEmpty((String) localValue)) {
                    return globalValue;
                }
            } else if (localValue instanceof List) {
                if (((List) localValue).isEmpty()) {
                    return globalValue;
                }
            }

            return localValue;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return toReturn;
    }
}
