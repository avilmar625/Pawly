# Fase 4C.3 — Rediseño transversal de secciones

## Alcance
Se toma como base directa `cambios82-fase4c-diseno-premium` y se mantiene el trabajo en una rama independiente. Esta entrega extiende la identidad de 4C.1/4C.2 a los destinos secundarios y especializados, sin alterar repositorios, persistencia, navegación ni reglas de negocio.

## Aplicación
- **Más:** accesos con superficies, formas, iconos y estados semánticos del tema; se conserva la jerarquía por bloques y se evitan volver a introducir paletas locales en las tarjetas de destino.
- **Aprender:** fondos, superficies y acentos de contenido conectados al esquema activo, incluido el modo oscuro; se conserva la cabecera editorial y la selección de guías/noticias.
- **Salud, Diario y Explorar:** limpieza de fondos y contenedores heredados para respetar los roles Material 3 activos.
- **Agenda, Market, profesionales, ayuda por pérdida y Comunidad:** se armonizan los fondos base y los contenedores de apoyo con el tema global allí donde existían superficies fijadas.
- **Componentes compartidos:** se priorizan `PawlyPageHeader`, `PawlyCard`, `PawlySectionHeading` y los roles `surface`, `surfaceVariant`, `primaryContainer`, `tertiaryContainer` y `errorContainer`.

## Criterios de conservación
- Se mantienen callbacks, destinos, formularios, filtros, carga remota y operaciones de Firebase/Cloudinary.
- Los colores propios de fotografías, overlays de imagen y categorías ilustrativas se conservan cuando aportan significado y no sustituyen el contraste del texto.
- No se cambian modelos de datos ni formatos persistidos.

## Validación pendiente
La compilación y la comprobación visual en Android/iOS no forman parte del criterio de cierre solicitado para esta fase. Deben revisarse en la validación de integración; 4C.4 cubrirá adaptabilidad y accesibilidad y 4C.6 hará la auditoría exhaustiva de duplicidades y pantallas residuales.
