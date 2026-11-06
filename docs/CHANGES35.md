# cambios35 — sincronización y colaboración completas

## Objetivo
Cerrar las partes más importantes que seguían siendo locales o estaban descritas de forma incompleta, sin añadir servicios de pago ni modificar `main`.

## Implementado
- Repositorio Firestore `FirebasePetProfileRepository` para nombre, foto y Diario por mascota.
- Fotografías de perfil comprimidas y subidas a Cloudinary desde galería o cámara; Firestore guarda la URL HTTPS.
- Sincronización del Diario con hasta 200 recuerdos por mascota. Al iniciar, combina entradas locales y remotas sin descartar recuerdos locales distintos.
- Aislamiento local por ID estable de mascota para foto, diario y marcadores rápidos de rutina; migración de preferencias antiguas al primer perfil.
- Conservación del ID estable al aplicar un cambio de nombre remoto.
- Acceso desde Salud → Compartir a perfiles aceptados.
- Pasaporte y rutinas consultan el espacio del propietario compartido; los lectores ven los datos sin controles de edición y propietarios/editores pueden modificarlos.
- Reglas Firestore para ficha y Diario compartidos, además de los permisos existentes para pasaporte y rutinas.
- Mensajes de estado de sincronización en el editor de perfil.
- CI habilitada para `cambios35`.

## Datos y privacidad
- No se usa Firebase Storage.
- No se activa Blaze ni se añaden claves privadas al APK.
- Cloudinary usa preset unsigned: las imágenes tienen URL pública para quien la conozca. No subir contenido sensible.
- Si no hay conexión o faltan reglas, la copia local se conserva; las funciones cloud no deben considerarse sincronizadas hasta que la app lo confirme.

## Activación
Desplegar reglas e índices desde la raíz:

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

Configurar `CLOUDINARY_CLOUD_NAME` y `CLOUDINARY_UPLOAD_PRESET` en `local.properties`.

## Pendiente externo / no simulado
- Push saliente automático: requiere backend confiable y, en Firebase, Blaze/Cloud Functions.
- Borrado completo de cuenta y cascada de datos: requiere backend y proceso operativo.
- Agenda rápida y checks del día siguen locales.
- No hay cifrado E2E ni almacenamiento privado de imágenes Cloudinary.
- Revisión legal, moderación operativa y pruebas físicas en Redmi/iOS siguen siendo necesarias antes de publicar.
