# Cambios 28 — rediseño visual integral

Esta rama aplica una dirección visual más editorial y coherente, inspirada en la propuesta de ocho pantallas.

## Cambios
- Inicio: tarjeta principal de mascota con fotografía panorámica, degradado y acción de edición integrada.
- Navegación: etiquetas visibles junto a los iconos para hacer más clara la navegación entre seis áreas.
- Explorar: filtros horizontales desplazables y tarjetas de categorías con jerarquía visual más clara.
- Salud: tarjeta principal presentada como pasaporte de bienestar, manteniendo recordatorios y fechas.
- Diario: cabecera fotográfica más amplia y textos orientados a recuerdos.
- Comunidad: cabecera destacada, navegación en chips redondeados y mayor coherencia con la paleta.
- Mensajería: cabecera más cuidada y contraste correcto en el botón de envío.
- Ajustes: jerarquía visual y títulos de sección refinados.

## Compatibilidad funcional
Se mantienen los flujos existentes de autenticación Firebase, muro, perfiles, mensajes, fotografías, recordatorios, datos locales, diario y lugares guardados. Los cambios de esta rama son de presentación y composición; no implican que se hayan activado servicios externos que aún no estén configurados.

## Validación pendiente
- Ejecutar testDebugUnitTest, lintDebug, assembleDebug y bundleRelease en GitHub Actions.
- Instalar el APK en un dispositivo Android y revisar tamaños, contraste y desbordamientos.
- Comparar capturas reales con la propuesta conceptual.
