# Fase 4C · Diseño premium de Pawly

## 4C.1 — Identidad visual global
- Nunito Variable integrada localmente con licencia SIL OFL.
- Tema Material 3 centralizado con cuatro acentos, esquemas claro/oscuro y formas compartidas.
- Primitivas comunes: PawlyCard, PawlySectionHeading, PawlyStatusPill y PawlyIconTile.
- Adopción extendida en Inicio/cuidados, Comunidad, aprendizaje, Salud, Diario, Explorar, selectores y diálogos de perfil.
- Migración de colores de texto, iconos, superficies y estados a roles semánticos donde se han tocado esos componentes.
- La auditoría exhaustiva de pantallas secundarias y accesibilidad queda para 4C.6.

## 4C.2 — Rediseño de Inicio
- Cabecera de mascota convertida en una tarjeta de bienvenida destacada, con foto/avatar, nombre, texto de contexto y acción de edición.
- Jerarquía de Inicio ordenada para priorizar la mascota y sus cuidados reales del día; después salud/peso, recuerdos, próximas ideas y aprendizaje.
- Secciones de cuidados y próximos eventos usan títulos compartidos.
- Tarjetas y acentos de Inicio/cuidados usan formas y roles semánticos del tema.
- Se mantienen callbacks y destinos existentes; el rediseño no crea tareas ficticias ni cambia la persistencia de registros.

## Otras mejoras 4C
- Shimmer reutilizable para carga remota real y skeleton en evolución de peso.
- Feedback háptico discreto en acciones rápidas y registros.
- Inicio limita el ancho en tablets y adapta márgenes/espaciado.
- Nunito y paletas funcionan sin descargar recursos en tiempo de ejecución.

## Validación
Los cambios están subidos a la rama de trabajo. No se declara compilación ni validación visual en dispositivo: deben ejecutarse en CI y Android Studio/Xcode. La rama no se fusiona a main.
