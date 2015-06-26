package org.jenkinsci.plugins.ghprb;

import static hudson.Util.fixEmpty;
import static hudson.Util.fixEmptyAndTrim;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.github.GHAuthorization;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.HostnamePortRequirement;
import com.cloudbees.plugins.credentials.domains.HostnameRequirement;
import com.cloudbees.plugins.credentials.domains.PathRequirement;
import com.cloudbees.plugins.credentials.domains.SchemeRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.common.base.Joiner;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class GhprbGitHubAuth extends AbstractDescribableImpl<GhprbGitHubAuth> {
    private static final Logger logger = Logger.getLogger(GhprbGitHubAuth.class.getName());

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private final String serverAPIUrl;
    private final String credentialsId;
    private final String id;
    private final String description;
    private final String secret;

    @DataBoundConstructor
    public GhprbGitHubAuth(
            String serverAPIUrl, 
            String credentialsId, 
            String description, 
            String id,
            String secret
            ) {
        if (StringUtils.isEmpty(serverAPIUrl)) {
            serverAPIUrl = "https://api.github.com";
        }
        this.serverAPIUrl = fixEmptyAndTrim(serverAPIUrl);
        this.credentialsId = fixEmpty(credentialsId);
        if (StringUtils.isEmpty(id)) {
            id = UUID.randomUUID().toString();
        }
        
        this.id = IdCredentials.Helpers.fixEmptyId(id);
        this.description = description;
        this.secret = secret;
    }

    @Exported
    public String getServerAPIUrl() {
        return serverAPIUrl;
    }

    @Exported
    public String getCredentialsId() {
        return credentialsId;
    }
    
    @Exported
    public String getDescription() {
        return description;
    }
    
    @Exported
    public String getId() {
        return id;
    }
    

    @Exported
    public String getSecret() {
        return secret;
    }
    
    public GitHub getConnection(Item context) throws IOException {
        GitHub gh = null;
        GitHubBuilder builder = new GitHubBuilder()
                    .withEndpoint(serverAPIUrl)
                    .withConnector(new HttpConnectorWithJenkinsProxy());
        
        if (!StringUtils.isEmpty(credentialsId)) {
            StandardCredentials credentials = CredentialsMatchers
                    .firstOrNull(
                            CredentialsProvider.lookupCredentials(StandardCredentials.class, context,
                                    ACL.SYSTEM, URIRequirementBuilder.fromUri(serverAPIUrl).build()),
                                    CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));
            
            if (credentials instanceof StringCredentials) {
                String accessToken = ((StringCredentials) credentials).getSecret().getPlainText();
                builder.withOAuthToken(accessToken);
            } else if (credentials instanceof UsernamePasswordCredentials){
                UsernamePasswordCredentials creds = (UsernamePasswordCredentials) credentials;
                String username = creds.getUsername();
                String password = creds.getPassword().getPlainText();
                builder.withPassword(username, password);
            }
        }
        try {
            gh = builder.build();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to connect using credentials: " + credentialsId, e);
        }
        
        return gh;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public static final class DescriptorImpl extends Descriptor<GhprbGitHubAuth> {

        @Override
        public String getDisplayName() {
            return "GitHub Auth";
        }

        /**
         * Stapler helper method.
         *
         * @param context
         *            the context.
         * @param remoteBase
         *            the remote base.
         * @return list box model.
         * @throws URISyntaxException 
         */
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context, @QueryParameter String serverAPIUrl) throws URISyntaxException {
            List<DomainRequirement> domainRequirements = getDomainReqs(serverAPIUrl);
            
            return new StandardListBoxModel()
                    .withEmptySelection()
                    .withMatching(
                            CredentialsMatchers.anyOf(
                            CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                            CredentialsMatchers.instanceOf(StringCredentials.class)),
                    CredentialsProvider.lookupCredentials(StandardCredentials.class,
                            context,
                            ACL.SYSTEM,
                            domainRequirements)
                            );
        }
        

        public FormValidation doCreateApiToken(
                @QueryParameter("serverAPIUrl") final String serverAPIUrl, 
                @QueryParameter("credentialsId") final String credentialsId, 
                @QueryParameter("username") final String username, 
                @QueryParameter("password") final String password) {
            try {

                GitHubBuilder builder = new GitHubBuilder()
                            .withEndpoint(serverAPIUrl)
                            .withConnector(new HttpConnectorWithJenkinsProxy());
                
                if (StringUtils.isEmpty(credentialsId)) {
                    if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password)) {
                        return FormValidation.error("Username and Password required");
                    }

                    builder.withPassword(username, password);
                } else {
                    StandardCredentials credentials = Ghprb.lookupCredentials(null, credentialsId, serverAPIUrl);
                    if (credentials instanceof StandardUsernamePasswordCredentials) {
                        StandardUsernamePasswordCredentials upCredentials = (StandardUsernamePasswordCredentials) credentials;
                        builder.withPassword(upCredentials.getUsername(), upCredentials.getPassword().getPlainText());
                    } else {
                        return FormValidation.error("No username/password credentials provided");
                    }
                }
                GitHub gh = builder.build();
                GHAuthorization token = gh.createToken(Arrays.asList(GHAuthorization.REPO_STATUS, 
                        GHAuthorization.REPO), "Jenkins GitHub Pull Request Builder", null);
                String tokenId;
                try {
                    tokenId = Ghprb.createCredentials(serverAPIUrl, token.getToken());
                } catch (Exception e) {
                    tokenId = "Unable to create credentials: " + e.getMessage();
                }
                
                return FormValidation.ok("Access token created: " + token.getToken() + " token CredentialsID: " + tokenId);
            } catch (IOException ex) {
                return FormValidation.error("GitHub API token couldn't be created: " + ex.getMessage());
            }
        }
        
        private List<DomainRequirement> getDomainReqs(String serverAPIUrl) throws URISyntaxException {
            List<DomainRequirement> requirements = new ArrayList<DomainRequirement>(2);
            
            URI serverUri = new URI(serverAPIUrl);
            
            if (serverUri.getPort() > 0) {
                requirements.add(new HostnamePortRequirement(serverUri.getHost(), serverUri.getPort()));
            } else {
                requirements.add(new HostnameRequirement(serverUri.getHost()));
            }
            
            requirements.add(new SchemeRequirement(serverUri.getScheme()));
            if (!StringUtils.isEmpty(serverUri.getPath())) {
                requirements.add(new PathRequirement(serverUri.getPath()));
            }
            return requirements;
        }

        public FormValidation doCheckServerAPIUrl(@QueryParameter String value) {
            if ("https://api.github.com".equals(value)) {
                return FormValidation.ok();
            }
            if (value.endsWith("/api/v3") || value.endsWith("/api/v3/")) {
                return FormValidation.ok();
            }
            return FormValidation.warning("GitHub API URI is \"https://api.github.com\". GitHub Enterprise API URL ends with \"/api/v3\"");
        }
        
        public FormValidation doCheckRepoAccess(
                @QueryParameter("serverAPIUrl") final String serverAPIUrl, 
                @QueryParameter("credentialsId") final String credentialsId,
                @QueryParameter("repo") final String repo) {
            try {
                GitHubBuilder builder = getBuilder(serverAPIUrl, credentialsId);
                if (builder == null) {
                    return FormValidation.error("No credentials ID provided!!");
                }
                GitHub gh = builder.build();
                GHRepository repository = gh.getRepository(repo);
                StringBuilder sb = new StringBuilder();
                sb.append("User has access to: ");
                List<String> permissions = new ArrayList<String>(3);
                if (repository.hasAdminAccess()) {
                    permissions.add("Admin");
                }
                if (repository.hasPushAccess()) {
                    permissions.add("Push");
                }
                if (repository.hasPullAccess()) {
                    permissions.add("Pull");
                }
                sb.append(Joiner.on(", ").join(permissions));
                
                return FormValidation.ok(sb.toString());
            } catch (Exception ex) {
                return FormValidation.error("Unable to connect to GitHub API: " + ex);
            }
        }
        
        private GitHubBuilder getBuilder(String serverAPIUrl, String credentialsId) {
            GitHubBuilder builder = new GitHubBuilder()
                .withEndpoint(serverAPIUrl)
                .withConnector(new HttpConnectorWithJenkinsProxy());

            StandardCredentials credentials = Ghprb.lookupCredentials(null, credentialsId, serverAPIUrl);
            if (credentials instanceof StandardUsernamePasswordCredentials) {
                StandardUsernamePasswordCredentials upCredentials = (StandardUsernamePasswordCredentials) credentials;
                builder.withPassword(upCredentials.getUsername(), upCredentials.getPassword().getPlainText());
                
            } else if (credentials instanceof StringCredentials) {
                StringCredentials tokenCredentials = (StringCredentials) credentials;
                builder.withOAuthToken(tokenCredentials.getSecret().getPlainText());
            } else {
                return null;
            }
            return builder;
        }

        public FormValidation doTestGithubAccess(
                @QueryParameter("serverAPIUrl") final String serverAPIUrl, 
                @QueryParameter("credentialsId") final String credentialsId) {
            try {
                GitHubBuilder builder = getBuilder(serverAPIUrl, credentialsId);
                if (builder == null) {
                    return FormValidation.error("No credentials ID provided!!");
                }
                GitHub gh = builder.build();
                GHMyself me = gh.getMyself();
                return FormValidation.ok("Connected to " + serverAPIUrl + " as " + me.getName());
            } catch (Exception ex) {
                return FormValidation.error("Unable to connect to GitHub API: " + ex);
            }
        }
    }

}
