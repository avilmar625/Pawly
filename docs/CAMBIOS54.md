# cambios54 · Primer desacoplamiento de la capa de datos

## Objetivo

Iniciar la preparación de Pawly para Android e iOS sin introducir una reescritura ni alterar la experiencia actual.

## Cambios incluidos

- Añadido `care/domain/PetCareRepository.kt`, contrato Kotlin para la persistencia del pasaporte sanitario y las rutinas.
- `FirebasePetCareRepository` implementa el contrato. La implementación sigue usando Firebase Authentication y Firestore, pero esos tipos no forman parte de la API de dominio.
- Añadido [docs/ARCHITECTURE.md](ARCHITECTURE.md), que describe las capas actuales, las reglas de extracción y la arquitectura objetivo Android + KMP compartido + iOS SwiftUI.
- Actualizado el README para dejar claro qué se ha separado y qué sigue pendiente.

## Compatibilidad y datos

No se han modificado las pantallas, la navegación, los nombres de colecciones/documentos de Firestore, las reglas, las claves de SharedPreferences ni los formatos de los registros locales. No se requiere desplegar reglas ni ejecutar migraciones de datos por estos cambios.

## Alcance que todavía no se ha abordado

- Extracción de ViewModels desde `PawlyApp.kt`.
- División de `FirebaseCommunityRepository`.
- Migración de modelos y casos de uso a un módulo KMP real.
- Implementaciones nativas iOS de autenticación, almacenamiento, multimedia, mapas y notificaciones.

## Validación

El cambio conserva las firmas de las operaciones existentes y añade la implementación explícita del contrato. La compilación, los tests, Android Lint y la generación del APK/AAB deben confirmarse en GitHub Actions antes de considerar la rama validada. No se debe interpretar la existencia del código o del workflow como una compilación satisfactoria.
