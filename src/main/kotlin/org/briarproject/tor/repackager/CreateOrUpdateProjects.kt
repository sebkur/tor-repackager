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

data class Versions(val tor: String, val obfs4proxy: String, val snowflake: String)
data class Project(val name: String, val dir: Path)
data class OsProjects(val projectTor: Project, val projectObfs4proxy: Project, val projectSnowflake: Project) {
    val list get() = listOf(projectTor, projectObfs4proxy, projectSnowflake)
}

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
    val torBrowserVersion = "12.0.4"

    // Map Tor Browser version to Tor versions
    val browserToVersions = mapOf(
        // TODO: make sure these versions are accurate
        "12.0.3" to Versions("0.4.7.13", "0.0.14", "2.5.1"),
        "12.0.4" to Versions("0.4.7.13", "0.0.14", "2.5.1"),
    )

    val browserVersions = Versions(torBrowserVersion, torBrowserVersion, torBrowserVersion)

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

    val projectsMacOs2 = projects(torRepackager.resolve("projects/macos2"))
    createUpdateTemplateMacFromTorBrowser(projectsMacOs2, projectTemplate, torBrowserVersion, browserVersions)

    runGradlePublish(projectsLinux)
    runGradlePublish(projectsMacOs)
    runGradlePublish(projectsWindows)
    runGradlePublish(projectsAndroid)
    runGradlePublish(projectsMacOs2)
}

fun runGradlePublish(projects: OsProjects) {
    for (project in projects.list) {
        println("Publishing ${project.dir}")
        ProcessBuilder("./gradlew", "clean", "publish").directory(project.dir.toFile()).start().waitFor()
    }
}

fun projects(dirOs: Path): OsProjects {
    val projectTor = Project("tor", dirOs.resolve("tor"))
    val projectObfs4proxy = Project("obfs4proxy", dirOs.resolve("obfs4proxy"))
    val projectSnowflake = Project("snowflake", dirOs.resolve("snowflake"))
    return OsProjects(projectTor, projectObfs4proxy, projectSnowflake)
}

fun createUpdateTemplate(
    targets: List<Target>,
    projects: OsProjects,
    projectTemplate: Path,
    torBrowserVersion: String,
    versions: Versions
) {
    println("Working on: " + targets.joinToString(", ") { it.id })
    val os = targets[0].os.id

    for (project in projects.list) {
        createFromTemplate(project, projectTemplate, os)
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
                                    copyToProject(entry, tar, target, projects.projectTor)
                                }

                                "tor/pluggable_transports/obfs4proxy" -> {
                                    copyToProject(entry, tar, target, projects.projectObfs4proxy)
                                }

                                "tor/pluggable_transports/snowflake-client" -> {
                                    copyToProject(entry, tar, target, projects.projectSnowflake, "snowflake$extension")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    updateGradleProperties(projects, versions)
}

fun createUpdateTemplateMacFromTorBrowser(
    projects: OsProjects,
    projectTemplate: Path,
    torBrowserVersion: String,
    versions: Versions
) {
    println("Working on: macos-any")
    val target = Target(OS.MacOS, Arch.Any)
    val os = target.os.id

    for (project in projects.list) {
        createFromTemplate(project, projectTemplate, os, "${project.name}-$os-torbrowser")
    }
    val url =
        "https://www.torproject.org/dist/torbrowser/$torBrowserVersion/TorBrowser-$torBrowserVersion-macos_ALL.dmg"
    println(url)

    // Download DMG to temporary directory and extract it there
    val dir = Files.createTempDirectory("tor")
    Runtime.getRuntime().addShutdownHook(Thread {
        dir.toFile().deleteRecursively()
    })

    // File to download DMG to
    val fileDmg = dir.resolve("tor-browser.dmg")
    // Directory to extract DMG to
    val dirDmg = dir.resolve("dmg")

    // Download DMG
    URL(url).openStream().use { urlInput ->
        fileDmg.outputStream().use { fos ->
            urlInput.copyTo(fos)
        }
    }

    // Extract DMG using 7z
    dirDmg.createDirectories()
    ProcessBuilder("7z", "x", fileDmg.absolutePathString()).directory(dirDmg.toFile()).start().waitFor()

    // Extract binaries
    val dirTor = dirDmg.resolve("Tor Browser/Tor Browser.app/Contents/MacOS/Tor")
    val dirPluggable = dirTor.resolve("PluggableTransports")

    copyToProject(dirTor.resolve("tor.real"), target, projects.projectTor, "tor")
    copyToProject(dirTor.resolve("libevent-2.1.7.dylib"), target, projects.projectTor)
    copyToProject(dirPluggable.resolve("obfs4proxy"), target, projects.projectObfs4proxy)
    copyToProject(dirPluggable.resolve("snowflake-client"), target, projects.projectSnowflake, "snowflake")

    updateGradleProperties(projects, versions)
}

fun updateGradleProperties(projects: OsProjects, versions: Versions) {
    updateGradleProperties(projects.projectTor) {
        replace(
            "pVersion=.*".toRegex(),
            "pVersion=${versions.tor}"
        )
    }
    updateGradleProperties(projects.projectObfs4proxy) {
        replace(
            "pVersion=.*".toRegex(),
            "pVersion=${versions.obfs4proxy}"
        )
    }
    updateGradleProperties(projects.projectSnowflake) {
        replace(
            "pVersion=.*".toRegex(),
            "pVersion=${versions.snowflake}"
        )
    }
}

@OptIn(ExperimentalPathApi::class)
fun createFromTemplate(project: Project, template: Path, os: String, name: String? = null) {
    project.dir.createDirectories()
    for (path in template.walk()) {
        val relative = template.relativize(path)
        if (relative.getName(0) == Paths.get("build")) continue
        if (relative.getName(0) == Paths.get(".gradle")) continue
        val target = project.dir.resolve(relative)
        target.parent.createDirectories()
        path.copyTo(target, REPLACE_EXISTING)
    }
    val projectName = name ?: "${project.name}-$os"
    updateGradleProperties(project) { replace("template", projectName) }
}

fun updateGradleProperties(project: Project, transform: String.() -> String) {
    val gradleProperties = project.dir.resolve("gradle.properties")
    gradleProperties.writeText(transform(gradleProperties.readText()))
}

fun copyToProject(entry: TarArchiveEntry, tar: InputStream, target: Target, project: Project, name: String? = null) {
    val filename = name ?: Paths.get(entry.name).fileName.toString()
    val resources = project.dir.resolve("src/main/resources")
    val file = resources.resolve("${target.jarName}/$filename")
    file.parent.createDirectories()
    println("${entry.name} → ${target.jarName}/$filename")
    file.outputStream(CREATE, TRUNCATE_EXISTING).use { output ->
        tar.copyTo(output)
    }
}

fun copyToProject(input: Path, target: Target, project: Project, name: String? = null) {
    val filename = name ?: input.name
    val resources = project.dir.resolve("src/main/resources")
    val file = resources.resolve("${target.jarName}/$filename")
    file.parent.createDirectories()
    println("${file.name} → ${target.jarName}/$filename")
    input.copyTo(file, REPLACE_EXISTING)
}
