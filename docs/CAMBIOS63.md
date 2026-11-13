# Cambios 63 — validación de mensajes de chat

- Extrae la normalización y validación del contenido del chat a una política Kotlin pura, separada del acceso a Firebase.
- Recorta espacios, limita el texto a 2.000 caracteres y las URLs a 2.048.
- Exige al menos texto o un adjunto y no permite enviar foto y vídeo juntos.
- Rechaza enlaces multimedia que no sean HTTPS antes de intentar escribir en Firestore.
- Añade pruebas unitarias de mensajes normales, adjuntos, límites y entradas inválidas.

Las reglas de Firestore siguen siendo la autoridad final: la validación local mejora los errores que recibe el usuario, pero no sustituye la seguridad del servidor.
