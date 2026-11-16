package com.pawly.app

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException

/** Converts Firebase failures, including wrapped causes, into concise Spanish guidance. */
internal fun Throwable.toPawlyMessage(fallback: String): String {
    val causes = generateSequence(this) { it.cause }.take(8).toList()
    val authCode = causes.filterIsInstance<FirebaseAuthException>().firstOrNull()?.errorCode.orEmpty()
    val firestoreCode = causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()?.code
    val networkFailure = causes.any {
        it is FirebaseNetworkException ||
            (it is FirebaseFirestoreException && it.code == FirebaseFirestoreException.Code.UNAVAILABLE)
    }
    val authSessionEstablished = message?.contains("Firebase ha validado tu acceso", ignoreCase = true) == true ||
        message?.contains("Google ha validado tu acceso", ignoreCase = true) == true

    if (firestoreCode == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        if (authSessionEstablished) {
            return "Tus credenciales son correctas y Firebase ha iniciado sesión, pero Firestore no permite cargar el perfil. La sesión no se ha cerrado. Publica las reglas actualizadas de firestore.rules y vuelve a entrar."
        }
        val accountWasCreated = message?.contains("La cuenta de acceso ya se ha creado", ignoreCase = true) == true
        return if (accountWasCreated) {
            "Firestore ha rechazado el guardado del perfil. Tu cuenta de acceso ya está creada y no se ha borrado. Publica las reglas de firestore.rules de la rama cambios60-comunidad-calendario y después inicia sesión con el mismo correo y contraseña para completar tu perfil."
        } else {
            "Firebase ha rechazado la operación por las reglas de Firestore. Publica el archivo firestore.rules de la rama cambios60-comunidad-calendario en Firebase Console."
        }
    }
    if (networkFailure) {
        return if (authSessionEstablished) {
            "Has iniciado sesión, pero no se ha podido cargar tu perfil por un problema de conexión. La sesión sigue abierta; comprueba Internet y vuelve a intentarlo."
        } else {
            "No hay conexión con Firebase. Comprueba Internet e inténtalo de nuevo."
        }
    }
    return when (authCode) {
        "ERROR_INVALID_EMAIL" -> "El correo electrónico no tiene un formato válido."
        "ERROR_USER_NOT_FOUND", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL",
        "ERROR_INVALID_LOGIN_CREDENTIALS", "ERROR_INVALID_PASSWORD", "ERROR_EMAIL_NOT_FOUND" ->
            "El correo o la contraseña no son correctos. Comprueba los datos o restablece la contraseña."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Ya existe una cuenta con ese correo. Prueba a iniciar sesión."
        "ERROR_WEAK_PASSWORD" -> "La contraseña es demasiado sencilla. Utiliza al menos 6 caracteres."
        "ERROR_USER_DISABLED" -> "Esta cuenta está desactivada. Contacta con el equipo de Pawly."
        "ERROR_TOO_MANY_REQUESTS" -> "Se han realizado demasiados intentos. Espera un poco y vuelve a probar."
        "ERROR_OPERATION_NOT_ALLOWED" -> "Este método de acceso no está activado en Firebase Authentication."
        else -> localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
    }
}
