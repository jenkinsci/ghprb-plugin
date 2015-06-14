xml = namespace("http://www.w3.org/XML/1998/namespace")
j = namespace("jelly:core")
f = namespace("/lib/form")

f.entry(field: "gitHubAuthId", title:_("GitHub API credentials")) {
  f.select()
}

f.entry(field: "adminlist", title: _("Admin list")) {
  f.textarea(default: descriptor.adminlist) 
}
f.entry(field: "useGitHubHooks", title: "Use github hooks for build triggering") {
  f.checkbox() 
}
f.advanced() {
  f.entry(field: "triggerPhrase", title: _("Trigger phrase")) {
    f.textbox() 
  }
  f.entry(field: "onlyTriggerPhrase", title: "Only use trigger phrase for build triggering") {
    f.checkbox() 
  }
  f.entry(field: "autoCloseFailedPullRequests", title: _("Close failed pull request automatically?")) {
    f.checkbox(default: descriptor.autoCloseFailedPullRequests) 
  }
  f.entry(field: "skipBuildPhrase", title: _("Skip build phrase")) {
    f.textarea(default: descriptor.skipBuildPhrase) 
  }
  f.entry(field: "displayBuildErrorsOnDownstreamBuilds", title: _("Display build errors on downstream builds?")) {
    f.checkbox(default: descriptor.displayBuildErrorsOnDownstreamBuilds) 
  }
  f.entry(field: "cron", title: _("Crontab line"), help: "/descriptor/hudson.triggers.TimerTrigger/help/spec") {
    f.textbox(default: descriptor.cron, checkUrl: "'descriptorByName/hudson.triggers.TimerTrigger/checkSpec?value=' + encodeURIComponent(this.value)") 
  }
  f.entry(field: "whitelist", title: _("White list")) {
    f.textarea() 
  }
  f.entry(field: "orgslist", title: _("List of organizations. Their members will be whitelisted.")) {
    f.textarea() 
  }
  f.entry(field: "allowMembersOfWhitelistedOrgsAsAdmin", title: "Allow members of whitelisted organizations as admins") {
    f.checkbox() 
  }
  f.entry(field: "permitAll", title: "Build every pull request automatically without asking (Dangerous!).") {
    f.checkbox() 
  }
  f.entry(field: "whiteListTargetBranches", title: _("Whitelist Target Branches:")) {
    f.repeatable(field: "whiteListTargetBranches", minimum: "1", add: "Add Branch") {
      table(width: "100%") {
        f.entry(field: "branch") {
          f.textbox() 
        }
        f.entry(title: "") {
          div(align: "right") {
            f.repeatableDeleteButton(value: "Delete Branch") 
          }
        }
      }
    }
  }
}
f.advanced(title: _("Trigger Setup")) {
  f.entry(title: _("Trigger Setup")) {
    f.hetero_list(items: instance == null ? null : instance.extensions, 
        name: "extensions", oneEach: "true", hasHeader: "true", descriptors: descriptor.getExtensionDescriptors()) 
  }
}
