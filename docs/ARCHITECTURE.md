# Arquitectura de Pawly

## Objetivo

Pawly debe mantener una experiencia nativa y coherente en Android e iOS. La estrategia prevista es conservar Jetpack Compose en Android y utilizar SwiftUI en iOS, compartiendo progresivamente modelos, reglas de negocio y casos de uso mediante Kotlin Multiplatform (KMP). No se pretende compartir a la fuerza las interfaces ni las APIs específicas de cada sistema.

## Estado de partida (cambios53)

- Un único módulo Gradle Android: `:app`.
- La navegación, buena parte del estado de pantalla y varias operaciones de persistencia siguen concentradas en `PawlyApp.kt`.
- `FirebaseCommunityRepository` agrupa numerosos flujos de autenticación, perfiles y comunidad.
- Algunas pantallas combinan interfaz con APIs Android (por ejemplo, mapas, intents, ubicación o selectores de contenido).
- Ya existen modelos Kotlin sin dependencias Android, repositorios especializados y validadores puros que sirven como puntos de extracción.

## Primer límite de arquitectura (cambios54)

Se introduce `care/domain/PetCareRepository.kt`, un contrato Kotlin que expresa las operaciones de pasaporte sanitario y rutinas sin exponer Firebase, Firestore, Android `Context` ni tipos de plataforma. `FirebasePetCareRepository` pasa a ser una implementación de ese contrato.

Este cambio es deliberadamente compatible: conserva los métodos públicos y el comportamiento de la implementación Firebase. No cambia las rutas de Firestore, los nombres de colección/documento, las reglas de seguridad, el almacenamiento local ni la interfaz de usuario.

## Reglas para las siguientes extracciones

1. **Domain:** modelos, validación y casos de uso puros; sin Android, Compose, Firebase ni Cloudinary.
2. **Data:** contratos de repositorio y adaptadores concretos. Firebase/Firestore y almacenamiento local deben quedar detrás de interfaces.
3. **Presentation:** pantallas Compose, estado de pantalla y ViewModels Android. La navegación no debe contener reglas de negocio ni serialización de datos.
4. **Platform:** cámara, galería, permisos, ubicación, mapas, notificaciones, PDF y Credential Manager se implementan mediante adaptadores nativos.
5. **Compatibilidad:** mantener las claves locales existentes y las rutas/documentos actuales de Firestore. Toda migración de datos debe ser explícita, no destructiva y probada.
6. **Seguridad:** no mover credenciales ni secretos a código compartido. Cloudinary/Firebase deben conservar las reglas y la configuración por plataforma.
7. **Pruebas:** cubrir validaciones y casos de uso sin Android; probar adaptadores Firebase y flujos de plataforma por separado.

## Arquitectura objetivo

```text
Pawly
├── androidApp (:app)
│   ├── Compose UI + Navigation
│   └── Android platform adapters
├── shared (Kotlin Multiplatform, fase posterior)
│   ├── domain models
│   ├── repository contracts
│   ├── use cases + validation
│   └── shared business rules
└── iosApp (SwiftUI, fase posterior)
    └── iOS platform adapters
```

La estructura anterior es el destino, no el estado actual de cambios54. No se declara todavía un módulo KMP ni una app iOS compilable. Antes de crear `:shared` habrá que fijar versiones compatibles de Kotlin/AGP/KMP, decidir las implementaciones multiplataforma de persistencia y autenticación y preparar CI para ambos sistemas.

## Siguiente secuencia recomendada

- Extraer contratos y lógica pura de Salud y perfiles de mascota.
- Introducir ViewModels y casos de uso en Inicio, Diario y Explorar sin cambiar la UI.
- Separar autenticación, perfiles, publicaciones y mensajería del repositorio Firebase monolítico.
- Añadir pruebas de regresión y generar APK de CI en cada rama de trabajo.
- Crear el módulo KMP cuando los límites anteriores estén estables; mantener Firebase, mapas, notificaciones y multimedia detrás de adaptadores adecuados para cada plataforma.
