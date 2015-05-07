package org.jenkinsci.plugins.ghprb.extensions;

import jenkins.model.Jenkins;
import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;

public abstract class GhprbExtensionDescriptor extends Descriptor<GhprbExtension> {
    
    public boolean isApplicable(Class<?> type) {
        return true;
    }

    public static DescriptorExtensionList<GhprbExtension,GhprbExtensionDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(GhprbExtension.class);
    }

}