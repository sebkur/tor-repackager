package org.briarproject.tor.repackager

import java.nio.file.Paths

fun main() {
    println("Tor Repackager 0.1.0")
    println("Packaging artifacts from Tor Browser")

    // Define current Tor Browser version
    val torBrowserVersion = "12.0.4"

    val browserVersions = Versions(torBrowserVersion, torBrowserVersion, torBrowserVersion)

    val propProjectDir = System.getProperty("projectdir")
    val torRepackager =
        if (propProjectDir != null) Paths.get(propProjectDir)
        else Paths.get("").toAbsolutePath()

    val projectTemplate = torRepackager.resolve("template")

    val projectsMacOsBrowser = projects(torRepackager.resolve("projects/macos-tor-browser"))
    createUpdateTemplateMacFromTorBrowser(projectsMacOsBrowser, projectTemplate, torBrowserVersion, browserVersions)

    runGradlePublish(projectsMacOsBrowser)
}
