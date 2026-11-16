# cambios26 · Rediseño visual de Pawly

Esta iteración prioriza una mejora de percepción y experiencia: imagen, jerarquía visual, navegación, consistencia y estados vacíos, conservando las operaciones y modelos existentes.

## Cambios
- Navegación inferior migrada a Material 3 `NavigationBar`, con estados seleccionados, iconos consistentes y etiquetas legibles.
- Cabecera superior común y acceso visible a personalización.
- Inicio: saludo contextual, tarjetas y tipografía más limpias; la foto del perfil de la mascota se mantiene como protagonista y se ofrece una fotografía de respaldo si aún no hay foto local.
- Explorar: tarjetas de categorías con fotografías editoriales, recorte uniforme y jerarquía de información más clara. Las fotografías remotas requieren conexión y pueden no estar disponibles si el proveedor externo cambia la URL.
- Diario: cabecera fotográfica, tarjetas adaptadas al tema y estado vacío diseñado.
- Salud y pantallas heredadas principales: superficies, fondos y colores de texto adaptados progresivamente al esquema Material 3 y a modo oscuro.
- Acceso a Comunidad: cabecera fotográfica y formulario con jerarquía visual y botones más cuidados.
- Reparada la declaración de `PawlyMainApp` que en la rama anterior contenía secuencias de salto de línea literales y no Kotlin válido.

## Alcance y limitaciones
- No se han cambiado rutas de navegación ni modelos de datos.
- Las fotos editoriales son remotas (Unsplash) y necesitan internet; la foto de perfil de la mascota sigue siendo local.
- Algunas pantallas antiguas todavía conservan colores fijos y necesitan una segunda pasada de adaptación completa al modo oscuro.
- La iconografía se ha unificado en Material Icons; no se ha sustituido por un set vectorial de marca personalizado.
- No se ha confirmado todavía la compilación ni la prueba en dispositivo. Consulta GitHub Actions.

## Próxima pasada visual
1. Adaptar completamente pantallas heredadas a tema claro/oscuro y paleta.
2. Sustituir iconos emoji residuales por ilustraciones vectoriales propias.
3. Mejorar pantallas de aprendizaje, perfil y mensajería con la misma dirección de arte.
4. Añadir fotos a recuerdos, con compresión, subida y gestión de privacidad.
5. Revisar accesibilidad: contraste, tamaños táctiles, TalkBack y escalado de fuente.
