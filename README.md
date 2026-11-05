# Pawly 🐾

**Más aventuras. Más recuerdos. Más vida juntos.**

Pawly es una aplicación Android para organizar la vida cotidiana con perros: rutinas, cuidados, ideas para salir y recuerdos compartidos.

> Estado: proyecto en desarrollo. La interfaz principal está implementada en Jetpack Compose. Parte del contenido sigue siendo demostrativo y las funciones que no estén conectadas a almacenamiento o servicios externos se indican expresamente en la aplicación.

## Creadores

- **Alex Vilarrubla Martín**
- **Maialen Cruz Tudanca**

## Funcionalidades actuales

- **Inicio:** selector para varios perros, perfil editable con foto de cámara o galería, tareas cotidianas y resumen actualizado.
- **Explorar:** búsqueda y filtros por categorías, con ideas genéricas y favoritos guardados en el dispositivo.
- **Salud:** fechas locales para vacunas, desparasitación y revisiones, editables y separadas por perfil; no ofrece pautas veterinarias ni notificaciones automáticas.
- **Diario:** recuerdos de texto persistentes, con recuento actualizado.
- **Persistencia local:** perfil, tareas, favoritos y diario se conservan entre sesiones en el dispositivo; no hay sincronización ni cuenta.

## Organización del código

La aplicación se está reorganizando por responsabilidades para facilitar su mantenimiento y la incorporación futura de funciones sociales:

- `MainActivity.kt`: punto de entrada Android.
- `PawlyApp.kt`: estado de aplicación y navegación principal.
- `ui/home`: pantalla de inicio y componentes visuales compartidos.
- `ui/explore`: exploración, favoritos y ubicación.
- `ui/health`: agenda de cuidados.
- `ui/diary`: diario.
- `ui/profile`: diálogos y edición de perfiles.

Esta separación es un primer paso de refactorización; la lógica de estado y persistencia todavía está en la capa de presentación y será extraída a ViewModels y repositorios en una fase posterior.

## Comunidad (rama cambios10)

La rama `cambios10` incorpora una comunidad conectable a Firebase: registro/inicio de sesión, perfiles de personas y perros, seguimiento, publicaciones con fotos, likes, comentarios, conversaciones en tiempo real, bloqueo y denuncias. Para activarla se necesita configurar un proyecto Firebase. Consulta [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md). Sin `app/google-services.json`, la comunidad muestra instrucciones de configuración y las funciones locales siguen disponibles.

## Tecnología

- Kotlin y Android nativo.
- Jetpack Compose y Material 3.
- Navigation Compose.
- Gradle Kotlin DSL.
- Java 17.
- Android Gradle Plugin 9.4.0 y Kotlin Compose plugin 2.4.21.

## Requisitos

- Android Studio compatible con Android Gradle Plugin 9.4.0.
- JDK 17.
- Android SDK Platform 36 y Build Tools 36.0.0.
- Android 8.0 (API 26) o superior para ejecutar la aplicación.

## Abrir y compilar

1. Clona el repositorio y abre la carpeta raíz en Android Studio.
2. En **Settings → Build, Execution, Deployment → Build Tools → Gradle**, selecciona **Gradle Wrapper** y JDK 17.
3. Sincroniza el proyecto y espera a que se descarguen Gradle y las dependencias.
4. Selecciona la configuración `app` y ejecuta en un emulador o dispositivo.

En macOS/Linux:

```bash
chmod +x gradlew
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

En Windows:

```bat
gradlew.bat testDebugUnitTest
gradlew.bat assembleDebug
```

El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## Transparencia y privacidad

Los elementos de Explorar son ideas genéricas y no representan establecimientos verificados: no se consultan ubicaciones, horarios ni restricciones de acceso. Los recordatorios de Salud son ejemplos de organización, no recomendaciones veterinarias. La agenda de cuidados todavía no permite registrar fechas y no envía notificaciones. Los datos de perfiles, tareas, favoritos, agenda de salud y diario se guardan localmente en el almacenamiento privado de la app; no se sincronizan y se excluyen de copias automáticas del sistema.

No introduzcas información médica sensible. Antes de una publicación real habrá que completar la política de privacidad, validar el comportamiento en dispositivos y definir una estrategia de exportación o recuperación de datos, ya que actualmente no hay sincronización ni copia de seguridad.

## Próximas mejoras

1. Persistencia local y perfiles editables para varios perros.
2. Fotografías, galería y diario permanente.
3. Agenda con recordatorios y notificaciones configurables.
4. Mapas y lugares reales con fuentes y fecha de verificación.
5. Pruebas automatizadas, accesibilidad, traducciones y preparación de publicación.

## Principios de producto

- Priorizar la seguridad y el bienestar animal.
- No presentar como verificados datos que no lo estén.
- Explicar el origen y la fecha de actualización de la información.
- Recopilar únicamente los datos necesarios y dar control al usuario.
- No sustituir el criterio veterinario ni los servicios de emergencia.

---

© 2026 Alex Vilarrubla Martín y Maialen Cruz Tudanca. Todos los derechos reservados.
