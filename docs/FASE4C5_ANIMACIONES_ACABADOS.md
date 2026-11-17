# Fase 4C.5 — Animaciones y acabados

## Cambios aplicados

- **Transición entre secciones:** el contenido principal cambia mediante un fundido breve (180 ms de entrada y 120 ms de salida). Se mantiene la navegación y el estado de cada sección; no se modifica la lógica funcional de los destinos.
- **Cabeceras compartidas:** los cambios de tamaño del encabezado usan `animateContentSize` con una duración corta, evitando saltos bruscos cuando cambia el contenido.
- **Acabado de carga:** se conserva el componente compartido de skeleton shimmer para contenido realmente asíncrono, sin añadir animaciones decorativas permanentes a las pantallas.
- **Coherencia:** se reutilizan Material 3, las formas, colores y tipografía de Pawly; los controles conservan las interacciones y feedback estándar del sistema.

## Decisiones de rendimiento y accesibilidad

- Animaciones breves y limitadas a transiciones de contenido; no se añaden partículas, parallax ni bucles decorativos globales.
- No se retrasa la respuesta a pulsaciones ni se bloquean acciones mientras se ejecutan transiciones.
- Los skeletons deben mostrarse solo mientras exista una carga real, no como contenido de relleno.
- La animación no sustituye los estados accesibles de foco, selección o confirmación.

## Validación pendiente

No se ha ejecutado una compilación ni pruebas instrumentadas en esta sesión. Antes de dar la fase por verificada, comprobar:
1. Cambiar repetidamente entre Inicio, Salud, Comunidad, Explorar y secciones secundarias.
2. Abrir Diario y volver atrás con el botón superior y el botón del sistema.
3. Verificar que formularios, diálogos, selectores y subidas siguen respondiendo durante las transiciones.
4. Revisar fluidez en un teléfono de gama media y en una tablet.
5. Activar la opción del sistema para quitar/reducir animaciones y comprobar el comportamiento esperado en el dispositivo.

**Estado:** cambios registrados en la rama de trabajo; compilación y prueba en dispositivos pendientes.
