package org.jenkinsci.plugins.ghprb;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import org.jenkinsci.plugins.ghprb.extensions.GhprbExtension;
import org.jenkinsci.plugins.ghprb.extensions.GhprbGlobalDefault;
import org.jenkinsci.plugins.ghprb.extensions.build.GhprbCancelBuildsOnUpdate;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHPullRequest;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Unit test for {@link org.jenkinsci.plugins.ghprb.GhprbTriggerTest}.
 */
@RunWith(MockitoJUnitRunner.class)
public class GhprbTriggerTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    
    @Mock
    private GhprbPullRequest pr;
    
    @Mock 
    private Ghprb helper;
    
    @Test
    public void testGlobalExtensions() throws Exception {
        GhprbTrigger.getDscp().getExtensions().add(new GhprbCancelBuildsOnUpdate(false));
        
        GhprbTrigger trigger = GhprbTestUtil.getTrigger();
        
        for (GhprbExtension ext : trigger.getDescriptor().getExtensions()) {
            if (ext instanceof GhprbGlobalDefault) {
                assertThat(trigger.getExtensions().contains(ext));
            }
        }
    }

    @Test
    public void testCheckSkipBuild() throws Exception {
        GHPullRequest issue = mock(GHPullRequest.class);
        
        boolean skipBuild = false;
        boolean build = true;
        
        String nonMatch = "Some dumb comment\r\nThat shouldn't match";
        String multiLine = "This is a multiline skip\r\n[skip ci]";
        String justSkipCi = "skip ci";
        String fullSkipCi = "[skip ci]";
        
        Map<String, Map<String, Boolean>> stringsToTest = new HashMap<String, Map<String, Boolean>>(10);
        
        Map<String, Boolean> comment = new HashMap<String, Boolean>(5);
        comment.put(nonMatch, build);
        comment.put(multiLine, skipBuild);
        comment.put(justSkipCi, build);
        comment.put(fullSkipCi, skipBuild);
        stringsToTest.put(".*\\[skip\\W+ci\\].*", comment);
        
        comment = new HashMap<String, Boolean>(5);
        comment.put(nonMatch, build);
        comment.put(multiLine, build);
        comment.put(justSkipCi, build);
        comment.put(fullSkipCi, skipBuild);
        stringsToTest.put("\\[skip ci\\]", comment);
        
        comment = new HashMap<String, Boolean>(5);
        comment.put(nonMatch, build);
        comment.put(multiLine, skipBuild);
        comment.put(justSkipCi, skipBuild);
        comment.put(fullSkipCi, skipBuild);
        stringsToTest.put("\\[skip ci\\]\n.*\\[skip\\W+ci\\].*\nskip ci", comment);
        
        Method checkSkip = GhprbPullRequest.class.getDeclaredMethod("checkSkipBuild");
        checkSkip.setAccessible(true);
        

        Field prField = GhprbPullRequest.class.getDeclaredField("pr");
        prField.setAccessible(true);
        prField.set(pr, issue);

        Field shouldRun = GhprbPullRequest.class.getDeclaredField("shouldRun");
        shouldRun.setAccessible(true);
        
        Field prHelper = GhprbPullRequest.class.getDeclaredField("helper");
        prHelper.setAccessible(true);
        prHelper.set(pr, helper);

        for (Entry<String, Map<String, Boolean>> skipMap : stringsToTest.entrySet()) {
            String skipPhrases = skipMap.getKey();

            Set<String> phrases = new HashSet<String>(Arrays.asList(skipPhrases.split("[\\r\\n]+")));

            given(helper.getSkipBuildPhrases()).willReturn(phrases);
            
            for (Entry<String, Boolean> skipResults : skipMap.getValue().entrySet()) {
                String nextComment = skipResults.getKey();
                Boolean shouldBuild = skipResults.getValue();
                
                given(issue.getBody()).willReturn(nextComment);
                String skipPhrase = "";
                
                for (String skipBuildPhrase : phrases) {
                    skipBuildPhrase = skipBuildPhrase.trim();
                    Pattern skipBuildPhrasePattern = Ghprb.compilePattern(skipBuildPhrase);
                   
                    if (skipBuildPhrasePattern.matcher(nextComment).matches()) {
                        skipPhrase = skipBuildPhrase;
                        break;
                    }
                }
                
                given(helper.checkSkipBuild(issue)).willReturn(skipPhrase);
                
                shouldRun.set(pr, true);
                checkSkip.invoke(pr);
                String errorMessage = String.format("Comment does %scontain skip phrase \n(\n%s\n)\n[\n%s\n]", shouldBuild ? "not ": "", nextComment, skipPhrases);
                
                if (shouldBuild) {
                    assertThat(skipPhrase).overridingErrorMessage(errorMessage).isEmpty();
                } else {
                    assertThat(skipPhrase).overridingErrorMessage(errorMessage).isNotEmpty();
                }
                
                assertThat(shouldRun.get(pr)).overridingErrorMessage(errorMessage).isEqualTo(shouldBuild);

            }
        }
    }
    

}
