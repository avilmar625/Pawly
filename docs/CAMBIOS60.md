# Cambios 60 — denuncias de eventos y validación de objetivos

## Qué cambia
- Las fichas de eventos incluyen una acción para denunciar el evento.
- Las denuncias admiten el tipo `event` y llegan al centro de moderación existente.
- El equipo administrador puede eliminar el evento denunciado y marcar la denuncia como revisada.
- Las reglas de Firestore limitan los campos aceptados, exigen estado inicial `open`, fecha de servidor y que el objetivo denunciado exista (publicación, usuario o evento).
- La eliminación administrativa de eventos queda autorizada en reglas; el organizador conserva su permiso de eliminación.

## Seguridad y límites
- La app no puede crear denuncias para IDs inexistentes ni enviar campos adicionales.
- La lectura, actualización y eliminación de denuncias sigue reservada a administradores.
- La revisión y eliminación siguen siendo acciones administrativas; no se automatiza una sanción por el mero hecho de recibir denuncias.
- Publica `firestore.rules` de esta rama en Firebase antes de probar esta función. La app y las reglas deben desplegarse coordinadamente.

## Pendiente de la auditoría global
Este cambio no sustituye la auditoría de notificaciones, el procesador real de borrado de cuentas, la verificación documental de profesionales, ni la sustitución futura del mapa WebView/CDN. Tampoco implica que el Worker de Cloudflare esté desplegado.
