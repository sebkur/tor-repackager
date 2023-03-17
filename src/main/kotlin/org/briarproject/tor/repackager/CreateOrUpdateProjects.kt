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
import kotlin.io.path.absolute
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
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
    val torBrowserVersion = "12.0.3"

    // Map Tor Browser version to Tor versions
    val browserToVersions = mapOf(
        // TODO: make sure these versions are accurate
        "12.0.3" to Versions("0.4.7.13", "0.0.14", "2.5.1")
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
fun createFromTemplate(project: Project, template: Path, os: String) {
    project.dir.createDirectories()
    for (path in template.walk()) {
        val relative = template.relativize(path)
        if (relative.getName(0) == Paths.get("build")) continue
        if (relative.getName(0) == Paths.get(".gradle")) continue
        val target = project.dir.resolve(relative)
        target.parent.createDirectories()
        path.copyTo(target, StandardCopyOption.REPLACE_EXISTING)
    }
    updateGradleProperties(project) { replace("template", "${project.name}-$os") }
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
