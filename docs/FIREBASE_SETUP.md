# Conectar Firebase y Cloudinary a Pawly

La rama cambios11 utiliza **Firebase Authentication + Cloud Firestore** para las cuentas y los datos sociales, y **Cloudinary** para las fotografías. No utiliza Firebase Storage ni requiere activar el plan Blaze.

## 1. Firebase Android

La aplicación Android está registrada con el identificador exacto:

~~~
com.pawly.app
~~~

Descarga el archivo actualizado google-services.json desde la configuración de esa aplicación y guárdalo localmente en:

~~~
Pawly/app/google-services.json
~~~

No lo subas a GitHub. El repositorio ignora tanto google-services.json en la raíz como app/google-services.json.

## 2. Authentication: correo y Google

En Firebase Console → Authentication → Sign-in method:

- Mantén activado **Email/Password**.
- Activa **Google** y guarda los cambios.
- Añade el correo de asistencia del proyecto si Firebase lo solicita.

Después de activar Google:

1. En Configuración del proyecto → General → Tus aplicaciones → aplicación Android, añade la huella **SHA-1** de la clave con la que ejecutas Pawly. Para obtener las huellas locales, ejecuta ./gradlew signingReport en la raíz del proyecto (en Windows: gradlew.bat signingReport).
2. Descarga otra vez google-services.json. El archivo debe incluir la información OAuth de Google; si se descargó antes de activar el proveedor, puede no contener el cliente web que Credential Manager necesita.
3. Sustituye el archivo local app/google-services.json por el nuevo y sincroniza Gradle.

La app usa Android Credential Manager y el ID de cliente web generado por Google Services. No introduzcas manualmente un API secret ni un OAuth client secret en el código.

## 3. Firestore (plan gratuito)

Crea Firestore Database en modo producción. Elige una región europea y despliega únicamente reglas e índices:

~~~bash
firebase deploy --only firestore:rules,firestore:indexes
~~~

No es necesario crear Firebase Storage ni desplegar reglas de Storage. La aplicación no incorpora el SDK de Firebase Storage.

## 4. Cloudinary para fotografías (plan gratuito)

Crea una cuenta en https://cloudinary.com/users/register/free y localiza el **Cloud name** en el Dashboard.

En Cloudinary Console → Settings → Upload → Upload presets:

1. Crea un preset nuevo.
2. Selecciona **Unsigned** como signing mode.
3. Restringe los formatos a JPG, PNG y WebP.
4. Establece un tamaño máximo razonable (por ejemplo, 5 MB) y, si está disponible, limita transformaciones y carpetas.
5. Guarda el nombre del preset.

La app comprime las imágenes antes de enviarlas y hace una carga unsigned al endpoint de imágenes. **No añadas el API Secret de Cloudinary a Android**: solo se usan el Cloud name y el nombre del preset unsigned. Ten en cuenta que el preset unsigned puede ser usado por terceros que lo conozcan; limita el preset y revisa el consumo y los límites de tu plan.

Añade estas dos líneas a tu archivo local local.properties (en la raíz del proyecto, junto a settings.gradle.kts):

~~~properties
CLOUDINARY_CLOUD_NAME=tu_cloud_name
CLOUDINARY_UPLOAD_PRESET=tu_preset_unsigned
~~~

No las pongas en gradle.properties ni subas local.properties a Git. Sin estos valores, el resto de la comunidad funciona, pero la subida de fotos mostrará un mensaje de configuración.

## 5. Compilar

Con app/google-services.json en su ubicación local y las propiedades Cloudinary configuradas:

~~~bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
~~~

En Windows:

~~~bat
gradlew.bat testDebugUnitTest
gradlew.bat assembleDebug
~~~

El plugin Google Services solo se aplica cuando existe app/google-services.json. El código puede compilar en CI sin ese archivo; el acceso con Google necesita el archivo actualizado y las huellas OAuth correctas en Firebase.

## Funciones sociales

- Registro e inicio de sesión con correo y contraseña.
- Inicio de sesión y registro con Google mediante Credential Manager.
- Creación automática de perfil Pawly y nombre de usuario al entrar por primera vez con Google.
- Perfiles públicos y perfiles de perros.
- Seguir y dejar de seguir cuentas.
- Publicaciones, likes y comentarios.
- Mensajería en tiempo real.
- Bloqueos, denuncias y herramientas de moderación.
- Fotografías de perfil, perros y publicaciones alojadas en Cloudinary; sus URLs se guardan en Firestore.

## Seguridad y límites

- Firebase Authentication y las reglas de Firestore siguen siendo la autoridad para las operaciones sociales.
- Las imágenes se cargan con un preset unsigned de Cloudinary. El preset no es un secreto fuerte; configura límites estrictos de formato/tamaño y controla el consumo.
- No se almacena el API Secret de Cloudinary en el cliente.
- El plan gratuito de Firestore tiene cuotas y límites; revisa el uso en Firebase Console.
- Antes de producción, añade App Check, protección antiabuso, términos y política de privacidad, proceso de eliminación/exportación de cuenta y pruebas de reglas con Firebase Emulator Suite.
- El panel de moderación requiere el custom claim admin: true, que solo debe asignarse desde un entorno de confianza mediante Firebase Admin SDK.
