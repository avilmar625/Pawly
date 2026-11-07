# Desarrollo y pruebas Android con emulador

La forma rápida de probar Pawly durante el desarrollo es ejecutar directamente la variante `debug` desde Android Studio. No hace falta generar, descargar ni instalar un APK manualmente en cada cambio.

## 1. Crear el emulador

1. Abre Android Studio y el proyecto Pawly.
2. Ve a **Tools → Device Manager → Create Device**.
3. Elige un dispositivo tipo Pixel.
4. En la selección de imagen del sistema, elige una imagen que indique **Google Play** (no solo AOSP ni Google APIs).
5. Descárgala, termina la creación e inicia el emulador.
6. Abre Play Store en el emulador e inicia sesión con una cuenta Google de pruebas.

Para Google Sign-In es importante que el emulador tenga Google Play Services y una cuenta Google añadida. Sin cuenta disponible, Credential Manager puede devolver `NoCredentialException`.

## 2. Configuración local de Firebase

Coloca el archivo de configuración del proyecto en:

```
app/google-services.json
```

Comprueba que la aplicación registrada en Firebase usa el identificador `com.pawly.app`. Para el emulador, la firma debug habitual es la del keystore debug local. Ejecuta:

```bash
./gradlew signingReport
```

Añade la huella SHA-1 de la variante `debug` en la aplicación Android de Firebase y descarga de nuevo `google-services.json`. En Authentication activa Correo/contraseña y Google. Asegúrate de que el JSON incluye el cliente OAuth Web, que se usa como `default_web_client_id`.

## 3. Ejecutar sin generar APK manualmente

1. Selecciona la configuración **app** en la barra superior de Android Studio.
2. Selecciona el emulador iniciado como dispositivo destino.
3. Pulsa **Run ▶** (atajo habitual: Control+R en macOS puede variar según el mapa de teclas; también puedes usar el botón Run).
4. Android Studio compilará solo lo necesario, instalará la app y la abrirá.
5. Edita Kotlin/Compose y vuelve a pulsar **Run**. Para cambios de interfaz compatibles, prueba **Apply Changes**; si no se aplica correctamente, usa Run de nuevo.

No es necesario subir cambios a GitHub para probar localmente. Los cambios que quieras conservar en la rama remota sí tendrás que confirmarlos y hacer push.

## 4. Dos usuarios para probar chat y sincronización

- Usa dos emuladores distintos o un emulador y un móvil.
- Registra dos cuentas diferentes de Firebase Authentication.
- Inicia sesión con una cuenta en cada dispositivo.
- Publica desde uno y comprueba la actualización en el otro.
- Inicia una conversación y prueba texto, fotos y vídeo.

También puedes crear un segundo AVD desde Device Manager. Para que el segundo emulador sea independiente, instala Pawly y autentica una cuenta distinta.

## 5. Ver errores sin adivinar

En Android Studio abre **View → Tool Windows → Logcat**, selecciona el proceso `com.pawly.app` y filtra por `FirebaseAuth`, `Credential`, `Firestore` o `Cloudinary`. Copia el mensaje de error completo cuando una prueba falle; no compartas tokens ni secretos.

## 6. Límites del emulador

- Google Sign-In necesita una imagen Google Play y una cuenta añadida.
- Cámara, selector de fotos y permisos pueden comportarse algo distinto que en un móvil real.
- Cloudinary requiere configurar los presets en Pawly → Más → Ajustes → Servicios multimedia.
- Las notificaciones push deben probarse además con la configuración FCM correspondiente.
- Una prueba con dos cuentas en dos instancias es necesaria para validar chat en tiempo real.
