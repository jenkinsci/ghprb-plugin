j = namespace("jelly:core")
f = namespace("/lib/form")

f.entry(field: "commentFilePath", title: _("Comment file path")) {
  f.textbox()
}
f.entry(field: "commentFileOnSuccess", title: _("Comment file on Success")) {
  f.checkbox(default: true)
}
f.entry(field: "commentFileOnFailure", title: _("Comment file on Failure")) {
  f.checkbox(default: true)
}
