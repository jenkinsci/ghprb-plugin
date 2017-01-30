package org.jenkinsci.plugins.ghprb.jobdsl;

import groovy.util.Node;
import hudson.model.FreeStyleProject;
import hudson.util.VersionNumber;
import javaposse.jobdsl.dsl.*;
import javaposse.jobdsl.dsl.ExtensibleContext;
import javaposse.jobdsl.plugin.JenkinsJobManagement;


import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.stapler.RequestImpl;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Created by benpatterson on 1/26/17.
 */
public class GhprbSimpleStatusContextTest {


    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Mock
    private RequestImpl req;
    @Mock
    private GHIssueComment comment;


    private FreeStyleProject project;

    private JenkinsJobManagement jm = new JenkinsJobManagement(System.out, null, new File("."));

    private DslScriptLoader loader = new DslScriptLoader(jm);


    @Before
    public void setUp() throws Exception {



    }

    @Test
    public void shouldCreateJobVanilla() throws Exception {




    }

    @After
    public void tearDown() throws Exception {

    }

}