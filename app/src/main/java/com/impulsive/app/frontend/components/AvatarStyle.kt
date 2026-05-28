package com.impulsive.app.frontend.components

import androidx.compose.ui.graphics.Color
import com.impulsive.app.R

enum class AvatarStyle(
    val id: String,
    val drawableResId: Int,
    val contentDescription: String,
    val backgroundColor: Color,
) {
    Avatar01("avatar_01", R.drawable.avatar_01, "Avatar 1", Color(0xFFF5EEF8)),
    Avatar02("avatar_02", R.drawable.avatar_02, "Avatar 2", Color(0xFFEFF3FA)),
    Avatar03("avatar_03", R.drawable.avatar_03, "Avatar 3", Color(0xFFF8F0EA)),
    Avatar04("avatar_04", R.drawable.avatar_04, "Avatar 4", Color(0xFFF0F5EF)),
    Avatar05("avatar_05", R.drawable.avatar_05, "Avatar 5", Color(0xFFF8F2F6)),
    Avatar06("avatar_06", R.drawable.avatar_06, "Avatar 6", Color(0xFFF1EEF9));

    companion object {
        fun fromId(id: String): AvatarStyle = entries.firstOrNull { it.id == id } ?: Avatar01
    }
}
