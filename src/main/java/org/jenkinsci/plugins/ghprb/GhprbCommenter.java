package org.jenkinsci.plugins.ghprb;

import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import java.io.IOException;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Phong Nguyen Le
 */
public class GhprbCommenter extends Builder {
    private final String comment;

    @DataBoundConstructor
    public GhprbCommenter(String comment) {
        this.comment = comment;
    }

    public String getComment() {
        return comment;
    }        

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) 
            throws InterruptedException, IOException {
        GhprbTrigger trigger = build.getProject().getTrigger(GhprbTrigger.class);
        if (trigger == null) {
            throw new AbortException("GitHub Pull Request Builder trigger is not configured for this project");
        }
        
        trigger.getRepository().addComment(Integer.parseInt(build.getBuildVariables().get("ghprbPullId")), 
                comment, build, listener);
        return true;
    }        
    
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {            
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Comment on GitHub pull request";
        }
    
    }
}
