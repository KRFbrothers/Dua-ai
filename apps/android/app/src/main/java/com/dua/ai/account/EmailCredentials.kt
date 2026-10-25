package com.dua.ai.account

object EmailCredentials {
    fun validEmail(email: String): Boolean = email.trim().let { it.length in 1..254 && it.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) }
    fun validPassword(password: String, creating: Boolean): Boolean = password.isNotBlank() && password.length in (if (creating) 8 else 1)..128
}
