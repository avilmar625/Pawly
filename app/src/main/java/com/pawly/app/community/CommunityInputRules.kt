package com.pawly.app

/**
 * Central validation rules for user-generated community content.
 * Kept independent from Firebase so the rules can be unit-tested.
 */
internal object CommunityInputRules {
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
    private val usernamePattern = Regex("^[A-Za-z0-9_.]{3,24}$")

    fun validateEmail(value: String): String {
        val email = value.trim().lowercase()
        require(email.length <= 254 && emailPattern.matches(email)) {
            "Introduce un correo electrónico válido."
        }
        return email
    }

    fun validatePassword(value: String) {
        require(value.length >= 6) { "La contraseña debe tener al menos 6 caracteres." }
        require(value.length <= 128) { "La contraseña es demasiado larga." }
    }

    fun validateUsername(value: String): String {
        val username = value.trim()
        require(usernamePattern.matches(username)) {
            "El nombre de usuario debe tener entre 3 y 24 caracteres y usar solo letras, números, puntos o guiones bajos."
        }
        return username
    }

    fun validatePost(text: String, imageUrl: String) {
        require(text.isNotBlank() || imageUrl.isNotBlank()) {
            "Añade un texto o una fotografía."
        }
        require(text.length <= 2000) { "La publicación no puede superar los 2.000 caracteres." }
        require(imageUrl.isBlank() || imageUrl.startsWith("https://")) {
            "La dirección de la fotografía no es segura."
        }
    }

    fun validateComment(value: String): String {
        val text = value.trim()
        require(text.isNotBlank()) { "Escribe un comentario." }
        require(text.length <= 1000) { "El comentario no puede superar los 1.000 caracteres." }
        return text
    }

    fun validateMessage(value: String): String {
        val text = value.trim()
        require(text.isNotBlank()) { "Escribe un mensaje." }
        require(text.length <= 2000) { "El mensaje no puede superar los 2.000 caracteres." }
        return text
    }
}
