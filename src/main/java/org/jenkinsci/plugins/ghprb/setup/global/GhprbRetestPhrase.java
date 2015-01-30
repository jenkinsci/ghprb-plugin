package org.jenkinsci.plugins.ghprb.setup.global;


import hudson.Extension;

import java.util.regex.Pattern;

import org.kohsuke.github.GHIssueComment;
import org.kohsuke.stapler.DataBoundConstructor;

import static org.apache.commons.lang.StringUtils.isBlank;

public class GhprbRetestPhrase extends GhprbGlobalSetup {
    
    @DataBoundConstructor
    public GhprbRetestPhrase(String retestPhrase) {
        this.retestPhrase = retestPhrase;
    }
    
    private final String retestPhrase; // = ".*test\\W+this\\W+please.*";

    public boolean isRetestPhrase(String comment) {
        if (isBlank(retestPhrase) || isBlank(comment)) {
            return false;
        }
        Pattern retestPhrasePattern = Pattern.compile(retestPhrase);
        return retestPhrasePattern.matcher(comment).matches();
    }
    
    public String getRetestPhrase() {
        return retestPhrase;
    }
    
    @Override
    public boolean shouldBuild(GHIssueComment comment, boolean isAdmin, boolean isWhiteListed){
        return isRetestPhrase(comment.getBody());
    }
    
    @Extension
    public static final class DescriptorImpl extends GhprbGlobalSetupDescriptor {

        @Override
        public String getDisplayName() {
            return "Re-Test Phrase";
        }
        
    }

}
