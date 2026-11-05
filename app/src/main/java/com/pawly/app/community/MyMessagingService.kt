package com.pawly.app.community

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyMessagingService : FirebaseMessagingService() {

    // Se llama cuando el dispositivo recibe un token nuevo o por primera vez
    override fun onNewToken(token: String) {
        Log.d("FCM", "Nuevo token: $token")
        // TODO: Guarda este token en Firestore dentro del perfil de tu usuario
    }

    // Se llama cuando llega un mensaje y la app está en primer plano (foreground)
    override fun onMessageReceived(message: RemoteMessage) {
        message.notification?.let {
            Log.d("FCM", "Título: ${it.title} - Cuerpo: ${it.body}")
            // Aquí puedes construir y mostrar una notificación local en Android
        }
    }
}
