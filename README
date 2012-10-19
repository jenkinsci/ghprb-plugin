This plugin builds pull requests from github and reports the results.

https://wiki.jenkins-ci.org/display/JENKINS/Github+pull+request+builder+plugin

When a new pull request is opened in the project and the author of the pull
request isn't whitelisted, builder will ask "Can one of the
admins verify this patch?" and one of the admins comment "this is ok to test"
to add the author to the whitelist.

If an author of a pull request is whitelisted, adding a new pull
request or new commit to an existing pull request will start a new
build.

A new build can also be started with a comment: "retest this please".

Requirements:
github-api plugin (https://wiki.jenkins-ci.org/display/JENKINS/GitHub+API+Plugin)
github plugin (https://wiki.jenkins-ci.org/display/JENKINS/Github+Plugin)
git plugin (https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin)

Installation:

Create a github 'bot' user for commenting in pull requests
then add the bot as a collaborator for your repository.

Install the plugin.
Go to Manage Jenkins -> Configure System -> "Github pull requests builder" section.
Set your bot's GitHub username and password.
Add GitHub usernames of admins for all jobs.
Under Advanced, you can modify:
  The phrase for adding users to the whitelist via comment.
  The phrase for starting a new build.
  The crontab line.
Save to preserve your changes.


Creating a job:

Create a new job.
Add the project's GitHub URL to the "GitHub project" field.
Select Git SCM.
Add your GitHub "Repository URL".
Under Advanced, set "refspec" to "+refs/pull/*:refs/remotes/origin/pr/*".
In "Branch Specifier", enter "${sha1}".
Under "Build Triggers", check "Github pull requests builder".
  Add admins for this specific job.
  Set the crontab line for this specific job
  Set the whitelisted users for this specific job.
Save to preserve your changes.

Make sure you *DON'T* have "Prune remote branches before build" advanced option
selected, since it will prune the branch created to test this build.

You are done :)
