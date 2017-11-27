package org.jenkinsci.plugins.ghprb;

import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHUser;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author janinko
 */
public class GhprbGitHub {
    private static final Logger LOGGER = Logger.getLogger(GhprbGitHub.class.getName());

    private final GhprbTrigger trigger;

    public GhprbGitHub(GhprbTrigger trigger) {
        this.trigger = trigger;
    }

    public boolean isUserMemberOfOrganization(String organisation, GHUser member) {
        boolean orgHasMember = false;
        try {
            GHOrganization org = trigger.getGitHub().getOrganization(organisation);
            orgHasMember = org.hasMember(member);
            LOGGER.log(Level.FINE, "org.hasMember(member)? user:{0} org: {1} == {2}",
                    new Object[] {member.getLogin(), organisation, orgHasMember ? "yes" : "no"});

        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            return false;
        }
        return orgHasMember;
    }

    public String getBotUserLogin() {
        try {
            return trigger.getGitHub().getMyself().getLogin();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            return null;
        }
    }
}
