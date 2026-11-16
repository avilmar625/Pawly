# Cambios 64 — Registro y acceso rápido a avisos

## Corrección crítica del registro

La creación de una cuenta escribe en un único batch tres documentos relacionados:
- `usernames/{username}`
- `usernameReservations/{uid}`
- `users/{uid}`

La regla de creación del perfil consultaba la reserva mediante `exists()`, que observa el estado anterior al batch. Como la reserva se crea en la misma operación atómica, esa comprobación podía fallar aunque el batch contuviera la reserva correcta. Se cambia a `existsAfter()`, coherente con las comprobaciones `getAfter()` del resto del flujo.

Esto corrige un fallo concreto de reglas que podía impedir completar el registro. La confirmación definitiva requiere probar Authentication y Firestore con el proyecto Firebase real y con Emulator Suite.

## Acceso rápido al centro de avisos

Se añade una campanita en la cabecera de Comunidad que abre directamente Actividad/Avisos. Muestra el contador de elementos sin leer. Se mantiene también la pestaña existente para no romper la navegación.

## Funciones ya presentes en esta rama

La revisión del código confirma que ya existían:
- búsqueda local de publicaciones por texto, nombre del perro y autor;
- scroll al último mensaje cuando cambia la lista de mensajes;
- contador de mensajes no leídos por conversación;
- contador y pantalla de notificaciones de actividad;
- filtros de categorías mediante FlowRow, evitando una fila horizontal desplazable.

Estas funciones no deben volver a implementarse en paralelo. Quedan pendientes las mejoras de alertas de perros perdidos por radio y preferencias, lugares concretos/colecciones colaborativas de Explorar, búsqueda segura mediante agenda y validación integral del registro.
