package com.impulsive.app.backend.data.local.preferences

import java.util.Locale

fun canonicalAccessKey(raw: String): String =
    raw.trim().trimEnd('.').lowercase(Locale.ROOT)
