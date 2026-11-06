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

Busca `Variant: debug` y copia `SHA1`; si vas a probar un APK firmado de release, registra también la SHA-1 de esa variante. Añádelas en Configuración del proyecto → General → Tu app Android → Huellas digitales SHA. Guarda y vuelve a descargar `google-services.json` en `app/`. Comprueba que en Authentication → Método de acceso → Google está habilitado y tiene un correo de asistencia configurado. En el JSON debe aparecer un cliente OAuth Android (tipo 1) con el package `com.pawly.app` y su SHA-1, además de un cliente Web (tipo 3); el plugin genera con este último el recurso `default_web_client_id` que Pawly usa como `serverClientId`.

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

## 5. Cloudinary para fotografías (incluidos mensajes privados)

1. Crea/abre una cuenta en https://cloudinary.com/.
2. Copia el **Cloud name** del Dashboard.
3. En Settings → Upload → Upload presets crea un preset **Unsigned**.
4. Limita los formatos admitidos (JPG, PNG y WebP) y el tamaño máximo. Pawly convierte las fotos a JPEG y las reduce antes de subirlas. El preset unsigned no requiere API Secret en el móvil. Las fotos de chat se suben a `pawly/message-photos/{uid}`; asegúrate de que el preset permita esa ruta. **Importante:** con el preset unsigned las imágenes tienen URL pública de Cloudinary. Las reglas de Firestore protegen el mensaje y su URL, pero no convierten el archivo de Cloudinary en privado: cualquiera que obtenga el enlace podría abrirlo. No envíes imágenes sensibles. El cifrado de extremo a extremo o la entrega privada requieren una fase adicional.
5. En `local.properties` (raíz del proyecto) añade:

```properties
CLOUDINARY_CLOUD_NAME=tu_cloud_name
CLOUDINARY_UPLOAD_PRESET=tu_preset_unsigned
```

No añadas el API Secret de Cloudinary a Android. El preset unsigned es práctico para pruebas, pero no sustituye cargas firmadas desde un backend para producción.

## 6. Plan gratuito y límites de servicios

- Mantén Firebase en el plan **Spark**: Authentication con Google/correo, Firestore dentro de sus cuotas y FCM son suficientes para cuentas, perfiles, publicaciones, comentarios y chat en tiempo real mientras la app está abierta. Revisa las cuotas de Firestore en la consola.
- **No uses Firebase Storage** en Spark: los buckets de Cloud Storage for Firebase requieren Blaze.
- Las imágenes se alojan en Cloudinary, no en Firebase Storage. Su plan gratuito tiene límites propios que pueden cambiar; controla el uso en su Dashboard.
- FCM puede recibir notificaciones sin coste, pero enviar notificaciones desde el servidor al destinatario exige un backend confiable. Cloud Functions requiere Blaze. En Spark no habrá push automático de mensajes; el chat se sincroniza en tiempo real mientras la app está conectada.
- No vincules una cuenta de facturación ni actives Blaze para seguir estos pasos.

## 7. Compilar y probar

Con JDK 17 y Android Studio:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Prueba primero una cuenta de correo y después una segunda cuenta distinta. Verifica registro, correo de verificación, inicio/cierre de sesión, recuperación de contraseña, perfil, mascotas, publicaciones y mensajería. No uses datos médicos reales durante las pruebas iniciales.

## 8. Qué no está automatizado todavía

- Las pantallas principales Inicio, Salud, Diario y Explorar siguen conservando parte de sus datos en almacenamiento local.
- El nuevo repositorio de salud aún necesita integración con la UI y una migración local confirmada por el usuario.
- Las invitaciones de cuidadores y los permisos compartidos entre familiares necesitan un modelo específico; no se debe compartir una cuenta ni abrir los datos de salud.
- FCM registra tokens y recibe notificaciones; el envío automático desde Pawly necesita backend confiable. En cambios30 se registra el token al iniciar sesión, pero todavía no se envían pushes de mensajes.
- La solicitud de eliminación de cuenta aún no borra todos los documentos, subcolecciones ni archivos de Cloudinary.
- App Check debe activarse después de registrar y probar la app; no actives enforcement hasta validar instalaciones debug y release.
- Funciones servidor (Cloud Functions/Cloud Run), cargas firmadas y tareas administrativas pueden requerir facturación. No actives Blaze sin revisar primero presupuesto y límites.

## 9. Privacidad y seguridad antes de producción

- Mantén las reglas de Firestore cerradas y pruébalas con dos cuentas.
- No guardes service account keys, contraseñas, OAuth client secrets ni API secrets en el APK o en GitHub.
- Completa política de privacidad, términos, soporte y procedimiento de eliminación de cuenta.
- Configura alertas de presupuesto y revisa el uso de cada servicio.


## 10. Datos sanitarios compartidos (cambios30)

Al entrar en Pasaporte o Cuidados con una cuenta, Pawly crea (si aún no existe) el espacio compartido de esa mascota y guarda allí los registros nuevos: `sharedPets/{ownerUid}_{petId}/passport` y `/tasks`. Los perfiles compartidos usan los miembros aceptados del documento `sharedPets`: propietario y editores pueden escribir; lectores solo pueden consultar. Las reglas de Firestore aplican los permisos en servidor, no dependen de ocultar botones en Android.

La primera carga conserva la migración local que ya existía: si la colección compartida está vacía y hay datos locales, se importan. Los datos antiguos de `users/{uid}/petCare` se copian automáticamente al espacio compartido la primera vez que el propietario abre Pasaporte o Cuidados. La copia antigua se conserva; la migración no elimina documentos. Antes de desplegar, revisa y publica las reglas actualizadas:

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

La selección de mascotas recibidas por invitación y su navegación desde el perfil de otro cuidador se completarán en la siguiente iteración. No compartas una cuenta para probarlo: utiliza dos cuentas verificadas distintas.


## 11. Diagnóstico de Google Sign-In

Si el botón de Google muestra un error:

1. Comprueba que el package Android en Firebase sea exactamente `com.pawly.app`.
2. Ejecuta `./gradlew signingReport` desde la raíz y registra en Firebase la SHA-1 de `debug` (la que usa Android Studio al pulsar Run). Si pruebas un APK release firmado, registra también la SHA-1 de esa clave.
3. Comprueba que Google esté habilitado como proveedor en Authentication y que exista un correo de asistencia.
4. Descarga otra vez el JSON y reemplaza `app/google-services.json`.
5. En Android Studio ejecuta **Sync Project with Gradle Files**, desinstala la app del móvil e instala de nuevo la compilación.
6. Si sigue fallando, copia el texto exacto del error de Pawly y el resultado de `signingReport` (puedes ocultar los SHA salvo los primeros/últimos caracteres). Nunca compartas claves privadas ni el contenido completo de archivos sensibles.

El botón usa Credential Manager para obtener un ID token de Google y Firebase Auth para validarlo. Que el proveedor esté activado no basta si el JSON descargado no contiene el cliente OAuth Web correcto.
