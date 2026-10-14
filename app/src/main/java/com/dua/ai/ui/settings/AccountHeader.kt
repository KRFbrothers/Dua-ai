package com.dua.ai.ui.settings

import androidx.compose.runtime.Immutable

/** Name / email / avatar initial for a settings header, from the signed-in account (same source as [SettingsScreen]). */
@Immutable
data class AccountHeader(val name: String, val email: String, val initial: String?, val signedIn: Boolean)

const val NOT_SIGNED_IN = "Not signed in"

fun accountHeader(displayName: String?, email: String?): AccountHeader {
    val n = displayName.orEmpty().trim()
    val e = email?.trim()?.takeIf { it.isNotEmpty() }
    val initial = (n.firstOrNull { it.isLetterOrDigit() } ?: e?.firstOrNull { it.isLetterOrDigit() })?.uppercaseChar()?.toString()
    val name = n.ifBlank { if (e == null) NOT_SIGNED_IN else e.substringBefore('@') }
    return AccountHeader(name, e ?: NOT_SIGNED_IN, initial, e != null)
}
