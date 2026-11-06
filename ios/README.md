# Pawly para iOS

Esta carpeta contiene el port nativo para iPhone. La aplicación Android original permanece en `app/` y no se modifica. iOS utiliza SwiftUI y el SDK oficial de Firebase para compartir el mismo proyecto de Authentication y Firestore.

## Estado de esta primera entrega

- Proyecto iOS generado con XcodeGen.
- Inicio de sesión con correo y contraseña.
- Registro con verificación de correo.
- Recuperación de contraseña.
- Sesión persistente mediante Firebase Authentication.
- Primer diseño SwiftUI con pestañas Inicio, Comunidad, Recuerdos y Cuenta.
- Lectura inicial del perfil de mascota desde `sharedPets`.
- Workflow de compilación para simulador iOS en GitHub Actions.

La comunidad, chat, multimedia, diario completo, salud, recordatorios y perfiles compartidos todavía deben portarse. No se presentan como terminados.

## Requisitos en el Mac

- macOS compatible con la versión de Xcode instalada.
- Xcode 16 o posterior.
- Homebrew.
- XcodeGen.

Instala XcodeGen:

```bash
brew install xcodegen
```

## Configurar Firebase iOS

1. Abre Firebase Console y entra en el mismo proyecto que utiliza Pawly Android.
2. En Configuración del proyecto añade una aplicación iOS.
3. Usa el identificador de paquete exacto: `com.pawly.ios`.
4. Descarga `GoogleService-Info.plist`.
5. Coloca el archivo en `ios/Pawly/GoogleService-Info.plist`.
6. No lo subas a Git: está excluido por el `.gitignore` de la raíz.
7. Activa Email/Password en Authentication. Las cuentas y usuarios de Firebase Authentication son comunes entre Android e iOS cuando ambas apps están registradas en el mismo proyecto.

No reutilices el `google-services.json` de Android: iOS necesita su propio `GoogleService-Info.plist`.

## Generar y abrir el proyecto

Desde la raíz del repositorio:

```bash
cd ios
xcodegen generate
open Pawly.xcodeproj
```

Selecciona el simulador o tu iPhone. Para instalar en un iPhone físico necesitarás seleccionar tu equipo de desarrollo en Signing & Capabilities y confiar en el certificado de desarrollo.

También puedes compilar desde Terminal:

```bash
xcodebuild -project Pawly.xcodeproj -scheme Pawly -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## Compartir datos con Android

La app usa el mismo proyecto Firebase y conserva la colección `users` y la colección `sharedPets` para comenzar la integración. No migres ni borres datos para instalar la app iOS. Algunas funciones Android guardan información local y no aparecerán automáticamente en iOS hasta que se porte su sincronización.
