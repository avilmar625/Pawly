# Fase 4C.6 — Auditoría final del diseño

## Alcance revisado

Auditoría estática de la rama `cambios83-fase4c3-redisenosecciones` sobre el tema, sistema de diseño, componentes visuales y destinos principales: Inicio/navegación, Más, Comunidad, Explorar, Diario, Salud, Calendario, Market, Aprender, Profesionales y Ayuda para mascotas perdidas.

## Correcciones aplicadas

- **Más:** eliminados los colores locales redundantes de las tarjetas de destino. Los iconos y flechas usan ahora los roles de color del tema, igual que el resto de superficies.
- **Jerarquía en Más:** retirado un bloque promocional que repetía la propuesta de valor ya expresada por la cabecera. La pantalla gana espacio y reduce ruido visual.
- **Cabeceras fotográficas:** añadido el token `PawlyPhotoScrim` al tema y aplicado a Más, Comunidad, Explorar, Diario y Aprender. Unifica el degradado oscuro que sostiene el texto blanco sobre imágenes.
- **Muro de Comunidad:** filtrado y ordenación se calculan dentro de `remember`, evitando repetir el filtrado y el orden O(n log n) en recomposiciones que no cambian los datos ni los filtros.
- **Documentación de paleta:** sincronizado el valor de Muted con el tema claro actual (`#625F56`).

## Resultado por criterio

| Criterio | Resultado de auditoría |
|---|---|
| Estilos antiguos dispersos | Se han retirado estilos locales innecesarios de Más y se ha centralizado el scrim fotográfico. Permanecen colores específicos para estados (error, favorito, verificación) y fondos de categorías ilustrativas; no son paletas completas duplicadas. |
| Diferencias entre pantallas | Las cabeceras, tarjetas y superficies principales comparten ahora tokens y componentes. Algunos destinos mantienen héroes fotográficos propios por identidad de contenido; no se fuerza una plantilla idéntica. |
| Contraste y legibilidad | El tema usa un texto secundario más oscuro y un scrim común en cabeceras fotográficas. Los textos blancos sobre superficies de marca deben comprobarse con contraste real, especialmente con acentos alternativos y modo oscuro. |
| Componentes duplicados | Existen primitivas reutilizables (`PawlyCard`, `PawlySectionHeading`, `PawlyStatusPill`, `PawlyIconTile`, `PawlyPageHeader`). La adopción todavía no es total: quedan superficies Material 3 específicas donde cambiar a una primitiva común podría alterar interacciones o jerarquía. |
| Móviles y tablets | El contenedor principal limita el ancho del contenido en ventanas amplias y conserva el ancho disponible en móviles. La ausencia de desbordamientos a 320 dp y con fuente al 200 % requiere prueba en dispositivo/emulador. |
| Botones, tarjetas, iconos y cabeceras | Los componentes Material 3 heredan el mínimo táctil global de 48 dp; los componentes personalizados con `clickable` necesitan revisión individual de hit target y descripción accesible. |
| Rendimiento | Las listas principales usan componentes lazy; el muro evita recalcular filtros/ordenación en recomposiciones no relacionadas. El shimmer es infinito solo mientras se compone el skeleton; debe reservarse para cargas reales. Las imágenes remotas y listas largas requieren profiling en dispositivo. |

## Decisiones deliberadas

- No se han eliminado colores semánticos de error, favorito, insignias verificadas ni estados de pérdida: comunican información y no sustituyen al color de marca.
- Los fondos suaves de categorías en Explorar y los degradados fotográficos son recursos de orientación visual; no se convierten todos en el mismo color.
- No se ha reemplazado indiscriminadamente cada `Surface` por `PawlyCard`: algunas superficies son botones, banners o contenedores con semántica e interacción propias.
- Se mantiene el comportamiento de navegación y las acciones existentes.

## Validación pendiente antes de dar el diseño por cerrado

Esta auditoría es estática. No acredita compilación ni prueba visual en dispositivos. Ejecutar al menos:

1. `./gradlew :app:assembleDebug` y pruebas unitarias disponibles.
2. Teléfono compacto (320–360 dp), teléfono estándar y tablet/plegable (>= 840 dp).
3. Escala de fuente 130 %, 150 % y 200 %, con TalkBack y navegación por foco.
4. Tema claro y oscuro con los cuatro acentos disponibles.
5. Cargas lentas/sin red, imágenes fallidas, listas extensas y navegación repetida entre destinos.
6. Verificar que cada control personalizado tiene área táctil suficiente y descripción accesible.

**Estado:** correcciones estáticas registradas en GitHub. No se declara compilación ni QA visual de dispositivo en esta sesión.
