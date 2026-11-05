# Pawly para iPhone

Esta carpeta contiene el inicio de la versión iOS nativa de Pawly. Se mantiene separada de la aplicación Android: no modifica ni sustituye `app/`.

## Tecnología y alcance inicial

- SwiftUI, con interfaz nativa de iOS.
- iOS 17 como versión mínima inicial.
- XcodeGen para generar el proyecto Xcode desde `project.yml`.
- Firebase Apple SDK preparado como dependencia (Auth y Firestore); requiere añadir el archivo de configuración iOS.
- La primera iteración incluye navegación y prototipos locales de Inicio, Explorar, Salud y Diario.
- La comunidad, sincronización de datos, subida de fotos y notificaciones se integrarán después; no se consideran funcionales todavía en iOS.

## Preparar el Mac

1. Instala/actualiza Xcode desde App Store y ábrelo una vez para aceptar sus componentes.
2. Instala XcodeGen. Si tienes Homebrew: `brew install xcodegen`. Si no, instala XcodeGen siguiendo su documentación oficial.
3. Clona el repositorio y cambia a la rama `ios-iphone`.
4. Desde la carpeta del repositorio ejecuta:

   ```bash
   cd iosApp
   xcodegen generate
   open Pawly.xcodeproj
   ```

5. En Xcode, selecciona el proyecto Pawly y el target Pawly. En **Signing & Capabilities**, selecciona tu Apple ID en **Team** y deja un Bundle Identifier único (por defecto `com.pawly.ios`).
6. Conecta el iPhone por cable, acepta la confianza en ambos dispositivos y activa **Ajustes > Privacidad y seguridad > Modo de desarrollador** si iOS lo solicita.
7. Selecciona el iPhone como destino y pulsa **Run**.

## Firebase (siguiente fase)

En Firebase Console, añade una aplicación iOS al mismo proyecto Firebase que usa Android. Usa exactamente el Bundle Identifier configurado en Xcode. Descarga `GoogleService-Info.plist` y añádelo al grupo/target Pawly en Xcode. No subas ese archivo al repositorio público.

El SDK de Firebase ya está declarado en `project.yml`, pero las pantallas iOS todavía no implementan el inicio de sesión ni la lectura/escritura de Firestore.

## Instalación personal

Para probarla en tu propio iPhone basta inicialmente con Xcode y tu Apple ID personal (firma gratuita). La firma gratuita tiene limitaciones de duración y distribución; no hace falta publicar en App Store para probarla en tu dispositivo. Para compartirla de forma estable o distribuirla ampliamente haría falta valorar Apple Developer Program.

## Importante

Esta versión es una base de portabilidad, no una conversión completa de Android. No se deben migrar ni borrar datos Android. El siguiente trabajo es conectar autenticación y Firestore, migrar los modelos y datos de Pawly, implementar las fotos con Cloudinary y probar las funciones en un iPhone real.
