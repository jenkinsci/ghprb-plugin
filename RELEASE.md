# Release process

This document is for maintainers to release versions of the GHPRB plugin.

The following release process is a little intense.  However, it serves as a high
quality assurance test which users will appreciate when they go to upgrade their
own Jenkins and nothing breaks from GHPRB.

- [Before releasing](#before-releasing)
- [Steps to release](#steps-to-release)

# Before releasing

Summary:

1. Setup Jenkins with GHPRB.
2. Track `$JENKINS_HOME` configuration with git.
3. Upgrade the plugin and check for any differences in `$JENKINS_HOME` which
   would negatively affect users. The configs should not break and Jenkins
   should cleanly migrate existing configurations.  That is, configured settings
   and jobs should be able to upgrade.

### 1. Setup Jenkins with GHPRB

Test that the plugin properly upgrades configuration without breaking existing
configuration.  Provision Jenkins LTS by downloading `jenkins.war`.  Start
Jenkins with the following command.

```
export JENKINS_HOME="./my_jenkins_home"
java -jar jenkins.war
```

Install the GHPRB plugin from the Jenkins update center.  Configure a dummy job
with GHPRB configured both globally and in the job.

### 2. Track `JENKINS_HOME` with Git

ref: https://github.com/github/gitignore/pull/1763

```
cd my_jenkins_home/
curl -Lo .gitignore https://raw.githubusercontent.com/samrocketman/gitignore/jenkins-gitignore/JENKINS_HOME.gitignore
git init
git add -A
git commit -m 'initial commit'
```

### 3. Upgrade the plugin

Build the latest development `master` branch for the GHPRB plugin.  It should
create `target/ghprb.hpi`.  In the Jenkins web UI you can upgrade the plugin by
visiting the following:

- Jenkins > Manage Jenkins > Plugin Manager > Advanced > Upload plugin

Upload your built `ghprb.hpi`.  Restart Jenkins by visiting
`http://localhost:8080/restart`.  After Jenkins has finished restarted, visit
the `$JENKINS_HOME` and view any changed configuration.

```
cd my_jenkins_home/
git status
git diff
```

If the migrated XML config looks OK and the jobs and settings you configured
still work, then proceed to a release.

# Steps to release

This outlines the maintainers steps to release the Jenkins GitHub Pull Request
Builder Plugin.  Follow the Jenkins documentation for [making a new
release][plugin-release].

- [ ] Configure your credentials in `~/.m2/settings.xml`. (outlined in [making a
      new release][plugin-release] doc)
- [ ] Create a new issue to track the release and give it the label `maintainer
      communication`.
- [ ] Create a release branch. `git checkout origin/master -b prepare_release`
- [ ] Update the release notes in `CHANGELOG.md`.
- [ ] Open a pull request from `prepare_release` branch to `master` branch.
      Merge it.
- [ ] Fetch the latest `master`.
- [ ] Clean the workspace `git clean -xfd`.
- [ ] Execute the release plugin.

    ```
    mvn release:prepare release:perform
    ```

- [ ] Wait for the plugin to be released into the Jenkins Update Center.  It
      takes roughly 8 hours for a release.
- [ ] Successfully perform an upgrade from the last stable plugin release to the
      current release.

See also the [release section of hosting plugins][plugin-release].


[plugin-release]: https://wiki.jenkins.io/display/JENKINS/Hosting+Plugins#HostingPlugins-Releasingtojenkins-ci.org
