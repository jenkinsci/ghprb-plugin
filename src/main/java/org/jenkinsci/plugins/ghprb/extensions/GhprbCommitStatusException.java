package org.jenkinsci.plugins.ghprb.extensions;

import org.kohsuke.github.GHCommitState;

import java.io.IOException;

public class GhprbCommitStatusException extends Exception {
    private static final long serialVersionUID = 6220095323686649609L;

    private final IOException exception;

    private final String message;

    private final GHCommitState state;

    private final int id;

    public GhprbCommitStatusException(IOException exception, GHCommitState state, String message, int id) {
        this.exception = exception;
        this.state = state;
        this.message = message;
        this.id = id;
    }

    public IOException getException() {
        return exception;
    }

    public String getMessage() {
        return message;
    }

    public GHCommitState getState() {
        return state;
    }

    public int getId() {
        return id;
    }

}
