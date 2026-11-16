# Eliminación de cuentas Pawly — estado y requisitos

## Estado actual

La pantalla de perfil todavía registra una solicitud `pending`; **no elimina automáticamente la cuenta**. No debe mostrarse como un borrado completado ni considerarse suficiente para publicar Pawly en Google Play.

La app ya registra para las nuevas subidas de imágenes/vídeos el `public_id` y el tipo de recurso Cloudinary en `cloudinaryAssets`, asociados al UID. Esto facilita identificar los medios nuevos, pero todavía no elimina recursos. Los medios subidos antes de esta migración no tienen necesariamente un registro de propiedad.

## Qué falta para completar el borrado

El endpoint de eliminación debe ejecutarse desde un backend de confianza (Cloudflare Worker), validar el ID token reciente y realizar, de forma idempotente y auditable:

1. Eliminar recursos Cloudinary de imagen y vídeo que pertenezcan al UID, usando la Admin API desde el servidor. Nunca exponer la API Secret de Cloudinary en Android.
2. Eliminar datos propios y subcolecciones de Firestore: perfil, datos privados, mascotas, publicaciones y comentarios, diario/recuerdos sincronizados, eventos creados, propuestas de lugares, mensajes, conversaciones, seguidores/seguidos, bloqueos, notificaciones, tokens FCM, reservas de usuario y registros de avisos.
3. Eliminar o anonimizar referencias del UID en documentos compartidos cuando corresponda, sin borrar contenido de terceros por error.
4. Eliminar el usuario de Firebase Authentication solo cuando las fases anteriores se hayan completado o exista una política de reintentos segura.
5. Guardar un estado mínimo de auditoría sin conservar datos personales innecesarios y permitir al cliente consultar el resultado.
6. Proporcionar también una ruta web de solicitud/eliminación, accesible fuera de la app, para cumplir los requisitos de las tiendas.

## Credenciales necesarias en Cloudflare

Cuando se implemente el endpoint, los secretos requeridos serán:

- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`
- `FIREBASE_WEB_API_KEY` (para la API de eliminación de Firebase Authentication, si se utiliza el endpoint Identity Toolkit)
- Los secretos de cuenta de servicio de Firebase ya descritos en `backend/cloudflare-worker/README.md`.

Estos valores se configuran con Wrangler y no deben enviarse por chat ni guardarse en GitHub. La cuenta gratuita de Cloudflare Workers no sustituye las cuotas propias de Cloudinary/Firebase.

## No desplegar como funcionalidad completada

Hasta que exista el endpoint, las pruebas de integración y la verificación de la eliminación real de Auth, Firestore y Cloudinary, la UI debe seguir describiendo el proceso como una solicitud pendiente. No afirmar que la cuenta o sus fotografías se borran automáticamente.
