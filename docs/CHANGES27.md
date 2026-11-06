# cambios27 — Acabado visual y coherencia de tema

## Objetivo
Consolidar el rediseño iniciado en cambios26, eliminar errores de compilación heredados y mejorar la coherencia entre las pantallas principales y sociales.

## Cambios
- Reparado el punto de entrada composable `PawlyMainApp` que impedía compilar cambios26.
- Sustituido el acceso inexistente `colorScheme.primaryLight` por un rol de color Material 3 válido.
- Rehechos los esquemas claro y oscuro con roles semánticos explícitos: primary containers, secondary containers, surface variants, outlines y texto asociado.
- Cabecera con icono de mascota y wordmark más limpio.
- Navegación inferior compacta: mantiene los seis destinos y evita que seis etiquetas compitan constantemente por el ancho.
- Diálogo de personalización con desplazamiento vertical para pantallas pequeñas.
- Aplicados colores dinámicos de Material 3 a componentes comunes de Comunidad y pantallas de Explorar/Diario.

## Validación pendiente
- GitHub Actions debe completar tests, lint, APK debug y AAB.
- Revisar Inicio, Explorar, Salud, Diario, Comunidad, perfil y chat en Android físico.
- Revisar modo claro y oscuro, tamaños de fuente grandes y dispositivos estrechos.
- Las fotografías editoriales remotas necesitan conexión; validar placeholders y errores de red.

## Capturas
No se han añadido imágenes sintéticas ni mockups presentados como capturas reales. Para revisar la apariencia real, instalar el APK de CI o compilar en Android Studio y capturar el Redmi. Compartir esas capturas permite afinar espaciados, tamaños y contraste con fidelidad.
