# Configuración de Firebase y Cloudinary para Pawly (cambios17)

Esta guía está pensada para dejar la aplicación conectada desde Android Studio. El código y las reglas están en el repositorio; los proyectos, credenciales y consentimientos deben crearse desde tus cuentas.

## Qué servicios utiliza Pawly

| Servicio | Uso | ¿Requiere Blaze? |
|---|---|---|
| Firebase Authentication | Registro e inicio de sesión por correo; Google opcional | No para las funciones incluidas |
| Cloud Firestore | Perfiles, publicaciones, comentarios, conversaciones, bloqueos y denuncias | No para comenzar; sujeto a cuotas/región |
| Firebase Cloud Messaging | Registro del token y recepción de mensajes push | No para recibir; enviar automáticamente requiere backend |
| Cloudinary | Fotografías de perfiles, mascotas y publicaciones | Tiene plan gratuito sujeto a límites |

Pawly no utiliza Firebase Storage. No se ha incluido una Cloud Function que pueda activar facturación sin que lo decidas.

## 1. Crear el proyecto Firebase

1. Entra en https://console.firebase.google.com/ y crea un proyecto, por ejemplo `pawly-prod`.
2. Puedes desactivar Google Analytics para este proyecto: Pawly no lo necesita para las funciones actuales.
3. En la configuración del proyecto, añade una aplicación Android.
4. Usa exactamente este identificador de paquete: `com.pawly.app`.
5. Descarga `google-services.json` y colócalo en `app/google-services.json` dentro de tu copia local del repositorio.

**No subas ese archivo a GitHub.** Ya está incluido en `.gitignore`. No compartas el archivo en capturas ni mensajes públicos.

## 2. Activar Authentication

En Firebase Console → Authentication → Sign-in method:

- Activa **Email/Password**.
- Activa **Google** solo si quieres ofrecer ese método.
- Configura el correo/nombre de asistencia que solicite Google.

Para Google Sign-In, añade la huella SHA-1 de la firma debug:

```bash
./gradlew signingReport
```

Busca el bloque `Variant: debug` y copia SHA-1. Añádela en Configuración del proyecto → Tus aplicaciones → Android → Huellas digitales SHA. Descarga otra vez `google-services.json` y reemplaza el archivo local.

El inicio de sesión con Google usa Credential Manager y el ID de cliente web que genera la configuración de Google Services. No pegues un OAuth client secret en el código.

## 3. Crear Firestore y desplegar las reglas

1. Firebase Console → Firestore Database → Crear base de datos.
2. Selecciona modo producción.
3. Escoge una región europea cercana a tus usuarios. La región no se puede cambiar después sin migrar datos.
4. Instala Firebase CLI desde https://firebase.google.com/docs/cli si aún no la tienes.
5. Desde la raíz del proyecto, inicia sesión y asocia el proyecto:

```bash
firebase login
firebase projects:list
firebase use --add
```

Selecciona el ID real de tu proyecto. El alias puede ser `default`.

6. Despliega reglas e índices:

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

Los ficheros `firestore.rules` y `firestore.indexes.json` del repositorio son la configuración inicial. Despliégalos antes de probar la Comunidad. Si Firestore muestra que un índice adicional es necesario, revisa la consulta y el enlace que facilita Firebase antes de crearlo.

**No publiques las reglas de prueba abiertas** (`allow read, write: if true`). Mantén las reglas del repositorio y prueba con dos cuentas distintas.

## 4. Cloudinary para fotografías

1. Crea una cuenta en https://cloudinary.com/users/register/free.
2. Copia el **Cloud name** del Dashboard.
3. En Settings → Upload → Upload presets, crea un preset con modo **Unsigned**.
4. Limita formatos a JPG, PNG y WebP y fija un máximo de tamaño razonable (por ejemplo 5 MB).
5. Guarda el nombre exacto del preset.

En el archivo local `local.properties` (junto a `settings.gradle.kts`) añade:

```properties
CLOUDINARY_CLOUD_NAME=tu_cloud_name
CLOUDINARY_UPLOAD_PRESET=tu_preset_unsigned
```

