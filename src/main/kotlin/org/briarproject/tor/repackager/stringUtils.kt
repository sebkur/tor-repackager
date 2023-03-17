package org.briarproject.tor.repackager

internal fun String.uppercaseFirstChar(): String =
    transformFirstCharIfNeeded(
        shouldTransform = { it.isLowerCase() },
        transform = { it.uppercaseChar() }
    )

internal fun String.lowercaseFirstChar(): String =
    transformFirstCharIfNeeded(
        shouldTransform = { it.isUpperCase() },
        transform = { it.lowercaseChar() }
    )

private inline fun String.transformFirstCharIfNeeded(
    shouldTransform: (Char) -> Boolean,
    transform: (Char) -> Char
): String {
    if (isNotEmpty()) {
        val firstChar = this[0]
        if (shouldTransform(firstChar)) {
            val sb = java.lang.StringBuilder(length)
            sb.append(transform(firstChar))
            sb.append(this, 1, length)
            return sb.toString()
        }
    }
    return this
}
