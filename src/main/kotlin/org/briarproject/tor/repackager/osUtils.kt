package org.briarproject.tor.repackager

import java.io.Serializable

enum class OS(val id: String) {
    Linux("linux"),
    Windows("windows"),
    MacOS("macos"),
    Android("android"),
}

enum class Arch(
    val id: String,
    val torName: String, // This name is used when resolving bundle names.
) {
    X64("x86_64", "x86_64"),
    X86("x86", "x86"),
    I686("i686", "i686"),
    Arm64("arm64", "aarch64"),
    Armv7("armv7", "armv7"),
    Any("any", "any"),
}

internal fun arch(arch: String?): Arch {
    for (candidate in Arch.values()) {
        if (candidate.id == arch) {
            return candidate
        }
    }
    error("Invalid architecture '$arch'")
}

data class Target(val os: OS, val arch: Arch, val archName: String? = null) : Serializable {
    val id: String
        get() = "${os.id}-${arch.id}"
    val torQualifier: String
        get() = "${os.id}-${arch.torName}"
    val jarName: String
        get() = archName ?: arch.torName
}

internal val currentTarget by lazy {
    Target(currentOS, currentArch)
}

internal val currentOS: OS by lazy {
    val os = System.getProperty("os.name")
    when {
        os.equals("Mac OS X", ignoreCase = true) -> OS.MacOS
        os.startsWith("Win", ignoreCase = true) -> OS.Windows
        os.startsWith("Linux", ignoreCase = true) -> OS.Linux
        else -> error("Unknown OS name: $os")
    }
}

internal val currentArch by lazy {
    when (val osArch = System.getProperty("os.arch")) {
        "x86_64", "amd64" -> Arch.X64
        "aarch64" -> Arch.Arm64
        else -> error("Unsupported OS arch: $osArch")
    }
}
