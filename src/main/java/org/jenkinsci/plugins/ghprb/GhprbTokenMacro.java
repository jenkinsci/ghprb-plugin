package org.jenkinsci.plugins.ghprb;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;

import org.jenkinsci.plugins.tokenmacro.DataBoundTokenMacro;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;

import java.io.IOException;

/**
 * {@code PR_Name} token that expands to the PR Name. {@code PR_User} token that expands to the PR Opener's email.
 *
 * @author Josh Caldwell
 */
@Extension(optional = true)
public class GhprbTokenMacro extends DataBoundTokenMacro {
    @Override
    public boolean acceptsMacroName(String macroName) {
        return macroName.equals("PR_Title") || macroName.equals("PR_Email");
    }

    @Override
    public String evaluate(AbstractBuild<?, ?> context, TaskListener listener, String macroName) 
            throws MacroEvaluationException, IOException, InterruptedException {
        GhprbCause cause = (GhprbCause) context.getCause(GhprbCause.class);
        if (cause == null) {
            return "";
        }

        if (macroName.equals("PR_Title")) {
            return cause.getTitle();
        } else if (macroName.equals("PR_Email")) {
            return cause.getAuthorEmail();
        } else {
            return "";
        }
    }
}
