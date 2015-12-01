f = namespace(lib.FormTagLib)
c = namespace(lib.CredentialsTagLib)

f.entry(title:_("GitHub Server API URL"), field:"serverAPIUrl") {
    f.textbox()
}

f.entry(title:_("Jenkins URL override"), field:"jenkinsUrl") {
    f.textbox()
}

f.entry(title:_("Shared secret"), field:"secret") {
    f.password()
}

f.entry(title:_("Credentials"), field:"credentialsId") {
    c.select(onchange="""{
            var self = this.targetElement ? this.targetElement : this;
            var r = findPreviousFormItem(self,'serverAPIUrl','credentialsId');
            r.onchange(r);
            self = null;
            r = null;
    }""" /* workaround for JENKINS-19124 */)
}

f.advanced(title:_("Test Credentials")) {
    f.optionalBlock(title:_("Test basic connection to GitHub")) {
        f.entry() {
            f.validateButton(title:_("Connect to API"), progress:_("Connecting..."), with:"serverAPIUrl,credentialsId", method:"testGithubAccess")
        }
    }
    
    f.entry(title:_("Repository owner/name"), field:"repo") {
        f.textbox()
    }
    f.optionalBlock(title:_("Test Permissions to a Repository")) {
        f.entry() {
            f.validateButton(title:_("Check repo permissions"), progress:_("Checking..."), with:"serverAPIUrl,credentialsId,repo", method:"checkRepoAccess")
        }
    }
    f.optionalBlock(title:_("Test adding comment to Pull Request")) {
        f.entry(title:_("Issue ID"), field:"issueId") {
            f.number()
        }
        f.entry(title:_("Comment to post"), field:"message1") {
            f.textbox()
        }
        f.validateButton(title:_("Comment to issue"), progress:_("Commenting..."), with:"serverAPIUrl,credentialsId,repo,issueId,message1", method:"testComment")
    }
    f.optionalBlock(title:_("Test updating commit status")) {
        f.entry(title:_("Commit SHA"), field:"sha1") {
            f.textbox()
        }
        f.entry(title:_("Commit State"), field:"state") {
            f.select()
        }
        f.entry(title:_("Status url"), field:"url") {
            f.textbox()
        }
        f.entry(title:_("Message to post"), field:"message2") {
            f.textbox()
        }
        f.entry(title:_("Context for the status"), field:"context") {
            f.textbox()
        }
        f.validateButton(title:_("Update status"), progress:_("Updating..."), with:"serverAPIUrl,credentialsId,repo,sha1,state,url,message2,context", method:"testUpdateStatus")
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
