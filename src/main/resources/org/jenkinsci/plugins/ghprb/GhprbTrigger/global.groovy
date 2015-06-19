j = namespace("jelly:core")
f = namespace("/lib/form")


f.section(title: descriptor.displayName) {
  f.entry(field: "githubAuth", title: _("GitHub Auth")) {
    f.repeatableProperty(field: "githubAuth", default: descriptor.getGithubAuth()) 
  }
  f.entry(field: "useComments", title: _("Use comments to report results when updating commit status fails")) {
    f.checkbox() 
  }
  f.entry(field: "useDetailedComments", title: _("Use comments to report intermediate phases: triggered et al")) {
    f.checkbox() 
  }
  f.entry(field: "adminlist", title: _("Admin list")) {
    f.textarea() 
  }
  f.advanced() {
    f.entry(field: "unstableAs", title: _("Mark Unstable build in github as")) {
      f.select() 
    }
    f.entry(field: "autoCloseFailedPullRequests", title: _("Close failed pull request automatically?")) {
      f.checkbox() 
    }
    f.entry(field: "displayBuildErrorsOnDownstreamBuilds", title: _("Display build errors on downstream builds?")) {
      f.checkbox() 
    }
    f.entry(field: "requestForTestingPhrase", title: _("Request for testing phrase")) {
      f.textarea(default: "Can one of the admins verify this patch?") 
    }
    f.entry(field: "whitelistPhrase", title: _("Add to white list phrase")) {
      f.textbox(default: ".*add\\W+to\\W+whitelist.*") 
    }
    f.entry(field: "okToTestPhrase", title: _("Accept to test phrase")) {
      f.textbox(default: ".*ok\\W+to\\W+test.*") 
    }
    f.entry(field: "retestPhrase", title: _("Test phrase")) {
      f.textbox(default: ".*test\\W+this\\W+please.*") 
    }
    f.entry(field: "skipBuildPhrase", title: _("Skip build phrase")) {
      f.textbox(default: ".*\\[skip\\W+ci\\].*") 
    }
    f.entry(field: "cron", title: _("Crontab line"), help: "/descriptor/hudson.triggers.TimerTrigger/help/spec") {
      f.textbox(default: "H/5 * * * *", checkUrl: "'descriptorByName/hudson.triggers.TimerTrigger/checkSpec?value=' + encodeURIComponent(this.value)") 
    }
  }
  f.entry(title: _("Application Setup")) {
    f.hetero_list(items: descriptor.extensions, name: "extensions", oneEach: "true", hasHeader: "true", descriptors: descriptor.getGlobalExtensionDescriptors()) 
  }
}
