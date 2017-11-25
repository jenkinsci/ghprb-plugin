package org.jenkinsci.plugins.ghprb.extensions;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.PredicateUtils;
import org.apache.commons.collections.functors.InstanceofPredicate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@SuppressWarnings("unchecked")
public abstract class GhprbExtensionDescriptor extends Descriptor<GhprbExtension> {
    public boolean isApplicable(Class<?> type) {
        return true;
    }

    public static List<GhprbExtensionDescriptor> getExtensions(Class<? extends GhprbExtensionType>... types) {
        List<GhprbExtensionDescriptor> list = getExtensions();
        filterExtensions(list, types);
        return list;
    }

    private static void filterExtensions(List<GhprbExtensionDescriptor> descriptors, Class<? extends GhprbExtensionType>... types) {
        List<Predicate> predicates = new ArrayList<Predicate>(types.length);
        for (Class<? extends GhprbExtensionType> type : types) {
            predicates.add(InstanceofPredicate.getInstance(type));

        }
        Predicate anyPredicate = PredicateUtils.anyPredicate(predicates);
        Iterator<GhprbExtensionDescriptor> iter = descriptors.iterator();
        while (iter.hasNext()) {
            GhprbExtensionDescriptor descriptor = iter.next();
            if (!anyPredicate.evaluate(descriptor)) {
                iter.remove();
            }
        }
    }

    private static DescriptorExtensionList<GhprbExtension, GhprbExtensionDescriptor> getExtensionList() {
        return Jenkins.getInstance().getDescriptorList(GhprbExtension.class);
    }

    /**
     * Don't mutate the list from Jenkins, they will persist;
     *
     * @return list of extensions
     */
    private static List<GhprbExtensionDescriptor> getExtensions() {
        List<GhprbExtensionDescriptor> list = new ArrayList<GhprbExtensionDescriptor>();
        list.addAll(getExtensionList());
        return list;
    }

    public static List<GhprbExtensionDescriptor> allProject() {
        List<GhprbExtensionDescriptor> list = getExtensions();
        filterExtensions(list, GhprbProjectExtension.class);
        return list;
    }

    public static List<GhprbExtensionDescriptor> allGlobal() {
        List<GhprbExtensionDescriptor> list = getExtensions();
        filterExtensions(list, GhprbGlobalExtension.class);
        return list;
    }

}
