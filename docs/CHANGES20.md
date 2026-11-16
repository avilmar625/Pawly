# cambios20 — Sincronización de salud y cuidados

## Cambios
- Conectadas las pantallas Pasaporte sanitario y Cuidados con `FirebasePetCareRepository` cuando hay una sesión Firebase activa.
- Las altas, ediciones de estado y eliminaciones se guardan primero en el almacenamiento local y se sincronizan con Firestore.
- Al abrir por primera vez cada sección, Pawly recupera los registros remotos.
- Si Firestore todavía no contiene registros para esa mascota, se importan en una única operación por lotes los datos locales de esa cuenta. La importación por lotes evita dejar una migración parcialmente escrita si falla la conexión.
- Se muestra el estado de sincronización y se conserva la copia local si una operación remota falla.
- El modo local sin cuenta continúa funcionando sin cambios.

## Estructura en Firestore
- `users/{uid}/petCare/{petId}/passport/{entryId}`
- `users/{uid}/petCare/{petId}/tasks/{taskId}`

Las reglas de `firestore.rules` restringen la lectura y escritura al propietario autenticado.

## Identificador de mascota
En esta primera integración, el identificador remoto se deriva de forma determinista del nombre normalizado de la mascota. Si se cambia el nombre, la sección puede resolver un espacio nuevo en Firestore; una siguiente mejora debe introducir un ID de mascota persistente en el perfil para que los cambios de nombre no separen el historial.

## Pruebas pendientes en dispositivo
1. Desplegar reglas e índices de Firestore.
2. Iniciar sesión con una cuenta de prueba.
3. Crear un registro sanitario y una rutina; cerrar y volver a abrir la app.
4. Instalar Pawly en otro dispositivo/emulador con la misma cuenta y verificar que aparecen.
5. Repetir con otra cuenta y confirmar que no puede leer los registros anteriores.
6. Probar sin conexión: los cambios quedan locales y la interfaz informa de que no se han sincronizado.

## Limitaciones que siguen abiertas
- Inicio, Diario, Explorar y datos generales del perfil aún no están sincronizados íntegramente.
- Las invitaciones y permisos entre cuidadores no están implementados.
- La sincronización de pasaporte y cuidados es de carga/guardado al entrar y al realizar acciones; no usa listeners en tiempo real.
- Los registros de salud no incluyen adjuntos, PDF ni exportación.
- No se debe considerar lista para producción hasta completar pruebas de reglas, App Check, privacidad y eliminación de cuenta.
