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

You can extend the standard build comment message on github 
creating a comment file from shell console or any other 
jenkins plugin. Contents of that file will be added to the comment on GitHub. 
This is useful for posting some build dependent urls for users without 
access to the jenkins UI console.

Jobs can be configured to only build if a matching comment is added to a pull request.  For instance, if you have two job you want to run against a pull request,
a smoke test job and a full test job, you can configure the full test job to only run if someone adds the comment ``full test please`` on the pull request.

For more details, see https://wiki.jenkins-ci.org/display/JENKINS/GitHub+pull+request+builder+plugin

### Master status:

[![Build Status](https://jenkins.ci.cloudbees.com/buildStatus/icon?job=plugins/ghprb-plugin)](https://jenkins.ci.cloudbees.com/job/plugins/job/ghprb-plugin/)

### Required Jenkins Plugins:
* github-api plugin (https://wiki.jenkins-ci.org/display/JENKINS/GitHub+API+Plugin)
* github plugin (https://wiki.jenkins-ci.org/display/JENKINS/GitHub+Plugin)
* git plugin (https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin)
* credentials plugin (https://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin)
* plain credentials plugin (https://wiki.jenkins-ci.org/display/JENKINS/Plain+Credentials+Plugin)

### Pre-installation:
* I recommend to create GitHub 'bot' user that will be used for communication with GitHub (however you can use your own account if you want).
* The user needs to have push rights for your repository (must be collaborator (user repo) or must have Push & Pull rights (organization repo)).  
* If you want to use GitHub hooks have them set automatically the user needs to have administrator rights for your repository (must be owner (user repo) or must have Push, Pull & Administrative rights (organization repo))  

### Installation:
* Install the plugin.  
* Go to ``Manage Jenkins`` -> ``Configure System`` -> ``GitHub pull requests builder`` section.

* Add GitHub usernames of admins (these usernames will be used as defaults in new jobs).  
* Under Advanced, you can modify:  
  * The phrase for adding users to the whitelist via comment. (Java regexp)  
  * The phrase for accepting a pull request for testing. (Java regexp)
  * The phrase for starting a new build. (Java regexp)  
  * The crontab line. This specify default setting for new jobs.  
* Under Application Setup
  * There are global and job default extensions that can be configured for things like:
    * Commit status updates
    * Build status messages
    * Adding lines from the build log to the build result message
    * etc.
* Save to preserve your changes.  

### Credentials
* If you are using Enterprise GitHub set the server api URL in ``GitHub server api URL``. Otherwise leave there ``https://api.github.com``.
* Set the Jenkins URL if you need to override the default (e.g. it's behind a firewall)
* A GitHub API token or username password can be used for access to the GitHub API
* To setup credentials for a given GitHub Server API URL:
  * Click Add next to the ``Credentials`` drop down
    * For a token select ``Kind`` -> ``Secret text``
      * If you haven't generated an access token you can generate one in ``Test Credentials...``.  
        * Set your 'bot' user's GitHub username and password.  
        * Press the ``Create Access Token`` button  
        * Jenkins will create a token credential, and give you the id of the newly created credentials.  The default description is: ``serverAPIUrl + " GitHub auto generated token credentials"``.
    * For username/password us ``Kind`` -> ``Username with password``
      * The scope determines what has access to the credentials you are about to create
    * The first part of the description is used to show different credentials in the drop down, so use something semi-descriptive
    * Click ``Add``
  * Credentials will automatically be created in the domain given by the ``GitHub Server API URL`` field.
  * Select the credentials you just created in the drop down.
  * The first fifty characters in the Description are used to differentiate credentials per job, so again use something semi-descriptive
* Add as many GitHub auth sections as you need, even duplicate server URLs


### Creating a job:
* Create a new job.  
* Add the project's GitHub URL to the ``GitHub project`` field (the one you can enter into browser. eg: ``https://github.com/janinko/ghprb``)  
* Select Git SCM.  
* Add your GitHub ``Repository URL``.  
* Under Advanced, set ``Name`` to ``origin`` and:
  * If you **just** want to build PRs, set ``refspec`` to ``+refs/pull/*:refs/remotes/origin/pr/*``
  * If you want to build PRs **and** branches, set ``refspec`` to ``+refs/heads/*:refs/remotes/origin/* +refs/pull/*:refs/remotes/origin/pr/*`` (see note below about [parameterized builds](#parameterized-builds))
* In ``Branch Specifier``, enter ``${sha1}`` instead of the default ``*/master``.
* If you want to use the actual commit in the pull request, use ``${ghprbActualCommit}`` instead of ``${sha1}``
* Under ``Build Triggers``, check ``GitHub pull requests builder``.
  * Add admins for this specific job.  
  * If you want to use GitHub hooks for automatic testing, read the help for ``Use github hooks for build triggering`` in job configuration. Then you can check the checkbox.
  * In Advanced, you can modify:  
    * The crontab line for this specific job. This schedules polling to GitHub for new changes in Pull Requests.  
    * The whitelisted users for this specific job.  
    * The organisation names whose members are considered whitelisted for this specific job.  
* Save to preserve your changes.  

Make sure you **DON'T** have ``Prune remote branches before build`` advanced option selected, since it will prune the branch created to test this build.  

#### Parameterized Builds
If you want to manually build the job, in the job setting check ``This build is parameterized`` and add string parameter named ``sha1`` with a default value of ``master``. When starting build give the ``sha1`` parameter commit id you want to build or refname (eg: ``origin/pr/9/head``).

### Job DSL Support

Since the plugin contains an extension for the Job DSL plugin to add DSL syntax for configuring the build trigger and
the pull request merger post-build action.

It is also possible to set Downstream job commit statuses when `displayBuildErrorsOnDownstreamBuilds()` is set in the
upstream job's triggers and downstreamCommitStatus block is included in the downstream job's wrappers.

Here is an example showing all DSL syntax elements:

```groovy
job('upstreamJob') {
    scm {
        git {
            remote {
                github('test-owner/test-project')
                refspec('+refs/pull/*:refs/remotes/origin/pr/*')
            }
            branch('${sha1}')
        }
    }

    triggers {
        githubPullRequest {
            admin('user_1')
            admins(['user_2', 'user_3'])
            userWhitelist('you@you.com')
            userWhitelist(['me@me.com', 'they@they.com'])
            orgWhitelist('my_github_org')
            orgWhitelist(['your_github_org', 'another_org'])
            cron('H/5 * * * *')
            triggerPhrase('OK to test')
            onlyTriggerPhrase()
            useGitHubHooks()
            permitAll()
            autoCloseFailedPullRequests()
            displayBuildErrorsOnDownstreamBuilds()
            whiteListTargetBranches(['master','test', 'test2'])
            allowMembersOfWhitelistedOrgsAsAdmin()
            extensions {
                commitStatus {
                    context('deploy to staging site')
                    triggeredStatus('starting deployment to staging site...')
                    startedStatus('deploying to staging site...')
                    statusUrl('http://mystatussite.com/prs')
                    completedStatus('SUCCESS', 'All is well')
                    completedStatus('FAILURE', 'Something went wrong. Investigate!')
                    completedStatus('PENDING', 'still in progress...')
                    completedStatus('ERROR', 'Something went really wrong. Investigate!')
                }
            }
        }
    }
    publishers {
        mergeGithubPullRequest {
            mergeComment('merged by Jenkins')
            onlyAdminsMerge()
            disallowOwnCode()
            failOnNonMerge()
            deleteOnMerge()
        }
    }
}

job('downstreamJob') {
    wrappers {
        downstreamCommitStatus {
            context('CONTEXT NAME')
            triggeredStatus("The job has triggered")
            startedStatus("The job has started")
            statusUrl()
            completedStatus('SUCCESS', "The job has passed")
            completedStatus('FAILURE', "The job has failed")
            completedStatus('ERROR', "The job has resulted in an error")
        }
    }
}
```

### Updates

#### -> 1.30.2
* Don't run through all the builds for changelog, track it in the PR object instead
* Synchronization around the PR object fields

#### -> 1.30.1
* Moved pull request state into build/pullrequests directory.
* Dynamic state is no longer kept as part of the trigger
* Merged #258 ignore comments on issues that aren't pull requests

#### -> 1.30
* Merged #253, cleaning up code.
* WebHooks are refactored to be closer to the variables it depends on
* PR data is now local per job instead of global
* The config.xml is only saved when there is a change to a PR the job is watching
* GitHub connections are now shared.
* Shouldn't run into rate limits on startup
* Pull Requests are only updated when the trigger runs, instead of on startup.

#### -> 1.29.8
* Merged #246 adding job dsl features and updating docs

#### -> 1.29.7
* Remove quoting of trigger phrase
* Merged #242 replaces newline and quoted quotes
* Merged #229 Add job dsl
* Merged #238 Update github-api version

#### -> 1.29.5 
* Merge #232 fixes erroneous no test cases found

#### -> 1.29.4
* Add secret when auto creating the web hook
* Accomodate Jenkins being behind a firewall
* Fix for recursive repo initializations.

#### -> 1.29.1
* Fix NPE for triggers without a project
* Add back default URL if env variables are missing

#### -> 1.29
* Reduced the size of the plugin .xml file drastically.
* Fixed issues with persistance store credentials.
* Reduced the noise caused by webhook logic, and reduced amount of work for webhooks

#### -> 1.28
* Fixed [JENKINS-29850] Add author github repo URL
* Fixed NPE and other errors in merge logic
* Removed only trigger phrase, trigger is expected always now
* Fixed [JENKINS-30115] Downstream jobs can now attach GitHub status messages
* Fixed SHA1 for Matrix builds
* Global defaults are now used if the local value isn't set.
* Fixed issues with CSR protection
* Commit updates can be skipped all together or individual steps can be skipped using --none--

#### -> 1.27
* Trigger is now a regex term
* Phrases are no matched agains multiple lines properly
* All regex patterns use ignore case and dotall
* Added variables to the parameters

#### -> 1.26
* Extend the checking for own code.
* Fail build only when desired if we can't merge the build.
* Add option to delete branch when merge succeeds.

#### -> 1.25
* Fix condition where admin can't run tests #60 JENKINS-25572, JENKINS-25574, JENKINS-25603
* Check for when webhook encoding is null and default to UTF-8
* Add configurable build message #36
* NullPointerException in GhprbTrigger.run() jankinko/ghprb#256
* Webhook repo checking now case insensitive #141

#### -> 1.24.x
* Fixed issues with new credential implementation.
* Fixed other issues.

#### -> 1.24
* Signature checking for webhooks
* Added fields to status updates for custom messages and a custom URL

#### -> 1.23 - 1.23.2
* Credentials are now handled by the jenkins credentials plugin
* Bug fixes due to switch to groovy config files.

#### -> 1.22.x
* Fix issue where if a project was disabled the Jenkins Trigger process would crash
* Fix commit status context
* Add one line test results for downstream builds
* Miscellaneous bug fixes

#### -> 1.22
* Move commit status over to extension form.  It is now configurable, satisfying #81, #73, and #19 at least.

#### -> 1.21
* Move all commenting logic out into extensions.

#### -> 1.20.1
* Null Pointer fix for trigger.
* Added clarity to error message when access is forbidden.

#### -> 1.20
* PullRequestMerger now notifies the taskListener of failures.
* AutoCloseFailedPullRequest has been extracted from the published URL check.

#### -> 1.19
* More work for disabled builds.
* Unified tabs to spaces.
* Updates to the tests, and added some tests.

#### -> 1.18
* Add support for folder projects.
* Correcting issue with default credentials.
* Correcting issue with ghRepository being null when it shouldn't be.
* Ignoring case when matching repo to url.
* Changing the wording for pull requests that are mergeable.
* Change requestForTesting phrase to a textArea.
* Check if project is disabled, if it is then don't do anything.

#### -> 1.14
* A comment file can be created during the build and added to any comment made to the pull request.  podarok#33
* Added a ``[skip ci]`` setting, that can be changed.  Adding the skip statement to the pull request body will cause the job not to run. sathiya-mit#29
* Escaping single quotes in log statements tIGO#38
* Fixed owner name deduction from url on github hook handling nikicat#40
* Removed unused Test field from the config

#### -> 1.13-1
* Replacing deprecated Github.connect method. tIGO#39
* Added a merge plugin for post build.  If the build is successful, the job can specify conditions under which the pull request "button" will be pressed.  

#### -> 1.8
In version 1.8 the GitHub hook url changed from ``http://yourserver.com/jenkins/job/JOBNAME/ghprbhook`` to ``http://yourserver.com/jenkins/ghprbhook/``. This shouldn't be noticeable in most cases but you can have two webhooks configured in you repository.

#### -> 1.4
When updating to versions 1.4 phrases for retesting on existing pull requsts can stop working. The solution is comment in pull request with ``ok to test`` or remove and create the job. This is caused because there was change in phrases.