No añadas el API Secret de Cloudinary a Android. El preset unsigned no es un secreto: restringe formatos/tamaño y vigila el consumo. Para una app pública con tráfico relevante, cambia a cargas firmadas desde un backend de confianza.

## 5. Abrir y compilar

1. Abre la carpeta raíz en Android Studio.
2. Usa JDK 17 y Gradle Wrapper.
3. Espera a que finalice la sincronización.
4. Ejecuta:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

El APK de depuración aparece en `app/build/outputs/apk/debug/app-debug.apk`.

Si todavía no has descargado Firebase o no quieres configurar Cloudinary, la aplicación base debe poder compilar igualmente: Firebase Services se aplica solo cuando existe `app/google-services.json`, y las cargas de fotos informan de que faltan las propiedades.

## 6. Firma local para generar un AAB de publicación

No necesitas configurar la firma para compilar ni instalar el APK debug. Cuando quieras preparar una versión release:

1. Genera una clave de subida desde Android Studio (Build → Generate Signed Bundle / APK → Create new) o con `keytool`.
2. Guarda el archivo keystore fuera del repositorio y conserva una copia de seguridad segura.
3. Añade las cuatro propiedades a tu `local.properties` local:

```properties
RELEASE_STORE_FILE=/ruta/segura/pawly-upload.jks
RELEASE_STORE_PASSWORD=tu_password
RELEASE_KEY_ALIAS=tu_alias
RELEASE_KEY_PASSWORD=tu_password
```

4. Ejecuta `./gradlew bundleRelease`. Si falta una propiedad, Gradle se detendrá con un mensaje claro; si no hay ninguna, la tarea de CI genera un AAB sin firmar para validar la compilación.
5. No subas el keystore ni las contraseñas a GitHub. Configura Play App Signing y guarda la clave de subida y sus copias con seguridad.

## 7. Probar la Comunidad de forma segura

Haz las primeras pruebas con dos cuentas tuyas:

- Registro por correo y verificación del mensaje recibido.
- Cerrar y volver a iniciar sesión.
- Recuperar contraseña.
- Completar perfil y añadir mascotas.
- Seguir/dejar de seguir.
- Publicar, dar like y comentar.
- Abrir conversación y enviar mensajes.
- Bloquear/desbloquear y enviar una denuncia.
- Cerrar sesión y comprobar que el token FCM del dispositivo se elimina cuando sea posible.

Las acciones de publicación, seguimiento, comentarios, bloqueo y mensajería requieren correo verificado. Las pruebas de moderación necesitan un usuario con custom claim `admin: true`, que debe asignarse desde un entorno de administración seguro; nunca desde Android.

## 8. Lo que todavía necesita un backend

Estas funciones **no se activan simplemente pegando google-services.json**:

- Envío automático de push cuando llega un mensaje o se publica una alerta.
- Eliminación completa de cuenta y datos relacionados (subcolecciones, posts, comentarios, mensajes, tokens, nombre reservado y archivos de Cloudinary).
- Invitaciones y sincronización familiar de rutinas/pasaporte.
- Cargas firmadas y controladas de Cloudinary para producción.

Implementarlas requiere un servicio de servidor y credenciales privadas fuera de la app. Cloud Functions/Cloud Run pueden requerir el plan de facturación Blaze u otro proveedor con facturación. Como Pawly se había planteado evitando Blaze, no he activado ni desplegado nada que genere cargos. Mientras no se implemente el backend, la solicitud de eliminación solo se registra en `accountDeletionRequests`; no equivale al borrado real. Consulta `docs/ACCOUNT_DELETION_OPERATIONS.md`.

## 9. Antes de producción

- Activar y verificar App Check con Play Integrity y un proveedor debug solo para desarrollo.
- Probar las reglas con Firebase Emulator Suite.
- Completar y publicar la política de privacidad y los términos.
- Publicar un recurso web HTTPS para eliminación de cuenta y conectarlo al proceso real.
- Configurar moderación, soporte y un administrador seguro.
- Revisar cuotas, región, retención, costes y transferencias de datos.
- No incluir claves privadas, service account JSON ni secretos en Git o en el APK.

