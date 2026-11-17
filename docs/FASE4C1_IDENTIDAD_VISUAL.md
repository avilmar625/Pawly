# Fase 4C.1 — Identidad visual global

## Objetivo
Establecer una base visual reutilizable para que las pantallas de Pawly compartan jerarquía, superficies, color y estados, sin modificar la lógica de negocio ni los datos.

## Sistema compartido
- `PawlyTheme` centraliza tipografía Nunito, paletas de acento, esquemas claro/oscuro y formas Material 3.
- `PawlyDesignSystem.kt` incorpora:
  - `PawlyCard`: superficie estándar con borde semántico y espaciado uniforme.
  - `PawlySectionHeading`: título y descripción de sección.
  - `PawlyStatusPill`: estados neutral, marca, éxito, aviso y error.
  - `PawlyIconTile`: contenedor de icono con superficie de marca.
- `PawlyVisualComponents.kt` mantiene la cabecera de página y el placeholder shimmer compartidos.

## Componentes adaptados
- Comunidad: tarjeta de estado vacío basada en `PawlyCard`.
- Centro de aprendizaje: cabeceras y tarjetas de artículos usan primitivas compartidas; los colores de texto, iconos, superficies y estados se leen del `MaterialTheme.colorScheme`.
- Selectores desplegables: formas derivadas del tema en vez de radios locales fijos.

## Reglas para nuevas pantallas
1. Usar `MaterialTheme.colorScheme` para texto, superficies, contenedores, bordes y estados.
2. No introducir nuevos colores hexadecimales en pantallas salvo ilustraciones o excepciones documentadas.
3. Usar `PawlyCard` para tarjetas de contenido estándar y `PawlySectionHeading` para títulos de bloques.
4. Reservar el color de marca para acciones y énfasis; no usarlo para todo el texto.
5. Usar `PawlyStatusPill` para estados en lugar de chips improvisados.
6. Las pantallas deben conservar contraste y legibilidad en ambos esquemas.

## Alcance y validación
Esta entrega es la base global y su primera adopción en componentes compartidos. No significa que todas las pantallas estén ya migradas: eso corresponde a 4C.3 y a la auditoría de 4C.6. La compilación y las pruebas visuales quedan pendientes; los errores de build no bloquean la implementación funcional/visual solicitada para esta fase.
