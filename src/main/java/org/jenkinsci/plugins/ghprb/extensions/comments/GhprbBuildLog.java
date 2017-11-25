package org.jenkinsci.plugins.ghprb.extensions.comments;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.ghprb.Ghprb;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommentAppender;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbExtensionDescriptor;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbProjectExtension;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.List;

public class GhprbBuildLog extends GhprbExtension implements GhprbCommentAppender, GhprbProjectExtension, GhprbGlobalExtension {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private final Integer logExcerptLines;

    @DataBoundConstructor
    public GhprbBuildLog(Integer logExcerptLines) {
        this.logExcerptLines = logExcerptLines;
    }

    public Integer getLogExcerptLines() {
        return logExcerptLines == null ? Integer.valueOf(0) : logExcerptLines;
    }

    public String postBuildComment(Run<?, ?> build, TaskListener listener) {

        StringBuilder msg = new StringBuilder();
        GHCommitState state = Ghprb.getState(build);

        int numLines = getDescriptor().getLogExcerptLinesDefault(this);

        if (state != GHCommitState.SUCCESS && numLines > 0) {
            // on failure, append an excerpt of the build log
            try {
                // wrap log in "code" markdown
                msg.append("\n\n**Build Log**\n*last ").append(numLines).append(" lines*\n");
                msg.append("\n ```\n");
                List<String> log = build.getLog(numLines);
                for (String line : log) {
                    msg.append(line).append('\n');
                }
                msg.append("```\n");
            } catch (IOException ex) {
                listener.getLogger().println("Can't add log excerpt to commit comments");
                ex.printStackTrace(listener.getLogger());
            }
        }
        return msg.toString();
    }

    public boolean ignorePublishedUrl() {
        return false;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }


    public static final class DescriptorImpl extends GhprbExtensionDescriptor implements GhprbGlobalExtension {

        @Override
        public String getDisplayName() {
            return "Append portion of build log";
        }

        public Integer getLogExcerptLinesDefault(GhprbBuildLog local) {
            Integer lines = Ghprb.getDefaultValue(local, GhprbBuildLog.class, "getLogExcerptLines");
            if (lines == null) {
                lines = 0;
            }
            return lines;
        }
    }

}
