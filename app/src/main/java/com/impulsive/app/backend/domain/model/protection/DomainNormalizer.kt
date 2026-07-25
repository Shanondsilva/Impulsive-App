package com.impulsive.app.backend.domain.model.protection

import java.net.IDN
import java.util.Locale

private val TrailingDnsSeparators = setOf(
    '.',
    '\u3002',
    '\uFF0E',
    '\uFF61',
)

fun normalizeDomainOrNull(value: String): String? {
    val trimmed = value.trim()

    if (trimmed.isEmpty()) {
        return null
    }

    var candidate = trimmed

    if (candidate.last() in TrailingDnsSeparators) {
        candidate = candidate.dropLast(1)
    }

    if (candidate.isEmpty() || candidate.last() in TrailingDnsSeparators) {
        return null
    }

    if (
        candidate.contains("://") ||
        candidate.contains('/') ||
        candidate.contains('\\') ||
        candidate.contains('@') ||
        candidate.contains(':') ||
        candidate.contains('*') ||
        candidate.contains('?') ||
        candidate.contains('#')
    ) {
        return null
    }

    val ascii = runCatching {
        IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES)
    }.getOrNull()?.lowercase(Locale.ROOT) ?: return null

    if (ascii.isEmpty() || ascii.length > 253) {
        return null
    }

    val labels = ascii.split('.')

    if (labels.size < 2) {
        return null
    }

    if (labels.any { it.isEmpty() || it.length > 63 }) {
        return null
    }

    if (
        labels.size == 4 &&
        labels.all { label -> label.toIntOrNull()?.let { it in 0..255 } == true }
    ) {
        return null
    }

    return ascii
}
