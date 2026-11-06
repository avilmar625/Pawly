# cambios30 · Sincronización compartida de cuidados

## Cambios incluidos

- El pasaporte sanitario y las rutinas pueden guardarse en el espacio compartido de la mascota en Firestore, en lugar de quedar exclusivamente ligados a la cuenta de quien los creó.
- Al abrir Pasaporte o Cuidados con sesión iniciada, Pawly crea el espacio compartido si aún no existe.
- La primera inicialización copia al espacio compartido los registros que existían en las antiguas subcolecciones privadas `users/{uid}/petCare/{petId}`. La copia original se conserva.
- Las reglas de Firestore limitan el acceso a miembros aceptados: propietario y editor pueden escribir; lector solo puede leer. Las reglas validan también la estructura y longitud de los campos.
- El modo sin cuenta continúa usando almacenamiento local.
- El workflow de Android se activa también en la rama `cambios30`.

## Antes de probar

1. Despliega las reglas: `firebase deploy --only firestore:rules,firestore:indexes`.
2. Compila e instala la app con el `google-services.json` local del proyecto Firebase.
3. Prueba con dos cuentas verificadas: una propietaria y otra invitada con rol editor/lector.
4. Comprueba lectura y escritura desde ambas cuentas y retirada de acceso.

## Pendiente

- La pantalla todavía no permite seleccionar y abrir directamente una mascota que otra persona ha compartido contigo; esta rama prepara el almacenamiento y los permisos del servidor.
- Los cambios no sustituyen la configuración de Firebase del proyecto ni despliegan reglas por sí mismos.
- El envío automático de notificaciones push de mensajes requiere una función backend de confianza; FCM en Android no envía notificaciones a otros usuarios por sí solo.
- La compilación CI de esta rama debe terminar antes de considerar validado el APK.
