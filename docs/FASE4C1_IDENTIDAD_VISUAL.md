# Fase 4C.1 — Identidad visual global

## Objetivo
Establecer una base visual reutilizable para que Pawly comparta jerarquía, superficies, color, formas y estados en sus principales áreas, sin modificar lógica de negocio ni datos.

## Sistema compartido
- `PawlyTheme`: tipografía Nunito local, paletas de acento, esquemas claro/oscuro y formas Material 3.
- `PawlyDesignSystem.kt`: `PawlyCard`, `PawlySectionHeading`, `PawlyStatusPill` y `PawlyIconTile`.
- `PawlyVisualComponents.kt`: cabecera de página y placeholder shimmer compartidos.

## Adopción aplicada en esta rama
- Comunidad: tarjeta de estado vacío basada en `PawlyCard`.
- Centro de aprendizaje: cabeceras y tarjetas de artículos usan primitivas compartidas; textos, iconos y superficies usan roles semánticos.
- Selectores desplegables: radios y contenedores derivados de las formas del tema.
- Salud: cabecera de sección y estados/superficies migrados a roles Material 3.
- Diario y Explorar: colores de texto y superficies de los componentes migrados a roles semánticos.
- Inicio/cuidados: títulos de sección compartidos, tarjeta principal de mascota basada en `PawlyCard` y tonos de cuidados/acciones derivados del tema.
- Perfil: diálogos migrados de colores globales locales a roles semánticos.

## Reglas para nuevas pantallas
1. Usar `MaterialTheme.colorScheme` para texto, superficies, contenedores, bordes y estados.
2. No introducir nuevos colores hexadecimales en pantallas salvo ilustraciones, fotografías, overlays o excepciones documentadas.
3. Usar `PawlyCard` para tarjetas de contenido estándar y `PawlySectionHeading` para títulos de bloques.
4. Reservar el color de marca para acciones y énfasis.
5. Usar `PawlyStatusPill` para estados en lugar de chips improvisados.
6. Conservar contraste y legibilidad en ambos esquemas.

## Pendiente de auditoría exhaustiva
La migración de pantallas principales y componentes compartidos ya está ampliada, pero todavía pueden quedar colores hexadecimales intencionados o estilos históricos en pantallas secundarias y módulos especializados. La auditoría final de todas las pantallas, accesibilidad y consistencia visual debe completarse en 4C.6; no se declara que cada literal de color del repositorio haya desaparecido. La compilación y las pruebas visuales en dispositivos siguen pendientes.
