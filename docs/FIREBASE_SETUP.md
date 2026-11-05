# Conectar Firebase a Pawly

La rama `cambios10` incorpora una comunidad real basada en Firebase. Sin la configuración de un proyecto Firebase, la aplicación conserva sus funciones locales y muestra una pantalla que explica que la comunidad aún no está conectada.

## 1. Crear el proyecto

1. Abre la [consola de Firebase](https://console.firebase.google.com/).
2. Crea un proyecto nuevo (por ejemplo, `pawly-production`).
3. Añade una aplicación Android con el identificador exacto: `com.pawly.app`.
4. Descarga `google-services.json` y colócalo en `app/google-services.json`.

El archivo está incluido en `.gitignore`: no lo subas al repositorio público. El identificador de aplicación que contiene no es una contraseña, pero mantener la configuración fuera de Git evita filtrar detalles de entornos y facilita separar desarrollo y producción.

## 2. Activar autenticación

En Firebase Console → Authentication → Sign-in method, activa **Email/Password**. No actives proveedores que la aplicación todavía no implementa.

## 3. Crear Firestore

En Firestore Database, crea la base de datos en modo producción. Desde la raíz del repositorio, con Firebase CLI instalado y autenticado, despliega reglas e índices:

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

Los ficheros de configuración son `firestore.rules` y `firestore.indexes.json`.

## 4. Activar Storage

En Firebase Console → Storage, crea el bucket y después despliega las reglas:

```bash
firebase deploy --only storage
```

Las imágenes están limitadas por las reglas a archivos de imagen de menos de 10 MB y a rutas asociadas al UID del usuario autenticado. Comprueba el plan y los precios vigentes de Firebase Storage en tu proyecto antes de distribuir la app; configura alertas de presupuesto y cuotas.

## 5. Compilar

Con el archivo de configuración en `app/google-services.json`, abre el proyecto en Android Studio y sincroniza Gradle. El plugin Google Services se aplica automáticamente solo cuando ese archivo existe.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Para CI, no es necesario subir el archivo al repositorio: el workflow compila el código con Firebase SDK incluido, y la pantalla de comunidad indica que falta conectar Firebase. Para probar funciones sociales en CI haría falta un entorno de pruebas Firebase y secretos gestionados en GitHub Actions.

## Funciones sociales incluidas

- Registro e inicio de sesión con correo y contraseña.
- Reserva transaccional de nombres de usuario únicos.
- Perfil público con nombre, biografía, privacidad y foto.
- Perfiles de perros con nombre, raza, edad, descripción, características y foto.
- Búsqueda de usuarios, seguir y dejar de seguir.
- Muro en tiempo real con publicaciones de texto/foto, likes y comentarios.
- Bandeja de conversaciones y mensajería en tiempo real.
- Bloqueo de cuentas y filtrado de sus publicaciones en el muro propio.
- Denuncias de perfiles y publicaciones enviadas a la colección `reports`.

## Moderación y puesta en producción

Las denuncias se almacenan en Firestore y solo pueden leerse/gestionarse desde clientes con el custom claim `admin: true`. La app incluye una pestaña **Moderar** visible únicamente para administradores: permite revisar avisos, retirar publicaciones denunciadas y restringir perfiles. La asignación del custom claim debe hacerse desde un entorno de confianza usando Firebase Admin SDK; nunca desde el cliente Android.

Antes de abrir el registro al público:
- La app envía verificación de correo al registrarse y ofrece recuperación de contraseña. Antes de producción, comprueba plantillas, entregabilidad y límites de correo.
- Añade App Check y protección contra abuso/rate limiting.
- Prepara un proceso humano de revisión de denuncias y respuesta a incidentes.
- Define términos de uso, política de privacidad, edad mínima y proceso de eliminación/exportación de cuenta.
- Prueba las reglas con Firebase Emulator Suite y casos de acceso cruzado entre usuarios.
- Revisa el coste de Firestore/Storage, límites, cuotas y alertas.
- No publiques el APK como producto final hasta completar estas tareas.

## Alcance de privacidad actual

El ajuste de perfil público controla la aparición en búsquedas y el acceso al perfil público. Las cuentas no verificadas pueden explorar, pero no publicar, seguir, comentar, enviar mensajes ni subir fotografías. El muro y los comentarios son visibles para usuarios autenticados de Pawly. Las publicaciones no deben incluir direcciones, datos de contacto privados ni información veterinaria sensible. El bloqueo filtra el contenido del usuario bloqueado en el muro propio y evita iniciar/continuar relaciones y conversaciones entre ambas cuentas.

El cliente Android no es una frontera de seguridad: las reglas de Firestore y Storage son la autoridad. Revísalas y pruébalas antes de producción.
