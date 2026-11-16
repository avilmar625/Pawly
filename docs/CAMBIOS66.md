# cambios66 — Estabilización y fidelidad visual

**Fecha:** 16 de noviembre de 2026  
**Rama:** `cambios66-estabilizacion-y-lint`  
**Base:** `cambios65-hoy-cuidados-diseno` (HEAD base `ad5e83e79a9a52baf3068f85fe1c45b78c58e4af`)  
**HEAD documentado antes de esta actualización documental:** `297d933085c5ecbd26ec8d6b8432a8da413636e2`

## Objetivo

Continuar desde la rama más avanzada (cambios65), corregir el bloqueo de CI y rematar la navegación inferior de acuerdo con el PDF de referencia sin retirar funcionalidades.

## Cambios de código

1. **Localización observable en Hoy.** `HomeCareTasksSection.kt` reutiliza `PawlyI18n.locale(language)` en la cabecera, fecha de próxima cita y horas de actividad. Sustituye cuatro lecturas de `Locale.getDefault()` que Android Lint detectó dentro de un composable.
2. **Diagnóstico CI completo.** El paso de Android Lint imprime el informe `lint-results-debug.txt` completo al fallar. No se rebaja Lint ni se crea una baseline para ocultar los errores.
3. **Navegación inferior.** Se alinea el orden con el PDF: Hoy, Mi perro, Comunidad, Explorar y Más. Diario y Calendario continúan agrupados visualmente bajo Mi perro; Aprender, Profesionales y Ayuda siguen accesibles bajo Más.

## Diseño confirmado frente al PDF

- Fondo crema, superficies cálidas, verde bosque, salvia y coral ya estaban en `PawlyTheme.kt`; se mantienen.
- Cabecera compacta de Hoy con avatar de mascota real (foto local/URL HTTPS o inicial).
- Progreso y tareas basados en registros existentes, próxima cita y accesos rápidos conectados a flujos existentes.
- No se reemplaza contenido real por datos ficticios.

## Estado de validación

- En cambios65, tests Kotlin pasaron; `lintDebug` falló con 8 errores, 72 warnings y 3 hints. El primer error fue `NonObservableLocale`; APK Debug y AAB Release quedaron omitidos.
- En cambios66, Firestore Emulator pasó en el commit `cb8e9ae758e8a3701fc815126434c15369f978bb`. La ejecución no corresponde al HEAD posterior de navegación.
- Para el HEAD `297d933...`, una nueva validación Firestore y un Android build se lanzaron. El estado debe consultarse en Actions; no se declara compilación correcta ni APK/AAB generado hasta que el workflow finalice.
- No se ha probado todavía en Android físico/emulador, iPhone, ni con credenciales reales de Firebase/Google/Cloudinary.

## Funcionalidad que se debe preservar

Eventos, lugares, filtros y mapa OSM, colecciones, búsqueda/llamada de teléfonos, directorio profesional, avisos de mascota perdida, perfiles multi-especie, Comunidad, chat, bloqueos/denuncias, Salud, pasaporte, cuidados compartidos, Diario, calendario, Aprender, PDF y backup/restauración. Esta rama no autoriza retirar ninguna de esas áreas.

## Próximos pasos

1. Revisar todos los diagnósticos del informe Lint de cambios66 y corregirlos.
2. Obtener tests, Lint, APK Debug y AAB Release verdes sobre el mismo HEAD.
3. Probar manualmente autenticación, selección/persistencia de mascotas, Cloudinary, cuidados compartidos, recordatorios, eventos, lugares y exportaciones.
4. Auditar por separado la rama iOS `ios-funciones` en Xcode/iPhone.
5. Mantener servicios gratuitos; no activar Blaze/Workers Paid ni subir secretos.
