# Cambios17 — integración y preparación de configuración

## Objetivo

Esta rama parte de `cambios16` y prepara un punto de trabajo estable para abrir en Android Studio, compilar, conectar Firebase/Cloudinary y continuar afinando antes de cualquier beta pública.

## Cambios incluidos

### 1. Módulo de cuidados más mantenible
- Se extraen los modelos `PassportEntry` y `CareTask` a `care/model/PetCareModels.kt`.
- Se extrae el acceso a SharedPreferences/JSON a `care/data/PetCareLocalStore.kt`.
- La pantalla mantiene la composición visual y consume modelos/adaptador separados.
- Se conservan las claves `v16_passport_...` y `v16_care_...` para no perder datos locales existentes en una actualización.

### 2. Preparación de compilación
- El workflow incluye `cambios17`.
- Ejecuta tests unitarios, Android Lint, `assembleDebug` y `bundleRelease`.
- Sube APK debug y AAB release sin firmar como artefactos si todos los pasos pasan.
- El AAB no está firmado para Google Play; la firma final y el Play App Signing se configuran en el entorno del propietario.

### 3. Configuración externa documentada
- Guía renovada `docs/FIREBASE_SETUP.md`.
- Plantilla segura `local.properties.example`.
- Requisitos de Firebase Authentication, SHA-1, Firestore, índices, reglas y Cloudinary documentados.
- No se suben credenciales ni secretos.

### 4. Documentación técnica
- Corregido el dato de SDK: compileSdk 36 y targetSdk 36.
- Documentada la separación actual y la deuda técnica.
- README actualizado con la ruta de configuración y el estado real.

## Qué puedes hacer tú al llegar a casa

1. Descarga la rama `cambios17` o cambia a ella en Android Studio.
2. Abre la carpeta raíz, usa JDK 17 y Gradle Wrapper.
3. Ejecuta `./gradlew testDebugUnitTest`, `./gradlew lintDebug` y `./gradlew assembleDebug`.
4. Si compila, instala el APK debug y recorre Inicio, Salud, Diario, Aprender, Explorar y Comunidad.
5. Configura Firebase siguiendo `docs/FIREBASE_SETUP.md`.
6. Copia los valores de Cloudinary a tu `local.properties` local y vuelve a sincronizar Gradle.
7. Despliega reglas e índices con `firebase deploy --only firestore:rules,firestore:indexes`.
8. Prueba la Comunidad con dos cuentas controladas.

## Lo que no se puede completar sin tus cuentas/decisiones

- Crear el proyecto Firebase y elegir región.
- Aceptar términos y activar proveedores de Authentication.
- Registrar el SHA-1 de tu firma y descargar tu `google-services.json`.
- Crear Cloudinary y configurar el preset unsigned.
- Crear/validar correo de soporte, política de privacidad, términos y datos del responsable.
- Configurar firma de release y Play Console.
- Decidir si aceptas un backend con facturación para funciones de servidor.

## Límites técnicos que siguen abiertos

- El pasaporte y las rutinas se guardan localmente en SharedPreferences/JSON, no en Firestore. El almacenamiento se identifica por nombre de mascota, por lo que dos perfiles locales con el mismo nombre pueden compartir la misma clave. La siguiente migración debe introducir IDs estables y Room.
- No hay invitaciones ni sincronización entre cuidadores; el campo responsable es una nota local.
- No hay adjuntos, importación de cartillas ni PDF.
- Los perfiles locales y varias áreas todavía son canino-céntricas; la multi-especie de Comunidad no generaliza automáticamente toda la app.
- FCM registra el token y recibe mensajes, pero no hay envío automático desde un backend.
- La solicitud de eliminación de cuenta solo registra una petición; no borra la cuenta ni todos los datos. No publicar como función de eliminación completada.
- El preset unsigned de Cloudinary es adecuado para pruebas controladas, no constituye una frontera fuerte de seguridad para una aplicación pública a escala.

## Importante sobre facturación

No se ha desplegado Firebase Functions, Cloud Run ni otro backend, y no se ha activado Blaze. Los envíos push automáticos, la eliminación integral de cuentas y las invitaciones remotas necesitan un entorno de servidor. Si se decide mantener el requisito de no usar Blaze, habrá que elegir y presupuestar un proveedor alternativo con un nivel gratuito adecuado. No pongas credenciales de servicio en el cliente Android.

## Verificación

Los cambios se han escrito en GitHub, pero el estado de compilación debe considerarse **pendiente de CI** hasta que GitHub Actions termine la ejecución de esta rama. No se afirma que el proyecto compile o que la comunidad funcione con Firebase hasta comprobarlo en CI y en un dispositivo con un proyecto de prueba configurado.
