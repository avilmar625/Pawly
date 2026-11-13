# Cambios 61 — privacidad del contacto profesional

## Qué cambia
- El contacto profesional deja de guardarse en el documento público `users/{uid}`.
- Se almacena en `users/{uid}/privateData/professionalContact`, accesible solo al propietario salvo que se cumplan todas las condiciones de publicación.
- El profesional debe activar una opción explícita para mostrar el contacto; además, su perfil debe ser público y el contacto no puede estar vacío.
- Las personas bloqueadas en cualquiera de los dos sentidos no pueden leer el contacto.
- La pantalla de perfil público solo presenta el contacto cuando la autorización está activa.
- Los contactos antiguos que sigan en el documento público se migran al espacio privado al abrir la comunidad con esa cuenta. La migración desactiva su visibilidad pública para no interpretar el comportamiento antiguo como consentimiento explícito.
- Se añaden pruebas unitarias para la política de publicación y traducciones de la nueva opción.

## Despliegue coordinado
1. Publicar las nuevas `firestore.rules` de esta rama.
2. Distribuir la versión Android con la migración.
3. No revertir a una versión anterior que vuelva a escribir `professionalContact` en el documento público.

## Limitación
La migración de los documentos antiguos se realiza cuando cada propietario vuelve a cargar su perfil. Para una migración masiva previa al lanzamiento, ejecutar un proceso administrativo controlado sobre los documentos existentes; no se requiere Blaze para el diseño de la app, pero sí credenciales privilegiadas protegidas y una ejecución operativa segura.
