# cambios55 — mejoras de comunidad y registro

Esta rama parte de `cambios54` y agrupa una primera entrega de mejoras para Comunidad.

## Cambios incluidos

- Registro de cuentas con una única escritura atómica en Firestore: reserva de nombre, índice de nombre y perfil se crean en el mismo batch.
- Las reglas de `firestore.rules` usan `getAfter()` / `existsAfter()` para validar las tres escrituras relacionadas.
- El envío del correo de verificación es independiente de la creación de la cuenta. Si el proveedor de correo falla, no se elimina una cuenta y un perfil que ya se han creado correctamente.
- Los errores de permisos de Firestore se traducen a un mensaje que indica que hay que publicar las reglas de esta rama.
- Chat: desplazamiento automático al mensaje más reciente.
- Bandeja de mensajes: contador de mensajes pendientes de leer por conversación (consulta hasta los últimos 100 mensajes entrantes y muestra un máximo de 99).
- Comunidad: búsqueda local de publicaciones por texto, nombre de mascota o autor entre las publicaciones cargadas.
- Los selectores de categorías de Comunidad, Explorar y Eventos se reorganizan en varias filas para evitar que queden opciones ocultas tras un scroll horizontal.

## Paso obligatorio de Firebase antes de probar el registro

Las reglas del repositorio no se publican automáticamente en el proyecto Firebase. Desde la raíz del repositorio, con Firebase CLI autenticada y el proyecto Pawly seleccionado, ejecuta:

```bash
firebase use
firebase deploy --only firestore:rules
```

Comprueba que `firebase use` muestra el proyecto correcto antes de desplegar. No sustituyas las reglas por reglas abiertas de prueba.

## Pendiente para una fase posterior

- Alertas push de mascotas perdidas por proximidad y preferencias por radio. FCM ya registra tokens, pero el envío entre usuarios necesita un backend de confianza; no se debe exponer una clave de servidor en Android ni activar servicios de pago sin aprobación.
- Centro de actividad dentro de Comunidad: lista notificaciones de likes, comentarios y nuevos seguidores; permite marcarlas como leídas y muestra un contador rojo de pendientes. No son todavía notificaciones push del sistema.
- Geolocalización aproximada de avisos de pérdida y filtro real por distancia (5/10/25/50 km). No se debe filtrar por una zona inventada ni publicar domicilios o coordenadas exactas.
- Guardar lugares individuales, crear colecciones personalizadas y publicar lugares de la comunidad con moderación.
- Búsqueda de contactos mediante agenda. Requiere permiso explícito y un diseño que no suba la agenda completa a Firestore.

## Validación

La compilación y las pruebas se validan mediante GitHub Actions. Un resultado verde confirma compilación, tests y lint; no sustituye una prueba funcional en un teléfono real.
