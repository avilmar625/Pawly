package com.pawly.app

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException

/** Converts common Firebase failures into concise, actionable Spanish messages. */
internal fun Throwable.toPawlyMessage(fallback: String): String {
    val authCode = (this as? FirebaseAuthException)?.errorCode.orEmpty()
    val firestoreCode = (this as? FirebaseFirestoreException)?.code
    if (firestoreCode == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        return "Firebase ha rechazado la operación por las reglas de Firestore. Publica las reglas del archivo firestore.rules de la rama cambios55 y vuelve a intentarlo."
    }
    if (firestoreCode == FirebaseFirestoreException.Code.UNAVAILABLE || this is FirebaseNetworkException) {
        return "No hay conexión con Firebase. Comprueba Internet e inténtalo de nuevo."
    }
    return when (authCode) {
        "ERROR_INVALID_EMAIL" -> "El correo electrónico no tiene un formato válido."
        "ERROR_USER_NOT_FOUND", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" ->
            "El correo o la contraseña no son correctos."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Ya existe una cuenta con ese correo. Prueba a iniciar sesión."
        "ERROR_WEAK_PASSWORD" -> "La contraseña es demasiado sencilla. Utiliza al menos 6 caracteres."
        "ERROR_USER_DISABLED" -> "Esta cuenta está desactivada. Contacta con el equipo de Pawly."
        "ERROR_TOO_MANY_REQUESTS" -> "Se han realizado demasiados intentos. Espera un poco y vuelve a probar."
        "ERROR_OPERATION_NOT_ALLOWED" -> "Este método de acceso no está activado en Firebase Authentication."
        else -> {
            localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
        }
    }
}
