package org.jenkinsci.plugins.ghprb.setup.global;

import jenkins.model.Jenkins;
import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;

public abstract class GhprbGlobalSetupDescriptor extends Descriptor<GhprbGlobalSetup> {
    
    public boolean isApplicable(Class<?> type) {
        return true;
    }

    public static DescriptorExtensionList<GhprbGlobalSetup,GhprbGlobalSetupDescriptor> all() {
        DescriptorExtensionList<GhprbGlobalSetup,GhprbGlobalSetupDescriptor> list = Jenkins.getInstance().getDescriptorList(GhprbGlobalSetup.class);
        return list;
    }
}
