# Fase 4A — Calidad y experiencia de uso

Rama: `cambios80-fase4a-calidad-ux`  
Base: `cambios79-fase3-bienestar-market`

## Cambios incluidos

### Inicio y cuidados
- Se elimina la generación visual de un paseo y una medicación por defecto cuando la mascota no tiene rutinas configuradas.
- El resumen de hoy cuenta exclusivamente las tareas reales configuradas y vencidas para la fecha actual.
- Si no hay rutinas, se muestra un estado vacío explicativo en lugar de presentar cuidados ficticios.
- El acceso rápido a Paseo abre la agenda cuando no existe una rutina de paseo que registrar; no alterna un estado booleano genérico.
- El registro rápido de comida reutiliza la validación de medicación/tarea configurada cuando existe una tarea de comida.

### Acceso urgente
- «He perdido a mi mascota» pasa a la primera posición del apartado Más, bajo una sección de ayuda urgente.

### Ajustes y configuración
- Los campos técnicos de Cloudinary en Ajustes solo se muestran en compilaciones de depuración (`BuildConfig.DEBUG`). Se mantienen disponibles para desarrollo, pero no se exponen al usuario final.

### Comunidad y diario
- La pantalla de configuración de Firebase deja de mostrar instrucciones internas de despliegue en compilaciones de producción.
- El estado de instalación sin Firebase usa un mensaje orientado al usuario, sin mencionar rutas de archivos o tareas de configuración.
- El error de subida de fotografías del diario se expresa como un problema recuperable de conexión/servicio.

### Pasaporte sanitario
- Se sustituye el campo de texto libre `AAAA-MM-DD` por un selector de fecha nativo.
- El valor continúa almacenándose en formato ISO `yyyy-MM-dd`, por lo que no cambia el formato persistido ni la compatibilidad de los registros existentes.

## Validaciones pendientes / límites conocidos
- La rama debe superar el workflow Android antes de considerar verificada la compilación. Los primeros workflows iniciados durante los commits consecutivos fueron cancelados por ejecuciones posteriores; no se consideran una validación satisfactoria.
- Es necesaria una prueba manual en Android para revisar el selector de fecha, el estado vacío de Inicio, el acceso urgente y el comportamiento de ajustes en build release/debug.
- iOS no se modifica en esta fase: los cambios descritos corresponden a Kotlin/Jetpack Compose.
- El registro de paseo sin rutina configurada dirige a la agenda para crear una rutina real; todavía no incorpora cronómetro, duración ni trazado GPS. Eso pertenece a una fase posterior.

## Criterios de aceptación manual
1. Mascota sin rutinas: Inicio no muestra paseo ni medicación inventados y presenta un estado vacío.
2. Mascota con rutinas: el contador solo incluye tareas que corresponden al día y a su programación.
3. Acceso rápido Paseo sin rutina: abre la agenda y no marca/desmarca un estado.
4. Acceso rápido Paseo con rutina: registra la tarea mediante el flujo de validación existente.
5. Más: la opción de mascota perdida aparece antes de Market y servicios profesionales.
6. Ajustes release: no aparece el formulario técnico de Cloudinary. Ajustes debug: sigue disponible.
7. Pasaporte: la fecha se elige mediante calendario y se guarda como fecha ISO.
8. Comunidad sin Firebase: no se muestran rutas de archivos ni instrucciones de despliegue.
