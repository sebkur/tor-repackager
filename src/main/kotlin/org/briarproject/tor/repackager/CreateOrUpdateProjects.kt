package org.briarproject.tor.repackager

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.net.URL
import java.nio.file.Paths
import java.util.zip.GZIPInputStream

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
        Target(OS.Android, Arch.Armv7),
        Target(OS.Android, Arch.Arm64),
    )

    // Define current Tor Browser version
    val torBrowserVersion = "12.0.3"

    // Map Tor Browser version to Tor versions
    val browserToTor = mapOf(
        "12.0.3" to "0.4.7.13"
    )

    val torVersion = browserToTor[torBrowserVersion]

    for (target in macTargets) {
        val url =
            "https://www.torproject.org/dist/torbrowser/$torBrowserVersion/tor-expert-bundle-$torBrowserVersion-${target.torQualifier}.tar.gz"
        println(url)

        URL(url).openStream().use {
            GZIPInputStream(it).use {
                TarArchiveInputStream(it).use {
                    while (true) {
                        val entry = it.nextTarEntry ?: break
                        if (!entry.isDirectory) {
                            if (entry.name == "tor/tor" || entry.name == "tor/libevent-2.1.7.dylib") {
                                val filename = Paths.get(entry.name).fileName.toString()
                                println("${entry.name} → ${target.arch.jarName}/$filename")
                            }
                        }
                    }
                }
            }
        }
    }
}
