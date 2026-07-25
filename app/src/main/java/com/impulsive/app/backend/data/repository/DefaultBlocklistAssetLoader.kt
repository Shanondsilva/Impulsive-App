package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.domain.model.protection.DefaultBlockedDomains
import com.impulsive.app.backend.domain.model.protection.DefaultBlocklistAsset
import com.impulsive.app.backend.domain.model.protection.parseDefaultBlocklistAsset

internal class DefaultBlocklistAssetLoader(context: Context) {
    private val assetManager = context.applicationContext.assets

    fun load(): DefaultBlocklistAsset {
        val text = assetManager.open(DefaultBlockedDomains.AssetPath)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        return parseDefaultBlocklistAsset(text)
    }
}
