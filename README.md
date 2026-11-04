# Pawly 🐾

**Más aventuras. Más recuerdos. Más vida juntos.**

Pawly es una aplicación Android pensada para acompañar a las personas que comparten su vida con perros. Reúne inspiración para salir, organización de cuidados y recuerdos en una experiencia sencilla, cálida y visual.

> Estado: primera versión de producto en desarrollo. La interfaz y los flujos principales ya están implementados; algunas funciones todavía usan contenido de demostración y almacenamiento temporal.

## Creadores

- **Alex Vilarrubla Martín**
- **Maialen Cruz Tudanca**

## Qué incluye esta primera versión

- **Inicio:** resumen del compañero, pequeñas métricas y rutina diaria interactiva.
- **Explorar:** categorías de naturaleza, playas, restauración y alojamientos; búsqueda, filtros y favoritos.
- **Salud:** espacio para organizar vacunación, desparasitación, revisiones y recordatorios.
- **Diario:** creación de recuerdos en texto durante la sesión.
- **Identidad visual:** paleta natural, componentes Material 3 personalizados y navegación inferior.

## Tecnología

- Kotlin
- Android nativo
- Jetpack Compose + Material 3
- Navigation Compose
- Gradle Kotlin DSL
- Arquitectura preparada para evolucionar hacia MVVM, repositorios y persistencia local.

## Abrir el proyecto

1. Instala Android Studio actualizado.
2. Abre la carpeta raíz del repositorio.
3. Espera a que Android Studio sincronice Gradle.
4. Usa JDK 17 y Android SDK Platform 35.
5. Ejecuta la configuración **app** en un emulador o dispositivo Android (Android 8.0 / API 26 o superior).

También puedes compilar desde un entorno con Gradle 8.9 y Android SDK:

```bash
gradle assembleDebug
```

El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## Transparencia sobre los datos

La versión actual utiliza contenido ilustrativo. Los lugares que aparecen en Explorar **no son establecimientos reales verificados**, no hay todavía un mapa ni geolocalización, y no se consultan horarios ni restricciones de acceso. Los recordatorios de salud son ejemplos organizativos, no recomendaciones veterinarias. Los cambios del diario y las casillas se guardan únicamente en memoria durante la sesión.

No introduzcas información personal o médica real en esta versión de demostración.

## Próximas etapas

1. Persistencia real con Room y gestión de perfiles de varios perros.
2. Fotos y permisos de galería/cámara, diario persistente y exportación.
3. Mapa y geolocalización con lugares reales, fuentes y fecha de verificación.
4. Agenda y notificaciones locales configurables.
5. Cuentas y sincronización opcional en la nube.
6. Moderación, reportes y herramientas de calidad para contenido comunitario.
7. Accesibilidad, traducciones, pruebas automatizadas y preparación de publicación.

## Principios de producto

- La seguridad y el bienestar animal van primero.
- No presentar como verificado un lugar, horario o permiso que no lo esté.
- Explicar de dónde viene la información y cuándo se comprobó.
- Privacidad por diseño: recopilar únicamente lo necesario y ofrecer control al usuario.
- No sustituir el criterio veterinario ni los servicios de emergencia.

---

© 2026 Alex Vilarrubla Martín y Maialen Cruz Tudanca. Todos los derechos reservados.