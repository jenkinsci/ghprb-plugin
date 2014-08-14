package org.jenkinsci.plugins.ghprb;

import java.io.IOException;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.kohsuke.github.GHBranch;
//import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHPullRequestCommitDetail.Commit;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHUser;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;

public class GhprbPullRequestMerge extends Recorder {
	
    private static final Logger logger = Logger.getLogger(GhprbPullRequestMerge.class.getName());
    
	
	private final boolean onlyAdminsMerge;
	private final boolean disallowOwnCode;
	private boolean onlyTriggerPhrase;
	private String mergeComment;

	@DataBoundConstructor
	public GhprbPullRequestMerge(String mergeComment, boolean onlyTriggerPhrase,
			boolean onlyAdminsMerge, boolean disallowOwnCode) {
		
		this.mergeComment = mergeComment;
		this.onlyTriggerPhrase = onlyTriggerPhrase;
		this.onlyAdminsMerge = onlyAdminsMerge;
		this.disallowOwnCode = disallowOwnCode;
	}
	
	public String getMergeComment() {
		return mergeComment;
	}
	
	public boolean isOnlyTriggerPhrase() {
		return onlyTriggerPhrase;
	}
	
	public boolean isOnlyAdminsMerge() {
		return onlyAdminsMerge;
	}
	
	public boolean isDisallowOwnCode() {
		return disallowOwnCode;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.BUILD;
	}
	
	private GhprbTrigger trigger;
	private Ghprb helper;
	private GhprbCause cause;
	private GHPullRequest pr;
	
	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
		AbstractProject<?, ?> project = build.getProject();
		if (build.getResult().isWorseThan(Result.SUCCESS)) {
			logger.log(Level.INFO, "Build did not succeed, merge will not be run");
			return true;
		}
		
		trigger = GhprbTrigger.extractTrigger(project);
		if (trigger == null) return false;
		
		ConcurrentMap<Integer, GhprbPullRequest> pulls = trigger.getDescriptor().getPullRequests(project.getName()); 
		
		helper = new Ghprb(project, trigger, pulls);
		helper.getRepository().init();
		
		cause = getCause(build);
		if (cause == null) {
			return true;
		}
		pr = helper.getRepository().getPullRequest(cause.getPullID());
		
		if (pr == null) {
			logger.log(Level.INFO, "Pull request is null for ID: " + cause.getPullID());
			return false;
		}
		
		Boolean isMergeable = pr.getMergeable();
		int counter = 0;
		while (counter++ < 15) {
			if (isMergeable != null) {
				break;
			}
			try {
				logger.log(Level.INFO, "Waiting for github to settle so we can check if the PR is mergeable.");
				Thread.sleep(1000);
			} catch (Exception e) {
				
			}
			isMergeable = pr.getMergeable();
		}
		
		if (isMergeable == null || isMergeable) {
			logger.log(Level.INFO, "Pull request cannot be automerged, moving on.");
	    	commentOnRequest("Pull request is not mergeable.");
			return true;
		}
		
		GHUser commentor = cause.getTriggerSender();
		
		boolean merge = true;
		

		if (isOnlyAdminsMerge() && !helper.isAdmin(commentor.getLogin())){
			merge = false;
			logger.log(Level.INFO, "Only admins can merge this pull request, {0} is not an admin.", 
					new Object[]{commentor.getLogin()});
	    	commentOnRequest(
	    			String.format("Code not merged because %s is not in the Admin list.", 
	    					commentor.getName()));
		}
		
		if (isOnlyTriggerPhrase() && !helper.isTriggerPhrase(cause.getCommentBody())) {
			merge = false;
			logger.log(Level.INFO, "The comment does not contain the required trigger phrase.");

	    	commentOnRequest(
	    			String.format("Please comment with '%s' to automerge this request", 
	    					trigger.getTriggerPhrase()));
	    	return true;
		}
		
	    if (isDisallowOwnCode() && isOwnCode(pr, commentor)) {
			merge = false;
			logger.log(Level.INFO, "The commentor is also one of the contributors.");
	    	commentOnRequest(
	    			String.format("Code not merged because %s has committed code in the request.", 
	    					commentor.getName()));
	    }
	    
	    if (merge) {
	    	logger.log(Level.INFO, "Merging the pull request");
	    	pr.merge(getMergeComment());
//	    	deleteBranch(); //TODO: Update so it also deletes the branch being pulled from.  probably make it an option.
	    }
		
		return merge;
	}
	
	private void deleteBranch() {
		String branchName = pr.getHead().getRef();
		try {
			GHBranch branch = pr.getRepository().getBranches().get(branchName);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void commentOnRequest(String comment) {
		helper.getRepository().addComment(pr.getNumber(), comment);
	}
	
	
	private boolean isOwnCode(GHPullRequest pr, GHUser commentor) {
		try {
			String commentorName = commentor.getName();
			for (GHPullRequestCommitDetail detail : pr.listCommits()) {
				Commit commit = detail.getCommit();
				String committerName = commit.getCommitter().getName();
				
				if (committerName.equalsIgnoreCase(commentorName)) {
					return true;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
	
    private GhprbCause getCause(AbstractBuild build) {
        Cause cause = build.getCause(GhprbCause.class);
        if (cause == null || (!(cause instanceof GhprbCause))) return null;
        return (GhprbCause) cause;
    }
	
	
	@Extension(ordinal=-1)
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
		@Override
		public String getDisplayName() {
			return "Github Pull Request Merger";
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}
		
		public FormValidation doCheck(@AncestorInPath AbstractProject project, @QueryParameter String value)
            throws IOException  {
            return FilePath.validateFileMask(project.getSomeWorkspace(),value);
        }

		
	}

}
