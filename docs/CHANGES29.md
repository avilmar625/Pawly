# Cambios29 — consolidación técnica

## Objetivo
Estabilizar la base de cambios28 antes de ampliar funcionalidades.

## Correcciones
- Sustituir referencias no disponibles a `MaterialTheme.colorScheme.primaryLight` por `MaterialTheme.colorScheme.primary` en ModerationScreen y LearningHubScreen.
- Añadir cambios29 al disparador de GitHub Actions para ejecutar validación, tests, lint, APK debug y bundle release en esta rama.

## Validación
La compilación de cambios28 detectó los dos errores anteriores en Kotlin. La validación de cambios29 queda pendiente de la ejecución de GitHub Actions. No se considera validada hasta que terminen correctamente los trabajos de compilación y empaquetado.

## Siguiente revisión
- Revisar errores de compilación adicionales que aparezcan.
- Revisar lint y warnings relevantes.
- Verificar persistencia, autenticación, permisos y comportamiento sin conexión.
- No incorporar nuevas funciones de backend hasta tener una compilación reproducible.
