package com.dua.ai.account

/** A profile contact number, never proof of phone ownership or an authentication claim. Empty removes it. */
object PhoneNumbers {
    fun normalize(value: String): String? {
        if (value.length > 64 || !value.matches(Regex("[+0-9(). -]*"))) return null
        val normalized = value.replace(Regex("[(). -]"), "")
        return normalized.takeIf { it.isEmpty() || it.matches(Regex("\\+[1-9][0-9]{6,14}")) }
    }
}
