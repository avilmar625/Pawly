# Notas técnicas

## Configuración actual

- Application ID: `com.pawly.app`
- Min SDK: 26 (Android 8.0)
- Compile / Target SDK: 37
- JDK: 17
- Android Gradle Plugin: 9.4.0
- Kotlin Compose plugin: 2.4.21
- Gradle Wrapper: 9.6.0

## Estructura actual

- `MainActivity.kt`: punto de entrada Android, sin lógica de pantallas.
- `PawlyApp.kt`: estado global de interfaz, navegación y persistencia local actual.
- `ui/home/HomeScreen.kt`: inicio y componentes visuales reutilizables.
- `ui/explore/ExploreScreen.kt`: catálogo ilustrativo, favoritos y búsqueda de ubicación.
- `ui/health/HealthScreen.kt`: agenda de cuidados.
- `ui/diary/DiaryScreen.kt`: diario local.
- `ui/profile/ProfileDialogs.kt`: edición y selección de perfiles.

Los archivos comparten temporalmente el paquete Kotlin `com.pawly.app` para evitar cambios innecesarios de visibilidad durante esta primera extracción. La estructura de carpetas ya separa las responsabilidades visuales; los siguientes pasos deben introducir paquetes de dominio y datos.

## Arquitectura objetivo

1. Extraer modelos de dominio (perros, tareas, cuidados, recuerdos y favoritos).
2. Mover el estado de cada funcionalidad a ViewModels.
3. Crear repositorios e interfaces para aislar almacenamiento y servicios.
4. Migrar SharedPreferences/JSON a Room con migraciones y pruebas.
5. Añadir pruebas unitarias y pruebas Compose.
6. Incorporar inyección de dependencias cuando la complejidad lo justifique.

## Comunidad: decisión de producto

La comunidad (cuentas, seguimiento, publicaciones, comentarios y mensajería) debe construirse después de estabilizar las capas de dominio y datos. Es una función multiusuario real: requiere autenticación, base de datos remota, reglas de autorización por usuario, sincronización, almacenamiento de imágenes, moderación, bloqueo y denuncia, protección de datos y políticas de retención. No debe simularse con datos locales ni presentarse como chat real sin backend.

## Configuración externa pendiente

No se incluyen claves API ni secretos. Para mapas, autenticación o servicios externos habrá que configurar credenciales de forma segura y documentar su uso. Nunca subir claves privadas al repositorio.


## Comunidad Firebase (cambios10)

- `community/CommunityModels.kt`: modelos de usuario, perro, publicación, comentario, conversación y mensaje.
- `community/FirebaseCommunityRepository.kt`: acceso a Firebase Authentication, Firestore y Storage.
- `community/CommunityScreen.kt`: autenticación, muro, búsqueda, perfiles, chat, comentarios y denuncias.
- `firestore.rules` y `storage.rules`: reglas de acceso; `firestore.indexes.json` declara el índice de búsqueda.
- `firebase.json`: configuración de despliegue con Firebase CLI.

La app no incluye credenciales Firebase. La comunidad se habilita al añadir `app/google-services.json` localmente. Las denuncias quedan en una cola Firestore reservada a administradores con custom claim; falta construir un panel de moderación y asignar administradores mediante un entorno de confianza.

