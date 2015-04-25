package org.jenkinsci.plugins.ghprb;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.github.GHIssue;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Unit test for {@link org.jenkinsci.plugins.ghprb.GhprbTriggerTest}.
 */
@RunWith(MockitoJUnitRunner.class)
public class GhprbTriggerTest {

    @Mock
    private GhprbPullRequest pr;

    @Test
    public void testCheckSkipBuild() throws Exception {
        GHIssue issue = mock(GHIssue.class);

        String[] comments = { "Some dumb comment\r\nThat shouldn't match", "[skip ci]" };
        String[] phraseArray = { "\\[skip\\W+ci\\]", "skip ci" };

        Method checkSkip = GhprbPullRequest.class.getDeclaredMethod("checkSkipBuild", GHIssue.class);
        checkSkip.setAccessible(true);

        Field shouldRun = GhprbPullRequest.class.getDeclaredField("shouldRun");
        shouldRun.setAccessible(true);

        for (String phraseString : phraseArray) {
            for (String comment : comments) {

                Set<String> phrases = new HashSet<String>(Arrays.asList(phraseString.split("[\\r\\n]+")));
                given(issue.getBody()).willReturn(comment);
                given(pr.getSkipBuildPhrases()).willReturn(phrases);
                boolean isMatch = false;
                for (String phrase : phrases) {
                    isMatch = Pattern.matches(phrase, comment);
                    if (isMatch) {
                        break;
                    }
                }
                shouldRun.set(pr, true);
                checkSkip.invoke(pr, issue);
                assertThat(shouldRun.get(pr)).isEqualTo(!isMatch);

            }
        }

    }

}
