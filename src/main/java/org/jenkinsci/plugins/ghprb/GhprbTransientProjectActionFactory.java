package org.jenkinsci.plugins.ghprb;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.TransientProjectActionFactory;
import java.util.Collection;
import java.util.Collections;

/**
 *
 * @author janinko
 */
@Extension
@Deprecated
public class GhprbTransientProjectActionFactory extends TransientProjectActionFactory{

	@Override
	public Collection<? extends Action> createFor(AbstractProject project) {
		GhprbTrigger trigger = GhprbTrigger.getTrigger(project);
		if (trigger == null || trigger.getGhprb() == null) {
			return Collections.emptyList();
		}

		return Collections.singleton(new GhprbProjectAction(trigger));
	}

}
