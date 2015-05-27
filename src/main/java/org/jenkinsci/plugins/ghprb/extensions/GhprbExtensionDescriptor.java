package org.jenkinsci.plugins.ghprb.extensions;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.PredicateUtils;
import org.apache.commons.collections.functors.InstanceofPredicate;

import jenkins.model.Jenkins;
import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;

@SuppressWarnings("unchecked")
public abstract class GhprbExtensionDescriptor extends Descriptor<GhprbExtension> {

    public boolean isApplicable(Class<?> type) {
        return true;
    }

    public static DescriptorExtensionList<GhprbExtension, GhprbExtensionDescriptor> getExtensions(Class<? extends GhprbExtensionType>... types) {
        DescriptorExtensionList<GhprbExtension, GhprbExtensionDescriptor> list = Jenkins.getInstance().getDescriptorList(GhprbExtension.class);
        filterExtensions(list, types);
        return list;
    }

    private static void filterExtensions(DescriptorExtensionList<GhprbExtension, GhprbExtensionDescriptor> descriptors, Class<? extends GhprbExtensionType>... types) {
        List<Predicate> predicates = new ArrayList<Predicate>(types.length);
        for (Class<? extends GhprbExtensionType> type : types) {
            predicates.add(InstanceofPredicate.getInstance(type));

        }
        Predicate anyPredicate = PredicateUtils.anyPredicate(predicates);
        for (GhprbExtensionDescriptor descriptor : descriptors) {
            if (!anyPredicate.evaluate(descriptor)) {
                descriptors.remove(descriptor);
            }
        }
    }

    public static DescriptorExtensionList<GhprbExtension, GhprbExtensionDescriptor> allProject() {
        DescriptorExtensionList<GhprbExtension, GhprbExtensionDescriptor> list = Jenkins.getInstance().getDescriptorList(GhprbExtension.class);
        filterExtensions(list, GhprbProjectExtension.class);
        return list;
    }

    public static DescriptorExtensionList<GhprbExtension, GhprbExtensionDescriptor> allGlobal() {
        DescriptorExtensionList<GhprbExtension, GhprbExtensionDescriptor> list = Jenkins.getInstance().getDescriptorList(GhprbExtension.class);
        filterExtensions(list, GhprbGlobalExtension.class);
        return list;
    }

}