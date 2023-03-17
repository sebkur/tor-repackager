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
    val altName: String, // This name is used when resolving bundle names.
) {
    X64("x86_64", "x86_64"),
    X86("x86", "x86"),
    I686("i686", "i686"),
    Arm64("arm64", "aarch64"),
    Armv7("armv7", "armv7"),
}

internal fun arch(arch: String?): Arch {
    for (candidate in Arch.values()) {
        if (candidate.id == arch) {
            return candidate
        }
    }
    error("Invalid architecture '$arch'")
}

data class Target(val os: OS, val arch: Arch) : Serializable {
    val id: String
        get() = "${os.id}-${arch.id}"
    val qualifier: String
        get() = "${os.id}-${arch.altName}"
    val name: String
        get() = "${os.id.uppercaseFirstChar()}${arch.id.uppercaseFirstChar()}"
}
