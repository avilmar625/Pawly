# Configuración de Firebase y Cloudinary para Pawly (cambios19)

Esta guía separa lo que debe hacerse en la consola de Firebase de lo que ya está preparado en el repositorio. No compartas credenciales privadas ni subas configuraciones locales a GitHub.

## 1. Descargar google-services.json

1. Abre https://console.firebase.google.com/ y entra en tu proyecto existente de Pawly.
2. Pulsa el engranaje junto a **Descripción general del proyecto** → **Configuración del proyecto** → pestaña **General**.
3. Baja hasta **Tus aplicaciones**.
4. Selecciona la aplicación Android cuyo nombre de paquete sea exactamente `com.pawly.app`.
5. Pulsa **Descargar google-services.json**.
6. Copia el archivo descargado a la carpeta `app/` de la raíz del repositorio. La ruta final debe ser `app/google-services.json`.
7. Comprueba que el nombre sea exactamente `google-services.json`, sin sufijos como `(1)`.

Si no existe todavía una app Android en ese proyecto, pulsa **Añadir app** → Android e introduce `com.pawly.app`. No registres otra aplicación con el mismo paquete si ya aparece una.

El archivo contiene identificadores del proyecto, no una contraseña de servidor. Aun así, Pawly lo mantiene fuera de Git con `.gitignore`. No lo publiques ni compartas su contenido.

## 2. Activar Authentication

En Firebase Console → **Authentication** → **Comenzar** → **Método de acceso**:

- Activa **Correo electrónico/contraseña**.
- Guarda los cambios.
- Google es opcional. Si lo activas, añade antes la huella SHA-1 de debug (ver debajo), vuelve a descargar el JSON y sustituye el archivo local.

Para obtener la huella debug desde la raíz del proyecto:

```bash
./gradlew signingReport
```

Busca `Variant: debug` y copia `SHA1`. Añádela en Configuración del proyecto → General → Tu app Android → Huellas digitales SHA. Después vuelve a descargar `google-services.json`.

## 3. Crear Firestore

1. En Firebase Console abre **Firestore Database** → **Crear base de datos**.
2. Selecciona **modo producción**.
3. Escoge una región europea adecuada para tus usuarios. La ubicación de la base de datos no se puede cambiar después sin migrar los datos.
4. No pegues reglas abiertas de pruebas. Las reglas versionadas en `firestore.rules` limitan los datos privados por UID.

Desde la raíz del proyecto, instala/actualiza Firebase CLI si es necesario y ejecuta:

```bash
firebase login
firebase projects:list
firebase use --add
```

Selecciona el ID del proyecto que acabas de abrir; puedes usar el alias `default`. Antes de desplegar, comprueba que el proyecto activo sea el correcto:

```bash
firebase use
```

Despliega las reglas e índices del repositorio:

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

No uses reglas de desarrollo del tipo `allow read, write: if true`.

## 4. Datos privados de mascotas (cambios19)

La rama cambios19 incorpora `FirebasePetCareRepository` y reglas de propietario para:

- Pasaporte veterinario: `users/{uid}/petCare/{petId}/passport/{entryId}`.
- Rutinas: `users/{uid}/petCare/{petId}/tasks/{taskId}`.

Solo la cuenta propietaria puede leer o modificar esos registros. En cambios20, las secciones Pasaporte sanitario y Cuidados ya utilizan este repositorio cuando el usuario ha iniciado sesión. Al entrar, descargan los registros remotos y, si Firestore está vacío para esa mascota, importan en una operación por lotes los registros locales de esa cuenta. Las altas, cambios y eliminaciones se guardan localmente y se envían a Firestore. Si falla la red, la copia local se conserva y la pantalla indica que la sincronización está pendiente. El modo local sin cuenta sigue guardando solo en el dispositivo.

Los perfiles de mascota de Comunidad continúan guardándose en el documento privado `users/{uid}` como `dogProfiles`. La siguiente fase debe vincular el ID estable de cada mascota con su espacio `petCare`.

## 5. Cloudinary para fotografías

1. Crea/abre una cuenta en https://cloudinary.com/.
2. Copia el **Cloud name** del Dashboard.
3. En Settings → Upload → Upload presets crea un preset **Unsigned**.
4. Limita los formatos admitidos (JPG, PNG y WebP) y el tamaño máximo.
5. En `local.properties` (raíz del proyecto) añade:

```properties
CLOUDINARY_CLOUD_NAME=tu_cloud_name
CLOUDINARY_UPLOAD_PRESET=tu_preset_unsigned
```

No añadas el API Secret de Cloudinary a Android. El preset unsigned es práctico para pruebas, pero no sustituye cargas firmadas desde un backend para producción.

## 6. Compilar y probar

Con JDK 17 y Android Studio:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Prueba primero una cuenta de correo y después una segunda cuenta distinta. Verifica registro, correo de verificación, inicio/cierre de sesión, recuperación de contraseña, perfil, mascotas, publicaciones y mensajería. No uses datos médicos reales durante las pruebas iniciales.

## 7. Qué no está automatizado todavía

- Las pantallas principales Inicio, Salud, Diario y Explorar siguen conservando parte de sus datos en almacenamiento local.
- El nuevo repositorio de salud aún necesita integración con la UI y una migración local confirmada por el usuario.
- Las invitaciones de cuidadores y los permisos compartidos entre familiares necesitan un modelo específico; no se debe compartir una cuenta ni abrir los datos de salud.
- FCM registra tokens y recibe notificaciones, pero el envío automático necesita backend confiable.
- La solicitud de eliminación de cuenta aún no borra todos los documentos, subcolecciones ni archivos de Cloudinary.
- App Check debe activarse después de registrar y probar la app; no actives enforcement hasta validar instalaciones debug y release.
- Funciones servidor (Cloud Functions/Cloud Run), cargas firmadas y tareas administrativas pueden requerir facturación. No actives Blaze sin revisar primero presupuesto y límites.

## 8. Privacidad y seguridad antes de producción

- Mantén las reglas de Firestore cerradas y pruébalas con dos cuentas.
- No guardes service account keys, contraseñas, OAuth client secrets ni API secrets en el APK o en GitHub.
- Completa política de privacidad, términos, soporte y procedimiento de eliminación de cuenta.
- Configura alertas de presupuesto y revisa el uso de cada servicio.


## 8. Datos sanitarios compartidos (cambios30)

Al entrar en Pasaporte o Cuidados con una cuenta, Pawly crea (si aún no existe) el espacio compartido de esa mascota y guarda allí los registros nuevos: `sharedPets/{ownerUid}_{petId}/passport` y `/tasks`. Los perfiles compartidos usan los miembros aceptados del documento `sharedPets`: propietario y editores pueden escribir; lectores solo pueden consultar. Las reglas de Firestore aplican los permisos en servidor, no dependen de ocultar botones en Android.

La primera carga conserva la migración local que ya existía: si la colección compartida está vacía y hay datos locales, se importan. Los datos antiguos de `users/{uid}/petCare` se copian automáticamente al espacio compartido la primera vez que el propietario abre Pasaporte o Cuidados. La copia antigua se conserva; la migración no elimina documentos. Antes de desplegar, revisa y publica las reglas actualizadas:

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

La selección de mascotas recibidas por invitación y su navegación desde el perfil de otro cuidador se completarán en la siguiente iteración. No compartas una cuenta para probarlo: utiliza dos cuentas verificadas distintas.
