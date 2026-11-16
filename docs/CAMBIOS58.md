# Cambios 58 — Solicitudes de eliminación idempotentes

- La aplicación usa el UID como identificador estable del documento de solicitud, en lugar de crear documentos con IDs aleatorios.
- Las solicitudes pendientes existentes se reconocen sin crear duplicados.
- Las reglas de Firestore solo permiten crear la solicitud en `accountDeletionRequests/{uid}` del propio usuario. Esto evita que un cliente genere múltiples solicitudes cambiando el ID del documento.
- La actualización y el borrado de solicitudes siguen reservados a administradores.

**Importante:** esta mejora evita solicitudes duplicadas, pero no implementa por sí sola el borrado efectivo de Firebase Authentication, Firestore ni los medios de Cloudinary. La solicitud sigue pendiente de un procesador backend seguro.
