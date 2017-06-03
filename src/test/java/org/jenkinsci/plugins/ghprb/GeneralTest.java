package org.jenkinsci.plugins.ghprb;

import org.jenkinsci.plugins.ghprb.extensions.build.GhprbCancelBuildsOnUpdate;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildLog;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildResultMessage;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildStatus;
import org.jenkinsci.plugins.ghprb.extensions.comments.GhprbCommentFile;
import org.jenkinsci.plugins.ghprb.extensions.status.GhprbSimpleStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class GeneralTest {

    private void checkClassForGetters(Class<?> clazz) {
        List<String> errors = GhprbTestUtil.checkClassForGetters(clazz);
        assertThat(errors).isEmpty();
    }

    @Test
    public void checkTriggerForGetters() {
        checkClassForGetters(GhprbTrigger.class);
    }

    @Test
    public void checkTriggerDescriptorForGetters() {
        checkClassForGetters(GhprbTrigger.DescriptorImpl.class);
    }

    @Test
    public void checkPullRequestMergeForGetters() {
        checkClassForGetters(GhprbPullRequestMerge.class);
    }

    @Test
    public void checkBuildLogForGetters() {
        checkClassForGetters(GhprbBuildLog.class);
    }

    @Test
    public void checkBuildResultMessageForGetters() {
        checkClassForGetters(GhprbBuildResultMessage.class);
    }

    @Test
    public void checkBuildStatusForGetters() {
        checkClassForGetters(GhprbBuildStatus.class);
    }

    @Test
    public void checkCommentFileForGetters() {
        checkClassForGetters(GhprbCommentFile.class);
    }

    @Test
    public void checkSimpleStatusForGetters() {
        checkClassForGetters(GhprbSimpleStatus.class);
    }

    @Test
    public void checkCancelBuildsOnUpdateForGetters() {
        checkClassForGetters(GhprbCancelBuildsOnUpdate.class);
    }
}
