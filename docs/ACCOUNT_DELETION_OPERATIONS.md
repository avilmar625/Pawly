# Solicitudes de eliminación de cuenta

## Estado en cambios15

La pantalla de perfil permite enviar una solicitud. La aplicación crea un documento en:

`accountDeletionRequests/{requestId}`

Campos: UID, correo, fecha del servidor, estado `pending` y origen `android`. Las reglas permiten al usuario crear y consultar sus propias solicitudes; solo un administrador puede modificarlas.

**Esto registra una solicitud; no elimina automáticamente la cuenta ni los datos.** No se debe describir como eliminación completada.

## Procedimiento operativo provisional

Hasta disponer de un backend automatizado:
1. Revisar periódicamente las solicitudes pendientes desde un entorno administrativo seguro.
2. Verificar la identidad del solicitante mediante el canal de cuenta, sin pedir contraseñas.
3. Identificar todos los datos asociados al UID en Firebase y Cloudinary.
4. Ejecutar el proceso de eliminación completo y documentar excepciones legales justificadas.
5. Confirmar al solicitante cuando se haya completado.
6. Marcar la solicitud como completada desde una cuenta administrativa.
7. Conservar solo la evidencia mínima necesaria durante el plazo definido en la política de privacidad.

No hacer esta operación desde una app cliente ni distribuir credenciales Firebase Admin.

## Bloqueante para producción

Google Play exige una vía de solicitud de eliminación dentro de la aplicación y un recurso web externo. Además, la eliminación debe alcanzar los datos asociados, no limitarse a desactivar la cuenta.

La implementación actual no cubre aún:
- Borrado recursivo de subcolecciones de usuarios.
- Publicaciones y comentarios del usuario.
- Mensajes y conversaciones.
- Tokens FCM.
- Reserva de nombre de usuario.
- Imágenes alojadas en Cloudinary.
- Datos locales guardados en dispositivos donde se haya iniciado sesión.

Antes de publicar, implementar un endpoint de confianza (por ejemplo, Cloud Run o un servicio equivalente) que autentique la solicitud, realice la cascada de borrado con credenciales de servidor y sea invocado también por el recurso web externo. Definir además cómo tratar mensajes compartidos con otras personas y obligaciones de conservación. Probar el proceso en un proyecto Firebase de pruebas.
