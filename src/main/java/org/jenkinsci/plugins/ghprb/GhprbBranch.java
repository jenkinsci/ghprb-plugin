package org.jenkinsci.plugins.ghprb;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Ray Sennewald & David Wang
 */

public class GhprbBranch extends AbstractDescribableImpl<GhprbBranch> {
    private String branch;

    public String getBranch() {
        return branch;
    }

    public boolean matches(String s) {
        return s.matches(branch);
    }

    @DataBoundConstructor
    public GhprbBranch(String branch) {
        this.branch = branch.trim();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<GhprbBranch> {
        @Override
        public String getDisplayName() {
            return "Branch";
        }
    }
}
