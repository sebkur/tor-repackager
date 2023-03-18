package org.briarproject.tor.repackager

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.io.path.outputStream

class TorExecutableTest {

    @TempDir
    lateinit var dir: Path

    private val extension = if (currentTarget.os == OS.Windows) ".exe" else ""

    @Test
    fun test() {
        val torBrowserVersion = "12.0.3"
        extract(torBrowserVersion)

        val tor = "tor$extension"

        println("Starting Tor")
        dir.resolve(tor).toFile().setExecutable(true, true)
        val process = ProcessBuilder("./$tor").directory(dir.toFile()).start()
        println("waiting for three seconds to see if it exits")
        val result = process.waitFor(3, TimeUnit.SECONDS)
        println("done waiting")
        if (result) {
            // If the process exited, print its output
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            println(output)
            println(error)
        }
        // Assert that the process did not exit on its own
        assertFalse(result)
        // Find forked off process and kill it. `process.destroy()` does not do it.
        ProcessHandle.allProcesses().filter { p ->
            p.info().command().map { it.endsWith("/$tor") }.orElse(false)
        }.findFirst().ifPresent(ProcessHandle::destroy)
    }

    private fun extract(torBrowserVersion: String) {
        val target = currentTarget

        val url =
            "https://www.torproject.org/dist/torbrowser/$torBrowserVersion/tor-expert-bundle-$torBrowserVersion-${target.torQualifier}.tar.gz"
        println("Downloading expert bundle from: $url")

        URL(url).openStream().use { urlInput ->
            GZIPInputStream(urlInput).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        if (!entry.isDirectory) {
                            when (entry.name.removeSuffix(extension)) {
                                "tor/tor", "tor/libevent-2.1.7.dylib" -> {
                                    copy(entry, tar)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun copy(entry: TarArchiveEntry, tar: InputStream, name: String? = null) {
        val filename = name ?: Paths.get(entry.name).fileName.toString()
        val file = dir.resolve(filename)
        file.outputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
            tar.copyTo(output)
        }
    }

}
