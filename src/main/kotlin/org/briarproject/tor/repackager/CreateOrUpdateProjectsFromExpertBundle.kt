package org.briarproject.tor.repackager

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.util.zip.GZIPInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText

fun main() {
    println("Tor Repackager 0.1.0")
    println("Packaging artifacts from Tor expert bundle")

    // Define target architectures available for download
    val macTargets = listOf(
        Target(OS.MacOS, Arch.X64),
        Target(OS.MacOS, Arch.Arm64)
    )
    val linuxTargets = listOf(
        Target(OS.Linux, Arch.I686),
        Target(OS.Linux, Arch.X64)
    )
    val windowsTargets = listOf(
        Target(OS.Windows, Arch.I686),
        Target(OS.Windows, Arch.X64)
    )
    val androidTargets = listOf(
        Target(OS.Android, Arch.X86),
        Target(OS.Android, Arch.X64),
        Target(OS.Android, Arch.Armv7, "armeabi-v7a"),
        Target(OS.Android, Arch.Arm64, "arm64-v8a"),
    )

    // Define current Tor Browser version
    val torBrowserVersion = "12.0.4"

    // Map Tor Browser version to Tor versions
    val browserToVersions = mapOf(
        // TODO: make sure these versions are accurate
        "12.0.3" to Versions("0.4.7.13", "0.0.14", "2.5.1"),
        "12.0.4" to Versions("0.4.7.13", "0.0.14", "2.5.1"),
    )

    val propProjectDir = System.getProperty("projectdir")
    val torRepackager =
        if (propProjectDir != null) Paths.get(propProjectDir)
        else Paths.get("").toAbsolutePath()

    val projectTemplate = torRepackager.resolve("template")
    val versions = requireNotNull(browserToVersions[torBrowserVersion])

    val projectsLinux = projects(torRepackager.resolve("projects/linux"))
    createUpdateTemplate(linuxTargets, projectsLinux, projectTemplate, torBrowserVersion, versions)

    val projectsMacOs = projects(torRepackager.resolve("projects/macos"))
    createUpdateTemplate(macTargets, projectsMacOs, projectTemplate, torBrowserVersion, versions)

    val projectsWindows = projects(torRepackager.resolve("projects/windows"))
    createUpdateTemplate(windowsTargets, projectsWindows, projectTemplate, torBrowserVersion, versions)

    val projectsAndroid = projects(torRepackager.resolve("projects/android"))
    createUpdateTemplate(androidTargets, projectsAndroid, projectTemplate, torBrowserVersion, versions)

    runGradlePublish(projectsLinux)
    runGradlePublish(projectsMacOs)
    runGradlePublish(projectsWindows)
    runGradlePublish(projectsAndroid)
}
