# Auditoría de interfaz iOS

Rama de trabajo: `codex/ios-responsive-darkmode-accessibility-2026-11`

Base: `codex/ios-final-polish-sync-hardening-2026-11`.

## Qué se ha revisado por código

- Estructura de las cinco pestañas principales: Inicio, Explorar, Salud, Comunidad y Más.
- Pantallas de autenticación, creación/edición de mascotas, pasaporte sanitario, rutinas, diario y formularios.
- Muro, publicación, comentarios, notificaciones, búsqueda de personas, bandeja de mensajes y chat.
- Ajustes, perfil, agenda, calendario, gastos, lugares, eventos, aprendizaje, Market, profesionales y mascotas perdidas.
- Componentes de diseño compartidos y superficies con color fijo.
- Controles iconográficos, etiquetas de accesibilidad visibles y dimensiones fijas que podían causar desbordamiento.

## Cambios aplicados

1. **Comunidad en pantallas estrechas.** La cabecera ya no intenta mostrar cinco acciones en una sola fila. Mantiene avisos y mensajes con sus contadores, y reúne búsqueda de personas, aviso de mascota perdida y creación de publicaciones en un menú accesible.
2. **Modo oscuro coherente.** Se añaden superficies adaptativas y se sustituyen fondos blancos fijos en formularios, tarjetas de inicio, publicaciones, mensajes, contenido de Aprender y Market. También se adapta el color secundario de la cabecera de autenticación.
3. **Chat.** Se reducen las miniaturas de fotos/vídeos y los espacios laterales para que las burbujas tengan más probabilidades de encajar en iPhones compactos.
4. **Ajustes honestos.** Se retira el selector Simple/Elegante porque el código no aplicaba ningún cambio visual al modificarlo. Se conserva la selección de acento y el modo oscuro.
5. **Diseño compartido.** El componente de tarjeta y los colores de superficie siguen la preferencia de apariencia de Pawly.

## Lo que confirma la CI

La rama anterior `codex/ios-final-polish-sync-hardening-2026-11` había superado el workflow de compilación de iOS Simulator. Los cambios de esta auditoría requieren que el workflow de esta rama termine correctamente antes de considerar confirmada la compilación de este nuevo conjunto.

## Comprobaciones visuales que siguen pendientes

La revisión del código no puede demostrar cómo se dibuja cada vista en un dispositivo concreto. Antes de publicar se debe validar como mínimo:

- iPhone compacto (ancho lógico aproximado de 320–375 pt), iPhone estándar y modelo grande.
- iPad, multitarea/Split View y orientación horizontal.
- Tamaños de texto de accesibilidad y VoiceOver; comprobar que los botones sin texto visible siguen teniendo etiquetas útiles.
- Modo claro y oscuro en todas las pestañas, especialmente textos secundarios, campos, tarjetas, mapas, menús y hojas modales.
- Teclado abierto en registro, publicación, búsqueda, chat y formularios; comprobar que no tapa el campo activo ni los botones de guardar.
- Fotos y vídeos grandes, imágenes que tardan en cargar o fallan, permisos denegados y ausencia de conexión.
- Navegación hacia atrás, apertura/cierre repetido de hojas, cambio rápido de pestaña y sesión/cuenta distinta.

## Alcance

Esta auditoría identifica y corrige problemas demostrables desde el código, pero no equivale a una inspección visual de capturas ni a una prueba manual en iPhone/iPad. El simulador no reproduce APNs reales ni valida credenciales de Firebase, Google o Cloudinary.
