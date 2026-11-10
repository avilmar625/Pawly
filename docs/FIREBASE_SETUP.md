# Configuración completa de Firebase y Cloudinary para Pawly

Esta guía describe la configuración de servicios para las versiones Android de Pawly. Separa los pasos que se hacen una sola vez en las consolas de Firebase y Cloudinary de los cambios que ya están preparados en Android.

**Arquitectura elegida para mantener el coste controlado:**

- **Firebase Authentication:** registro e inicio de sesión.
- **Cloud Firestore:** perfiles sociales, publicaciones, comentarios, conversaciones, invitaciones y datos compartidos de mascotas.
- **Firebase Cloud Messaging (FCM):** registro del token Android y recepción de mensajes push cuando exista un emisor de confianza.
- **Cloudinary:** alojamiento y entrega CDN de fotos y vídeos.
- **Almacenamiento local Android:** preferencias, recordatorios locales y partes de la experiencia que aún no se sincronizan.

No se utiliza Firebase Storage. No hace falta activar Blaze ni vincular una tarjeta para seguir esta arquitectura. Firebase Storage exige actualmente el plan Blaze; por eso las fotos y vídeos se alojan en Cloudinary.

---

## 1. Firebase: proyecto y aplicación Android

1. Entra en [Firebase Console](https://console.firebase.google.com/) y abre el proyecto de Pawly.
2. En el engranaje de **Configuración del proyecto → General → Tus aplicaciones**, busca la app Android con el identificador exacto:
   `com.pawly.app`.
3. Si no existe, pulsa **Añadir aplicación → Android** y registra ese identificador. No crees otra app duplicada si ya existe.
4. Descarga `google-services.json`.
5. Colócalo en la raíz del módulo Android, exactamente en:

   ```
   app/google-services.json
   ```

Este archivo contiene identificadores de configuración del proyecto. Está excluido de Git por `.gitignore`; no lo subas al repositorio ni lo pegues en un chat público. Sin este archivo, Pawly puede compilar, pero no inicializará Firebase en el dispositivo.

## 2. Firebase Authentication

En **Authentication → Comenzar → Método de acceso**:

1. Activa **Correo electrónico/contraseña**.
2. Guarda.
3. Para habilitar Google:
   - Activa el proveedor **Google**.
   - Configura el correo de asistencia.
   - En Android Studio ejecuta `./gradlew signingReport`.
   - Copia la huella **SHA-1** de la variante `debug` y añádela en **Configuración del proyecto → General → Tu app Android → Huellas digitales SHA**.
   - Descarga de nuevo `google-services.json` y reemplaza el anterior.
   - Comprueba que el JSON contiene el cliente OAuth Android y el cliente OAuth Web. El cliente Web es el que Credential Manager entrega a Firebase como `serverClientId`.

La aplicación envía correo de verificación en el registro. Algunas acciones sociales y de publicación requieren una cuenta verificada. Para probar Google Sign-In, usa primero una compilación debug con su SHA-1 registrada.

## 3. Cloud Firestore

1. Abre **Firestore Database → Crear base de datos**.
2. Selecciona **modo producción**.
3. Elige una región europea próxima a los usuarios. La región no se puede cambiar después sin migrar la base de datos.
4. No pegues reglas de prueba abiertas. Las reglas versionadas en este repositorio controlan el acceso por UID, pertenencia y rol.

### Vincular la CLI al proyecto

Instala Firebase CLI si aún no está instalada. Desde la carpeta raíz del repositorio ejecuta:

```bash
firebase login
firebase projects:list
firebase use --add
```

Selecciona el ID del proyecto de Pawly y usa el alias `default`. Este paso crea el archivo local `.firebaserc`; no inventes el ID ni lo añadas a Git si contiene configuración específica de tu cuenta.

Comprueba el proyecto activo:

```bash
firebase use
```

Debe mostrar el proyecto correcto antes de desplegar. Publica reglas e índices:

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

El archivo `firebase.json` ya apunta a `firestore.rules` y `firestore.indexes.json`. No hay reglas abiertas del tipo `allow read, write: if true`.

### Datos que ya usan Firestore

- Comunidad: perfiles de usuario, perfiles de mascotas, publicaciones, likes, comentarios, seguidores, bloqueos, denuncias y conversaciones.
- Chat: mensajes de texto, fotos y vídeos; acceso restringido a los dos participantes.
- Mascotas compartidas: invitaciones, miembros y roles; el propietario y los editores pueden modificar los registros permitidos, los lectores solo consultan.
- Pasaporte sanitario, rutinas, ficha básica y Diario: espacio de mascota compartido bajo `sharedPets/{ownerUid}_{petId}`, con las reglas de miembro/rol correspondientes.

Los recordatorios del dispositivo y algunas marcas de tareas cotidianas siguen siendo locales. No se debe interpretar que todo el contenido de Pawly se sincroniza automáticamente por el mero hecho de iniciar sesión.

## 4. Cloudinary: cuenta y presets de subida

Crea o abre una cuenta en [Cloudinary](https://cloudinary.com/). En el Dashboard copia el **Cloud name**.

Pawly usa dos presets unsigned distintos para que cada tipo de archivo tenga una lista de formatos independiente. En **Settings → Upload → Upload presets**, crea:

### Preset A: imágenes

- Nombre sugerido: `pawly_images_unsigned`.
- Signing Mode: **Unsigned**.
- Allowed formats: `jpg, jpeg, png, webp`.
- Activa **Disallow public ID** si aparece en las opciones.
- No permitas formatos que Pawly no utiliza.
- La app convierte las fotografías a JPEG y las optimiza antes de subirlas.

### Preset B: vídeos

- Nombre sugerido: `pawly_videos_unsigned`.
- Signing Mode: **Unsigned**.
- Allowed formats: `mp4, mov, webm, 3gp`.
- Activa **Disallow public ID** si aparece en las opciones.
- No permitas otros formatos.

Guarda ambos presets y copia sus nombres exactamente. Los nombres de presets unsigned quedan visibles en el APK y no son contraseñas.

**Límite importante de seguridad:** Cloudinary no aplica un límite de tamaño por preset unsigned. Pawly comprueba 8 MB para las fotos ya comprimidas y 40 MB para los vídeos en el cliente, pero un cliente modificado podría saltarse esa comprobación. Los presets unsigned también permiten que cualquiera que conozca el nombre intente subir archivos. Esta arquitectura es adecuada para pruebas y uso inicial controlado, pero no equivale a cargas autenticadas de producción. Para una comunidad pública a gran escala habrá que añadir un backend que firme las subidas y valide al usuario; no se debe poner el API Secret en Android.

Las carpetas `pawly/...` que envía la app sirven para organizar el contenido, no son una barrera de seguridad. Las URLs entregadas por Cloudinary son públicas para cualquiera que las conozca. Firestore protege el acceso a los mensajes y publicaciones, pero no vuelve privada la URL del archivo ni ofrece cifrado de extremo a extremo. No subas documentos, imágenes íntimas ni material sensible.

## 5. Configuración de Cloudinary en Android

**Opción recomendada para una app ya instalada:** abre Pawly → Más → Ajustes → Servicios multimedia. Introduce el Cloud name y los dos presets unsigned (imágenes y vídeos) y pulsa Guardar. La configuración queda almacenada en el dispositivo y no requiere generar otro APK. Esto permite probar Cloudinary también en instalaciones distribuidas.

**Opción para desarrolladores:** en la raíz del proyecto, antes de compilar:

1. Copia `local.properties.example` como `local.properties`.
2. Rellena los tres valores:

```properties
CLOUDINARY_CLOUD_NAME=tu_cloud_name
CLOUDINARY_IMAGE_UPLOAD_PRESET=pawly_images_unsigned
CLOUDINARY_VIDEO_UPLOAD_PRESET=pawly_videos_unsigned
```

3. Guarda el archivo. No añadas el API Secret de Cloudinary.
4. Sincroniza Gradle en Android Studio.

La app conserva compatibilidad temporal con el antiguo valor `CLOUDINARY_UPLOAD_PRESET`: si falta uno de los presets nuevos, utiliza ese valor como alternativa. Es preferible configurar los dos presets separados y retirar la clave antigua.

## 6. Fotos y vídeos: funcionamiento actual

| Contenido | Servicio | Límites de la app |
|---|---|---|
| Foto de perfil social | Cloudinary + URL en Firestore | JPEG optimizado |
| Foto de mascota / ficha | Cloudinary + URL en Firestore | JPEG optimizado |
| Foto de publicación | Cloudinary + URL en Firestore | 8 MB tras compresión |
| Vídeo de publicación | Cloudinary + URL en Firestore | 40 MB |
| Foto en mensaje privado | Cloudinary + URL en Firestore | 8 MB tras compresión |
| Vídeo en mensaje privado | Cloudinary + URL en Firestore | 40 MB |

Las fotos se redimensionan antes de subirlas (lado mayor máximo aproximado de 1600 px, calidad JPEG 82). Los vídeos admitidos son MP4, MOV, WebM y 3GP; se suben al endpoint de vídeo de Cloudinary sin transcodificación previa. En publicaciones y chat se muestra un reproductor integrado.

Firestore almacena las URL HTTPS y los metadatos; los bytes no se guardan en Firestore. La app permite un único adjunto (foto o vídeo) por publicación o mensaje.

## 7. Firebase Cloud Messaging (notificaciones)

Pawly registra el token FCM del dispositivo en `users/{uid}/fcmTokens`. En `cambios46` también se incluye una Cloud Function de confianza: cuando se crea un mensaje en una conversación, busca los dispositivos del destinatario y manda un aviso. La notificación no incluye el texto privado; muestra el nombre de quien escribe y si ha enviado una foto o un vídeo. Al tocarla se abre directamente Comunidad → Mensajes.

### Desplegar las notificaciones

El envío no se activa por subir la app ni por publicar reglas. Hay que desplegar el backend una vez:

1. Instala Node.js 22 y una versión reciente de Firebase CLI.
2. En la carpeta raíz del repositorio, inicia sesión y selecciona el proyecto correcto con `firebase use`.
3. Instala las dependencias de la función:
   ```bash
   cd functions
   npm install
   cd ..
   ```
4. Comprueba otra vez el proyecto activo con `firebase use` y despliega:
   ```bash
   firebase deploy --only functions:notifyOnNewChatMessage
   ```
5. En Android, permite las notificaciones cuando Pawly lo solicite. En Android 13 o posterior, si se denegaron, actívalas también desde Ajustes del sistema → Aplicaciones → Pawly → Notificaciones.

**Plan y coste:** Cloud Functions requiere que el proyecto use el plan Blaze con una cuenta de facturación vinculada. Firebase Cloud Messaging no tiene coste por mensaje, y Cloud Functions incluye cuotas sin coste en Blaze, pero el uso por encima de las cuotas se factura. Antes de desplegar, revisa el [plan y las cuotas actuales de Firebase](https://firebase.google.com/pricing/) y configura alertas de presupuesto. El código ya está preparado, pero este cambio no modifica el plan ni despliega nada en tu proyecto.

Para probarlo, usa dos dispositivos o emuladores con cuentas distintas: deja uno fuera de Pawly o en otra pantalla, envía un mensaje desde el otro y toca la notificación. Comprueba también el permiso del sistema y que ambos usuarios hayan abierto Comunidad al menos una vez para registrar sus tokens.

## 8. Compilar y probar

Con JDK 17, Android Studio y los archivos locales preparados:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Prueba con dos cuentas distintas y verificadas:

1. Registrar una cuenta con correo y verificarlo.
2. Cerrar sesión y registrar una segunda cuenta.
3. Comprobar que cada cuenta ve su propio perfil y sus datos.
4. Publicar una foto y comprobar que aparece desde la segunda cuenta.
5. Publicar un vídeo corto MP4 y reproducirlo desde la segunda cuenta.
6. Enviar una foto y un vídeo por chat y comprobar que solo los participantes pueden leer el mensaje en Firestore.
7. Compartir una mascota con rol editor y después lector; verificar que los permisos se respetan en el servidor.
8. Comprobar que al cerrar sesión no se mezclan los datos locales de distintas cuentas.

No uses información veterinaria real durante las pruebas iniciales. Comprueba también el consumo y los límites del plan gratuito de Cloudinary desde su Dashboard.

## 9. Qué no está listo para producción

- Las cargas unsigned de Cloudinary no autentican al usuario y las URLs son públicas para quien las conozca.
- No hay backend propio que firme subidas, aplique moderación o elimine archivos de Cloudinary.
- Los avisos push entre usuarios funcionan después de desplegar `notifyOnNewChatMessage` en un proyecto Firebase con Cloud Functions habilitado.
- La solicitud de eliminación de cuenta no borra automáticamente todas las subcolecciones y archivos multimedia.
- No hay cifrado de extremo a extremo para chat ni multimedia.
- Algunas preferencias, recordatorios y estados diarios permanecen locales.
- App Check y su enforcement no deben activarse hasta integrar el SDK, probar debug/release y verificar que no bloquean a usuarios legítimos.
- Política de privacidad, términos, soporte, moderación operativa y proceso de eliminación deben completarse antes de publicar la app.

**No actives Firebase Storage para esta configuración.** Cloudinary sigue alojando las fotos y los vídeos. El envío automático de mensajes sí usa Cloud Functions; si quieres activarlo, revisa las cuotas y el presupuesto y cambia el proyecto a Blaze desde Firebase Console antes de desplegar. No se cambia el plan automáticamente.
