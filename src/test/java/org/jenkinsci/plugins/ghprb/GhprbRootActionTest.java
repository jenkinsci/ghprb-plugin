package org.jenkinsci.plugins.ghprb;



import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.IOException;
import java.net.URLEncoder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class GhprbRootActionTest {


    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Mock
    private StaplerRequest req;
    
    private BufferedReader br;
    
    @Before
    public void setup() throws Exception {

    }
    
    @Test
    public void testUrlEncoded() throws IOException {
        BufferedReader br = new BufferedReader(new StringReader(
                "payload=" + URLEncoder.encode(GhprbTestUtil.PAYLOAD, "UTF-8")));

        given(req.getContentType()).willReturn("application/x-www-form-urlencoded");
        given(req.getReader()).willReturn(br);
        given(req.getCharacterEncoding()).willReturn("UTF-8");
        given(req.getHeader("X-GitHub-Event")).willReturn("issue_comment");

        GhprbRootAction ra = new GhprbRootAction();
        ra.doIndex(req, null);
    }
    
    @Test
    public void testJson() throws Exception {
        given(req.getContentType()).willReturn("application/json");
        given(req.getHeader("X-GitHub-Event")).willReturn("issue_comment");

        // convert String into InputStream
        InputStream is = new ByteArrayInputStream(GhprbTestUtil.PAYLOAD.getBytes());
     
        // read it with BufferedReader
        br = spy(new BufferedReader(new InputStreamReader(is)));
        
        given(req.getReader()).willReturn(br);
        
        GhprbRootAction ra = new GhprbRootAction();
        ra.doIndex(req, null);
        
        verify(br, times(1)).close();
    }
    

}
