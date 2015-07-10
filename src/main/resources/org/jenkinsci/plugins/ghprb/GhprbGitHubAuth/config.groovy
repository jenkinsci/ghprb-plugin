f = namespace(lib.FormTagLib)
c = namespace(lib.CredentialsTagLib)

f.entry(title:_("GitHub Server API URL"), field:"serverAPIUrl") {
    f.textbox()
}

f.entry(title:_("Shared secret"), field:"secret") {
    f.password()
}

f.entry(title:_("Credentials"), field:"credentialsId") {
    c.select(onchange="""{
            var self = this.targetElement ? this.targetElement : this;
            var r = findPreviousFormItem(self,'serverAPIUrl');
            r.onchange(r);
            self = null;
            r = null;
    }""" /* workaround for JENKINS-19124 */)
}

f.advanced(title:_("Test Credentials")) {
    f.entry(title:_("Test Credentials")) {
        f.validateButton(title:_("Connect to API"), progress:_("Connecting..."), with:"serverAPIUrl,credentialsId", method:"testGithubAccess")
        f.entry(title:_("Repository owner/name"), field:"repo") {
            f.textbox()
        }
        f.validateButton(title:_("Check repo permissions"), progress:_("Connecting..."), with:"serverAPIUrl,credentialsId,repo", method:"checkRepoAccess")
    }
}
    
f.advanced(title:_("Create API Token")) {
    f.entry(title:_("Create API Token")) {
        f.entry(title:_("Username temp"), field:"username") {
            f.textbox()
        }
        f.entry(title:_("Password temp"), field:"password") {
            f.password()
        }
        f.validateButton(title:_("Create Token"), progress:_("Creating..."), with:"serverAPIUrl,credentialsId,username,password", method:"createApiToken")
    }
}

f.entry(field: "description", title: _("Description")) {
  f.textbox()
}

f.advanced(title:_("Auth ID")) {
  f.entry(field: instance != null ? null : 'id', title: _("ID")) {
    f.textbox(name: "_.id", value: instance != null ? instance.id : null, readonly: instance != null ? 'readonly' : null)
  }
}

f.entry {
    div(align:"right") {
        input (type:"button", value:_("Delete Server"), class:"repeatable-delete")
    }
}
