# Notas técnicas

## Configuración actual

- Application ID: `com.pawly.app`
- Min SDK: 26 (Android 8.0)
- Target / Compile SDK: 35
- JDK: 17
- Android Gradle Plugin: 8.7.3
- Kotlin: 2.0.21
- Gradle recomendado: 8.9

## Estructura

- `MainActivity.kt`: punto de entrada, tema Compose, navegación y pantallas iniciales.
- `app/build.gradle.kts`: configuración de dependencias.
- `app/src/main/res`: manifiesto, recursos y tema Android.

La primera entrega concentra la interfaz en un único archivo para facilitar la revisión inicial. Antes de añadir persistencia y funciones de producción, conviene separar en paquetes `core/designsystem`, `feature/home`, `feature/explore`, `feature/health`, `feature/diary`, `data` y `domain`.

## Próxima refactorización

1. Extraer modelos y estado a ViewModels.
2. Añadir repositorios e interfaces de dominio.
3. Implementar Room y migraciones.
4. Añadir pruebas unitarias y pruebas Compose.
5. Inyectar dependencias (Hilt o Koin) cuando la complejidad lo justifique.

## Configuración externa pendiente

No se incluyen claves API ni secretos. Para mapa, autenticación, analítica o servicios externos habrá que configurar credenciales de forma segura y documentar su uso. Nunca subir claves privadas al repositorio.