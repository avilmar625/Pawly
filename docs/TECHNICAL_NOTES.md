# Notas técnicas

## Configuración actual

- Application ID: `com.pawly.app`
- Min SDK: 26 (Android 8.0)
- Compile SDK: 37 Preview · Target SDK: 36
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

La comunidad es una función multiusuario real: requiere autenticación, base de datos remota, reglas de autorización por usuario, sincronización, alojamiento externo de imágenes, moderación, bloqueo y denuncia, protección de datos y políticas de retención.

## Configuración externa pendiente

No se incluyen claves API ni secretos. Para mapas, autenticación o servicios externos habrá que configurar credenciales de forma segura y documentar su uso. Nunca subir claves privadas al repositorio.


## Comunidad Firebase + Cloudinary (cambios11)

- `community/CommunityModels.kt`: modelos de usuario, perro, publicación, comentario, conversación y mensaje.
- `community/FirebaseCommunityRepository.kt`: Firebase Authentication y Cloud Firestore.
- `community/CloudinaryImageUploader.kt`: compresión JPEG y carga unsigned de imágenes a Cloudinary.
- `community/CommunityScreen.kt`: autenticación por correo y Google Credential Manager, muro, búsqueda, perfiles, chat, comentarios, denuncias y permiso opcional de notificaciones.
- `community/MyMessagingService.kt`: recepción FCM, notificaciones en primer plano y almacenamiento privado del token por usuario.
- `firestore.rules` y `firestore.indexes.json`: reglas e índices de Firestore.
- `firebase.json`: despliegue solo de Firestore; Firebase Storage no se utiliza.

La app necesita `app/google-services.json` local y valores de Cloudinary en `local.properties`. El token FCM se guarda en `users/{uid}/fcmTokens`; las reglas de Firestore permiten el acceso solo al propietario. El envío automático de avisos requiere un backend de confianza y no se realiza desde el cliente. Las denuncias quedan en una cola Firestore reservada a administradores con custom claim. La app incorpora un panel de moderación para revisar avisos, eliminar publicaciones y restringir perfiles. Los administradores deben asignarse mediante un entorno de confianza.

