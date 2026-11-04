# Pawly 🐾

**Más aventuras. Más recuerdos. Más vida juntos.**

Pawly es una aplicación Android para organizar la vida cotidiana con perros: rutinas, cuidados, ideas para salir y recuerdos compartidos.

> Estado: proyecto en desarrollo. La interfaz principal está implementada en Jetpack Compose. Parte del contenido sigue siendo demostrativo y las funciones que no estén conectadas a almacenamiento o servicios externos se indican expresamente en la aplicación.

## Creadores

- **Alex Vilarrubla Martín**
- **Maialen Cruz Tudanca**

## Funcionalidades actuales

- **Inicio:** resumen del perfil de demostración y tareas cotidianas interactivas.
- **Explorar:** búsqueda y filtros por categorías, con ideas genéricas y favoritos de demostración.
- **Salud:** panel organizativo para vacunas, desparasitación y revisiones; no ofrece pautas veterinarias.
- **Diario:** creación de recuerdos de texto durante la sesión.
- **Diseño:** interfaz Material 3, paleta natural y navegación inferior.

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
- Android SDK Platform 37 y Build Tools 37.0.0.
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

La versión actual utiliza contenido ilustrativo. Los elementos de Explorar no representan establecimientos verificados: no se consultan ubicaciones, horarios ni restricciones de acceso. Los recordatorios de Salud son ejemplos de organización, no recomendaciones veterinarias. El diario y las casillas de tareas todavía utilizan estado temporal en memoria y no deben considerarse un registro permanente.

No introduzcas información personal o médica sensible en esta versión de demostración. Antes de una publicación real habrá que incorporar persistencia segura, política de privacidad, gestión de permisos y pruebas en dispositivos.

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
