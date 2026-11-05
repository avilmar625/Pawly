# cambios19 — Firebase y sincronización multiusuario

## Objetivo

Convertir Pawly en una aplicación con cuentas y datos privados sincronizados, sin romper cambios18 ni introducir servicios de pago innecesarios.

## Trabajo incluido

- Nueva rama basada en cambios18.
- Puerta de autenticación global: registro/inicio de sesión antes de entrar en Pawly, sesión persistente y retorno a la pantalla de acceso al cerrar sesión. Se conserva una opción explícita de modo local.\n- Almacenamiento local separado por UID para evitar que dos cuentas del mismo dispositivo compartan preferencias privadas; el modo local conserva el almacenamiento anterior.\n- Nuevo `FirebasePetCareRepository` para leer, crear, actualizar y borrar entradas del pasaporte veterinario y rutinas bajo el UID autenticado.
- Reglas Firestore owner-only para las subcolecciones privadas de salud y rutinas.
- CI Android ejecutado también en cambios19.
- Guía de Firebase actualizada con descarga de `google-services.json`, activación de Email/Password, configuración de Firestore y despliegue de reglas.

## Modelo privado nuevo

```text
users/{uid}/petCare/{petId}/passport/{entryId}
users/{uid}/petCare/{petId}/tasks/{taskId}
```

La seguridad se basa en el UID de Firebase Authentication, no en el nombre visible de la mascota ni en el email.

## Importante: no afirmar que la migración está terminada

El repositorio de salud ya está creado, pero todavía no se ha conectado a las pantallas Salud ni al almacenamiento local. No se importan ni eliminan datos locales automáticamente. La UI deberá resolver la identidad estable de cada mascota, detectar datos locales, mostrar una vista previa y pedir confirmación antes de subirlos.

El resto de los datos de Inicio, Diario y Explorar también requiere integración gradual con la cuenta. La Comunidad ya dispone de su propio acceso Firebase, pero el inicio de sesión todavía no es una puerta global de toda la aplicación.

## Tareas del propietario del proyecto

1. Descargar `google-services.json` para `com.pawly` y guardarlo en `app/google-services.json` (ignorado por Git).
2. Activar Email/Password en Firebase Authentication.
3. Crear Firestore en región europea.
4. Seleccionar el proyecto correcto con Firebase CLI.
5. Desplegar `firestore.rules` e índices.
6. Configurar Cloudinary en `local.properties`.
7. Probar con dos cuentas distintas.

## Siguientes fases técnicas

1. Puerta global de autenticación y sesión persistente.
2. ID estable de mascotas y repositorio cloud único para perfiles.
3. Integración de Salud con migración local confirmada.
4. Sincronización de Diario, tareas y lugares guardados.
5. Modelo de invitaciones de cuidadores con permisos granulares.
6. Pruebas de reglas Firestore con Emulator Suite y App Check.
7. Backend seguro para notificaciones push y eliminación completa de cuenta.

## Coste

No se despliega Cloud Functions ni Cloud Run y no se activa Blaze automáticamente. Revisar límites y facturación antes de añadir servicios de servidor.
