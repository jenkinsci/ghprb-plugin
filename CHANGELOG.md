### Updates


#### -> 1.33.1
* Handle 'edited' pull request hooks
* Fix NPE when creating hooks

#### -> 1.33.0
* Ability to blacklist branches

#### -> 1.32.8
* Setting a TODO on getting comment counts

#### -> 1.32.7
* Some NPE fixes, some doc fixes
* Simpler ingestion of environment variables

#### -> 1.32.6
* Set the pull request after updating the updated timestamp. [See PR 368]

#### -> 1.32.5
* Pull new data in the case of a mergeability check

#### -> 1.32.4
* Don't check for comments when a new PR is opened.

#### -> 1.32.3
* Set the parameters again.

#### -> 1.32.2
* Remove adding parameters action from the env contributor; just add them all.

#### -> 1.32.1
* Allow for manual managing of webhooks
* Various fixes, like:
  * Avoid overwriting test results when there's a race condition
  * Reduce API calls
  * Skip build phrase fix and improvements (PR#339)
  * Security 170
  * Downstream job fixes

#### -> 1.31.4
* Update Github API version
* Avoid NPE if cause is null
* Refactoring around thread usage:
  * Updated core Jenkins version used in order to fix an issue with Matrix job types
  * The trigger regex is something the admin configures. Handle regex issues gracefully to avoid a build queue blockage.
  * Handle webhooks in separate threads
  * Handle triggers with threads

#### -> 1.31.3
* Catch configuration errors made by user
* Expand merge comment

#### -> 1.31.2
* Fixes for bugs related canceled builds and global overrides

#### -> 1.31.1
* Various changes to support mvn & java library upgrades
* Other refactors

#### -> 1.30.7 & 1.30.8
* These were rolled back

#### -> 1.30.6
* Fix "no test results found". Interpreting test results is now avaiable as a configuration setting, and is off by default.
* If user is whitelisted after his/her PR was opened, allow testing on the PR going forward.

#### -> 1.30.5
* README fixes
* Fix NPE when last build has been deleted
* Inherit some global configuration to individual projects
* Add extension: when a new build is queued, it will abort any running builds for that pull request, and remove any older ones from the queue. (This is the default but comes with a configuration setting to override it.)

#### -> 1.30.4
* Fix NPE for Ghprb.extractTrigger

#### -> 1.30.3
* Use `get*` instead of `is*` as a convention for methods

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
