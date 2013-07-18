package org.jenkinsci.plugins.ghprb;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

/**
 * @author janinko
 */
public class GhprbGitHub {
	private static final Logger logger = Logger.getLogger(GhprbGitHub.class.getName());
	private GitHub gh;

	private void connect() throws IOException{
		String accessToken = GhprbTrigger.getDscp().getAccessToken();
		String serverAPIUrl = GhprbTrigger.getDscp().getServerAPIUrl();
		if(accessToken != null && !accessToken.isEmpty()) {
			try {
				gh = GitHub.connectUsingOAuth(serverAPIUrl, accessToken);
			} catch(IOException e) {
				logger.log(Level.SEVERE, "Can''t connect to {0} using oauth", serverAPIUrl);
				throw e;
			}
		} else {
			gh = GitHub.connect(GhprbTrigger.getDscp().getUsername(), null, GhprbTrigger.getDscp().getPassword());
		}
	}

	public GitHub get() throws IOException{
		if(gh == null){
			connect();
		}
		return gh;
	}

	public boolean isUserMemberOfOrganization(String organisation, GHUser member){
		boolean orgHasMember = false;
		try {
			GHOrganization org = get().getOrganization(organisation);
			orgHasMember = org.hasMember(member);
			String yesNo = "no";
			if (orgHasMember){
				yesNo = "yes";
			}
			logger.log(Level.INFO, "org.hasMember(member)? user:" + member.getLogin() + " org: " + organisation + " == " + yesNo);

		} catch (IOException ex) {
			logger.log(Level.SEVERE, null, ex);
			return false;
		}
		return orgHasMember;
	}
}
