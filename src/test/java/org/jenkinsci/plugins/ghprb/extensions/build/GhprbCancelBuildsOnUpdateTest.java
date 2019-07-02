package org.jenkinsci.plugins.ghprb.extensions.build;

import hudson.model.FreeStyleProject;
import org.jenkinsci.plugins.ghprb.GhprbITBaseTestCase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class GhprbCancelBuildsOnUpdateTest extends GhprbITBaseTestCase {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private FreeStyleProject project;

    private GhprbCancelBuildsOnUpdate gcbou;

    @Before
    public void setUp() throws Exception {
        project = jenkinsRule.getInstance().createProject(FreeStyleProject.class, "FSPRJ");

        Map<String, Object> config = new HashMap<>(1);

        super.beforeTest(config, null, project);

        given(ghprbPullRequest.getPullRequestAuthor()).willReturn(ghUser);

        gcbou = new GhprbCancelBuildsOnUpdate(false);
    }

    @Test
    public void testCancelCurrentBuilds() {
        builds.build(ghprbPullRequest, ghUser, "");
        gcbou.cancelCurrentBuilds(project, 1);
    }
}
