package org.jenkinsci.plugins.ghprb.extensions.status;

import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildResultMessage;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import java.util.List;

public abstract class GhprbSimpleStatusDescriptor extends GhprbExtensionDescriptor {

    public abstract String getDisplayName();

    public abstract String getTriggeredStatusDefault(GhprbSimpleStatus local);

    public abstract String getStatusUrlDefault(GhprbSimpleStatus local);

    public abstract String getStartedStatusDefault(GhprbSimpleStatus local);

    public abstract Boolean getAddTestResultsDefault(GhprbSimpleStatus local);

    public abstract List<GhprbBuildResultMessage> getCompletedStatusDefault(GhprbSimpleStatus local);

    public abstract String getCommitStatusContextDefault(GhprbSimpleStatus local);

    public abstract Boolean getShowMatrixStatusDefault(GhprbSimpleStatus local);

    public abstract boolean addIfMissing();
}

