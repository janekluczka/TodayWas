package pl.luczka.todaywas.ui.auth

fun isValidEmail(email: String): Boolean = email.isNotBlank() && email.contains("@")

fun isValidPassword(password: String): Boolean = password.length >= 6
