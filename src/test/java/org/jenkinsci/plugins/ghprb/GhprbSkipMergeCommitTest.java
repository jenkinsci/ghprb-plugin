package org.jenkinsci.plugins.ghprb;

import java.net.URL;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitUser;
import org.mockito.runners.MockitoJUnitRunner;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class GhprbSkipMergeCommitTest extends GhprbITBaseTestCase {


    private GHUser ghuser = mock(GHUser.class);
    private GitUser gituser = mock(GitUser.class);

        @Rule
        public JenkinsRule jenkinsRule = new JenkinsRule();

        @Before
        public void setUp() throws Exception {
            super.beforeTest();
        }

        @Test
        public void shouldUseMergeCommitWhenFlagIsNotSet() throws Exception {
            // GIVEN

            URL url = new URL("http://example.com");
            Boolean skipMergeCommitFlag = false;
            GhprbTrigger trigger = new GhprbTrigger(
                    "user", "user", "", "*/1 * * * *", "retest this please", false, false, false, false, false, null, null, false,
                    null, null, skipMergeCommitFlag
            );

            GhprbCause cause = new GhprbCause("c0mmit", 57, true, "targetBranch", "sourceBranch", "email@fake.com", "title", url, ghuser, "comment\nbody", gituser, skipMergeCommitFlag);

            // THEN
            assertThat(trigger.getSkipMergeCommit()).isEqualTo(Boolean.FALSE);
            assertThat(cause.isMerged()).isEqualTo(true);
            assertThat(trigger.getCommitShaString(cause)).isEqualTo("origin/pr/57/merge");
        }

        @Test
        public void shouldNotUseMergeCommitWhenFlagIsNotSetAndNoMergeExists() throws Exception {
            // GIVEN
            URL url = new URL("http://example.com");
            // User wants to use merge commits
            Boolean skipMergeCommitFlag = false;
            // ..But in this case, the code cannot be automatically merged by GitHub
            Boolean prMergeProperty = false;

            GhprbTrigger trigger = new GhprbTrigger(
                    "user", "user", "", "*/1 * * * *", "retest this please", false, false, false, false, false, null, null, false,
                    null, null, skipMergeCommitFlag
            );
            GhprbCause cause = new GhprbCause("c0mmit", 57, prMergeProperty, "targetBranch", "sourceBranch", "email@fake.com", "title", url, ghuser, "comment\nbody", gituser, skipMergeCommitFlag);

            // THEN
            assertThat(trigger.getSkipMergeCommit()).isEqualTo(Boolean.FALSE);
            assertThat(cause.isMerged()).isEqualTo(false);
            assertThat(trigger.getCommitShaString(cause)).isEqualTo("c0mmit");
        }

        @Test
        public void shouldNotUseMergeCommitWhenFlagIsSet() throws Exception {
            // GIVEN
            URL url = new URL("http://example.com");

            Boolean skipMergeCommitFlag = true;
            GhprbTrigger trigger = new GhprbTrigger(
                    "user", "user", "", "*/1 * * * *", "retest this please", false, false, false, false, false, null, null, false,
                    null, null, skipMergeCommitFlag
            );
            GhprbCause cause = new GhprbCause("c0mmit", 57, true, "targetBranch", "sourceBranch", "email@fake.com", "title", url, ghuser, "comment\nbody", gituser, skipMergeCommitFlag);

            // THEN
            assertThat(trigger.getSkipMergeCommit()).isEqualTo(Boolean.TRUE);
            assertThat(cause.isMerged()).isEqualTo(false);
            assertThat(trigger.getCommitShaString(cause)).isEqualTo("c0mmit");
        }


    }

