package org.briarproject.tor.repackager

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.InputStream
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.util.zip.GZIPInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.walk

data class Versions(val tor: String, val obfs4proxy: String, val snowflake: String)

fun main() {
    println("Tor Repackager 0.1.0")

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
    val torBrowserVersion = "12.0.3"

    // Map Tor Browser version to Tor versions
    val browserToVersions = mapOf(
        "12.0.3" to Versions("0.4.7.13", "0.0.14", "2.5.1")
    )

    val propProjectDir = System.getProperty("projectdir")
    val torRepackager =
        if (propProjectDir != null) Paths.get(propProjectDir)
        else Paths.get("").toAbsolutePath()

    val projectTemplate = torRepackager.resolve("projects/template")
    // TODO: update version numbers in gradle.properties
    // TODO: run ./gradlew clean publish on each project

    val versions = browserToVersions[torBrowserVersion]

    val dirLinux = torRepackager.resolve("projects/linux")
    createTemplate(linuxTargets, dirLinux, projectTemplate, torBrowserVersion)

    val dirMacOs = torRepackager.resolve("projects/macos")
    createTemplate(macTargets, dirMacOs, projectTemplate, torBrowserVersion)

    val dirWindows = torRepackager.resolve("projects/windows")
    createTemplate(windowsTargets, dirWindows, projectTemplate, torBrowserVersion)

    val dirAndroid = torRepackager.resolve("projects/android")
    createTemplate(androidTargets, dirAndroid, projectTemplate, torBrowserVersion)
}

fun createTemplate(targets: List<Target>, dirOs: Path, projectTemplate: Path, torBrowserVersion: String) {
    val projectTor = dirOs.resolve("tor")
    val projectObfs4proxy = dirOs.resolve("obfs4proxy")
    val projectSnowflake = dirOs.resolve("snowflake")

    val projects = listOf(projectTor, projectObfs4proxy, projectSnowflake)
    for (project in projects) {
        createFromTemplate(project, projectTemplate)
    }
    for (target in targets) {
        val url =
            "https://www.torproject.org/dist/torbrowser/$torBrowserVersion/tor-expert-bundle-$torBrowserVersion-${target.torQualifier}.tar.gz"
        println(url)

        URL(url).openStream().use { urlInput ->
            GZIPInputStream(urlInput).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        if (!entry.isDirectory) {
                            val extension = if (target.os == OS.Windows) ".exe" else ""
                            when (entry.name.removeSuffix(extension)) {
                                "tor/tor", "tor/libevent-2.1.7.dylib" -> {
                                    copyToProject(entry, tar, target, projectTor)
                                }
                                "tor/pluggable_transports/obfs4proxy" -> {
                                    copyToProject(entry, tar, target, projectObfs4proxy)
                                }
                                "tor/pluggable_transports/snowflake-client" -> {
                                    copyToProject(entry, tar, target, projectSnowflake, "snowflake$extension")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPathApi::class)
fun createFromTemplate(project: Path, template: Path) {
    project.createDirectories()
    for (path in template.walk()) {
        val relative = template.relativize(path)
        if (relative.getName(0) == Paths.get("build")) continue
        if (relative.getName(0) == Paths.get(".gradle")) continue
        val target = project.resolve(relative)
        target.parent.createDirectories()
        path.copyTo(target, StandardCopyOption.REPLACE_EXISTING)
    }
}

fun copyToProject(entry: TarArchiveEntry, tar: InputStream, target: Target, project: Path, name: String? = null) {
    val filename = name ?: Paths.get(entry.name).fileName.toString()
    val resources = project.resolve("src/main/resources")
    val file = resources.resolve("${target.jarName}/$filename")
    file.parent.createDirectories()
    println("${entry.name} → ${target.jarName}/$filename")
    file.outputStream(CREATE, TRUNCATE_EXISTING).use { output ->
        tar.copyTo(output)
    }
}
