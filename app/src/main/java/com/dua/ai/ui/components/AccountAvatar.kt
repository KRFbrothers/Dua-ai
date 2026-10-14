package com.dua.ai.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.dua.ai.ui.history.rememberProfilePhoto
import com.dua.ai.ui.theme.SettingsAvatarStyle
import com.dua.ai.ui.theme.SettingsShapes
import com.dua.ai.ui.theme.SettingsTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import com.dua.ai.ui.theme.SettingsSizes

/** The actual account photo uses the drawer's bounded, sampled cache; no invented profile image. */
@Composable
internal fun AccountAvatar(email: String?, displayName: String, avatarUrl: String, size: Dp,
    modifier: Modifier = Modifier, photo: ImageBitmap? = rememberProfilePhoto(if (displayName.isBlank() && email.isNullOrBlank()) "" else avatarUrl)) {
    val colors = SettingsTheme.colors
    Box(modifier.size(size).clip(SettingsShapes.circle).background(colors.avatar).testTag("account-avatar"),
        contentAlignment = Alignment.Center) {
        val initial = (displayName.ifBlank { email.orEmpty() }).firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
        if (displayName.isBlank() && email.isNullOrBlank()) Icon(ReferenceIcons.Account, "Anonymous profile",
            modifier = Modifier.size(SettingsSizes.rowIcon), tint = colors.onAvatar)
        else if (photo != null) Image(photo, contentDescription = "Account photo", modifier = Modifier.size(size), contentScale = ContentScale.Crop)
        else if (initial != null) Text(initial, style = SettingsAvatarStyle, color = colors.onAvatar)
        else Icon(ReferenceIcons.Account, "Profile", modifier = Modifier.size(SettingsSizes.rowIcon), tint = colors.onAvatar)
    }
}
