package org.briarproject.tor.repackager

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.net.URL
import java.util.zip.GZIPInputStream

fun main() {
    println("Tor Repackager 0.1.0")

    // Define target architecture
    val target = Target(OS.MacOS, Arch.Arm64)

    // Define current Tor Browser version
    val torBrowserVersion = "12.0.3"

    // Map Tor Browser version to Tor versions
    val browserToTor = mapOf(
        "12.0.3" to "0.4.7.13"
    )

    val torVersion = browserToTor[torBrowserVersion]
    val url =
        "https://www.torproject.org/dist/torbrowser/$torBrowserVersion/tor-expert-bundle-$torBrowserVersion-${target.qualifier}.tar.gz"
    println(url)

    URL(url).openStream().use {
        GZIPInputStream(it).use {
            TarArchiveInputStream(it).use {
                while(true) {
                    val entry = it.nextTarEntry ?: break
                    if (!entry.isDirectory) {
                        println(entry.name)
                    }
                }
            }
        }
    }
}
