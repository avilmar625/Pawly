# Fase 4C · Diseño premium de Pawly

## Alcance implementado en cambios82

- Nunito Variable se integra localmente en `res/font`; la interfaz no necesita conexión ni descarga de fuentes para renderizarla.
- Se incluye la licencia SIL Open Font License 1.1 en `licenses/OFL-NUNITO.txt`.
- Se centraliza la tipografía Material 3 con Nunito en todos los estilos de texto.
- Se armonizan los cuatro acentos de marca y se mejora el contraste de los roles principales en modo oscuro.
- Se añade un componente shimmer reutilizable y se utiliza en la tarjeta de evolución del peso mientras se recupera el pasaporte remoto cuando no hay datos locales.
- Se introduce feedback háptico discreto al marcar cuidados y utilizar acciones rápidas.
- Inicio adapta márgenes, separación y ancho máximo en pantallas amplias; en tablets el contenido se mantiene centrado y legible.
- Las variaciones de peso utilizan colores semánticos del tema en lugar de verdes/rojos literales.

## Decisiones de diseño

- La paleta base sigue siendo cálida: fondo crema, superficies marfil, verde bosque y acentos suaves.
- El modo oscuro usa primarios claros por acento para mantener legibilidad sobre superficies oscuras.
- Se conservan selector de idioma, paletas, modo simple/elegante y rutas de navegación.
- No se modifican modelos, claves de almacenamiento, Firestore, Cloudinary ni datos sanitarios.

## Validación

La revisión de código está realizada sobre la rama de continuidad indicada. La compilación, Android Lint y la validación visual en emulador/dispositivo deben ejecutarse en CI y Android Studio antes de dar la fase por cerrada. La rama no se fusiona a `main`.
