# GitHub Pull Request Builder Plugin

This Jenkins plugin builds pull requests from GitHub and will report the results directly to the pull request via
the [GitHub Commit Status API](http://developer.github.com/v3/repos/statuses/)

When a new pull request is opened in the project and the author of the pull
request isn't whitelisted, builder will ask ``Can one of the
admins verify this patch?``. One of the admins can comment ``ok to test``
to accept this pull request for testing, ``test this please`` for one time
test run and ``add to whitelist`` to add the author to the whitelist.

If an author of a pull request is whitelisted, adding a new pull
request or new commit to an existing pull request will start a new
build.

A new build can also be started with a comment: ``retest this please``.

You can extend standard build comment message on github 
creating ${WORKSPACE}/commentinfo.md file from shell console or any other
jenkins plugin. Contents of that file will be added to comment at GitHUB.
This is usefull for posting some build dependent urls for users without
access to jenkins UI console.

Jobs can be configured to only build if a matching comment is added to a pull request.  For instance, if you have two job you want to run against a pull request,
a smoke test job and a full test job, you can configure the full test job to only run if someone adds the comment ``full test please`` on the pull request.

For more details, see https://wiki.jenkins-ci.org/display/JENKINS/GitHub+pull+request+builder+plugin

### Master status:

[![Build Status](https://jenkins.ci.cloudbees.com/buildStatus/icon?job=plugins/ghprb-plugin)](https://jenkins.ci.cloudbees.com/job/plugins/job/ghprb-plugin/)

### Required Jenkins Plugins:
* github-api plugin (https://wiki.jenkins-ci.org/display/JENKINS/GitHub+API+Plugin)
* github plugin (https://wiki.jenkins-ci.org/display/JENKINS/GitHub+Plugin)
* git plugin (https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin)

### Pre-installation:
* I recommend to create GitHub 'bot' user that will be used for communication with GitHub (however you can use your own account if you want).
* The user needs to have push rights for your repository (must be collaborator (user repo) or must have Push & Pull rights (organization repo)).  
* If you want to use GitHub hooks have them set automatically the user needs to have administrator rights for your repository (must be owner (user repo) or must have Push, Pull & Administrative rights (organization repo))  

### Installation:
* Install the plugin.  
* Go to ``Manage Jenkins`` -> ``Configure System`` -> ``GitHub pull requests builder`` section.
* If you are using Enterprise GitHub set the server api URL in ``GitHub server api URL``. Otherwise leave there ``https://api.github.com``.
* Set your 'bot' user's Access Token.  
  * If you don't have generated your access token you can generate one in ``Advanced...``.  
    * Set your 'bot' user's GitHub username and password.  
    * Press the ``Create Access Token`` button  
    * Copy the generated Access Token into the field ``Access Token``
    * Clear the username and password fields
  * If you don't want to use Access Token leve the field empty and fill the username and password in ``Advanced...``.
* Add GitHub usernames of admins (these usernames will be used as defaults in new jobs).  
* Under Advanced, you can modify:  
  * The phrase for adding users to the whitelist via comment. (Java regexp)  
  * The phrase for accepting a pull request for testing. (Java regexp)
  * The phrase for starting a new build. (Java regexp)  
  * The crontab line. This specify default setting for new jobs.  
* Save to preserve your changes.  

### Creating a job:
* Create a new job.  
* Add the project's GitHub URL to the ``GitHub project`` field (the one you can enter into browser. eg: ``https://github.com/janinko/ghprb``)  
* Select Git SCM.  
* Add your GitHub ``Repository URL``.  
* Under Advanced, set ``refspec`` to ``+refs/pull/*:refs/remotes/origin/pr/*``.  
* In ``Branch Specifier``, enter ``${sha1}``.  
* Under ``Build Triggers``, check ``GitHub pull requests builder``.
  * Add admins for this specific job.  
  * If you want to use GitHub hooks for automatic testing, read the help for ``Use github hooks for build triggering`` in job configuration. Then you can check the checkbox.
  * In Advanced, you can modify:  
    * The crontab line for this specific job. This schedules polling to GitHub for new changes in Pull Requests.  
    * The whitelisted users for this specific job.  
    * The organisation names whose members are considered whitelisted for this specific job.  
* Save to preserve your changes.  

Make sure you **DON'T** have ``Prune remote branches before build`` advanced option selected, since it will prune the branch created to test this build.

If you want to manually build the job, in the job setting check ``This build is parameterized`` and add string parameter named ``sha1``. When starting build give the ``sha1`` parameter commit id you want to build or refname (eg: ``origin/pr/9/head``).


### Updates

#### -> 1.8
In version 1.8 the GitHub hook url changed from ``http://yourserver.com/jenkins/job/JOBNAME/ghprbhook`` to ``http://yourserver.com/jenkins/ghprbhook/``. This shouldn't be noticeable in most cases but you can have two webhooks configured in you repository.

#### -> 1.4
When updating to versions 1.4 phrases for retesting on existing pull requsts can stop working. The solution is comment in pull request with ``ok to test`` or remove and create the job. This is caused because there was change in phrases.
