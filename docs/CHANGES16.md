# Cambios16 — pasaporte y cuidados

## Objetivo

Dar un paso desde una aplicación centrada en funciones separadas hacia un espacio organizado alrededor de cada mascota. La interfaz de Salud incorpora un pequeño centro de cuidados con agenda, pasaporte sanitario y rutinas.

## Implementado en esta rama

### Pasaporte sanitario local
- Registro de vacunas, desparasitación, medicación, visitas veterinarias, alergias, intervenciones, peso y notas.
- Fecha validada con formato ISO (AAAA-MM-DD).
- Campo de detalle para clínica, observaciones o información adicional.
- Edición básica mediante alta y eliminación de registros.
- Persistencia en SharedPreferences, con claves separadas por nombre de mascota.
- Estado vacío y aviso claro de que el historial no sustituye la cartilla oficial ni el historial de la clínica.

### Organización de cuidados
- Crear rutinas como paseos, comidas, medicación o cuidados puntuales.
- Asignar un nombre de responsable y una frecuencia.
- Marcar y desmarcar tareas completadas.
- Persistencia local por mascota.

### Integración visual
- La pestaña Salud presenta tres vistas: Agenda, Pasaporte y Cuidados.
- La agenda previa se conserva como primera vista.
- Se reutiliza Material 3 y los componentes visuales existentes.

## Límites deliberados

- Los datos del pasaporte y las rutinas solo están en el dispositivo. No se sincronizan con Firebase ni con otros móviles.
- No se adjuntan fotografías ni PDF, no se importan cartillas y no existe exportación veterinaria.
- La asignación de responsable es una anotación organizativa local; no invita ni concede acceso a otra persona.
- No se reinician automáticamente las rutinas según su frecuencia: marcar una tarea es un estado manual.
- Los perfiles locales de Inicio siguen siendo mayoritariamente caninos. La selección de especie de Comunidad no convierte por sí sola toda la aplicación en multi-especie.
- No hay validación clínica de los datos introducidos. La aplicación no recomienda vacunas, tratamientos ni dosis.

## Trabajo recomendado para la siguiente iteración

1. Unificar el modelo local de mascota (ID estable, nombre, especie, foto y datos básicos) y migrar sin pérdida los perfiles existentes.
2. Pasar el pasaporte a Room con repositorios y ViewModels; usar IDs estables en vez del nombre para evitar colisiones si dos mascotas comparten nombre.
3. Añadir adjuntos privados con límites de tamaño, cifrado/controles de acceso y política de retención.
4. Generar un PDF de resumen veterinario con fecha de exportación y origen de los datos.
5. Implementar el cuidado compartido en Firebase: propietario, cuidador y solo lectura; invitación segura, aceptación, revocación y registro de actividad.
6. Evitar duplicidades de medicación mediante confirmación de dosis con hora, persona y zona horaria; no sugerir dosis.
7. Completar multi-especie en Inicio, Salud, Aprender, Diario y Explorar, con campos y guías adecuados por especie.
8. Añadir pruebas de migración, persistencia, validación de fechas y reglas de acceso.

## Verificación

La rama se ha preparado mediante cambios en GitHub. No se ha ejecutado una compilación Android Studio/Gradle en un entorno con las dependencias y configuración del proyecto, por lo que **no se afirma que compile todavía**. Antes de darla por buena, abrir en Android Studio y ejecutar:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

También probar en un dispositivo: cambio entre Agenda/Pasaporte/Cuidados, crear y borrar registros, reiniciar la app y comprobar persistencia para dos mascotas con nombres distintos.
