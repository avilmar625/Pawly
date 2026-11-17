# Pawly — sistema visual Android

Este documento recoge la dirección visual de Pawly basada en el PDF «Pawly — pantalla Hoy y navegación» (16-11-2026). Es una guía de coherencia para las pantallas existentes, no una excusa para eliminar funciones.

## Principios de interfaz
- Priorizar una interfaz cálida, clara y fotográfica, con jerarquía visual y acciones reconocibles.
- La pantalla Hoy debe responder rápidamente: qué mascota está seleccionada, qué cuidados quedan, qué viene después y qué se puede registrar.
- Las tarjetas que parezcan botones deben ejecutar una acción real y llevar a su destino funcional.
- Mantener textos legibles, etiquetas visibles en navegación, estados vacíos útiles, carga y errores explicados.
- Las fotografías deben acompañar contenido real; no inventar datos, actividad, ubicaciones, citas ni métricas.

## Paleta base
| Token | Color | Uso |
|---|---|---|
| Forest | #173F35 | Acción principal, progreso, navegación activa |
| Forest Light | #2D6655 | Estados secundarios |
| Cream | #FFF8F0 | Fondo principal |
| Paper | #FFFEFB | Superficies y tarjetas |
| Ink | #202923 | Texto principal |
| Muted | #625F56 | Texto secundario en tema claro |
| Lime | #E4EBC1 | Acentos positivos y progreso |
| Peach | #F3DCCF | Próximas citas, avisos suaves |
| Line | #ECE5DC | Bordes y separadores |

Los valores base viven en `PawlyTheme.kt`. Los acentos alternativos y el tema oscuro deben seguir funcionando.

## Componentes y comportamiento
- **Cabecera Hoy:** fecha local, mascota seleccionada, acceso a notificaciones y ajustes. No mostrar contadores inventados.
- **Progreso diario:** número de cuidados completados, progreso proporcional y mensaje contextual.
- **Lo de hoy:** tareas ordenadas por hora, responsable y estado. La medicación conserva la confirmación preventiva de posible doble toma.
- **Próximamente:** fecha y hora cuando la fuente almacena hora real. Las entradas de solo fecha no deben mostrar 00:00.
- **Registrar rápido:** Paseo, Peso, Foto y Comida deben conservar sus acciones actuales.
- **Cuidado compartido:** mostrar actividad real y quién registró cada acción; no simular sincronización.
- **Recuerdos:** aniversario solo cuando existe una entrada real del Diario.
- **Explorar:** en modo elegante puede usar una imagen panorámica con degradado y texto superpuesto; en modo simple conserva la tarjeta ligera. Ambos abren la misma sección funcional.
- **Navegación:** Hoy, Mi perro, Comunidad, Explorar y Más; los destinos secundarios siguen accesibles sin duplicar Salud.

## Criterios de aceptación visual
1. Contraste suficiente en tema claro y oscuro.
2. Áreas táctiles cómodas y etiquetas no truncadas.
3. Diseño adaptable a pantallas pequeñas y grandes.
4. Estados de carga, vacío, error y modo sin conexión claramente diferenciados.
5. Ninguna tarjeta interactiva queda como decoración sin callback.
6. No usar fotografía remota como único indicador o como sustituto de datos funcionales.
7. Verificar que la imagen del modo elegante carga con conexión y que el contenido sigue siendo legible si falla la red.

## No-regresión
La mejora visual no debe retirar ni degradar: eventos, lugares, mapas, teléfonos/llamadas, colecciones, comunidad, perfiles, chat, salud, pasaporte, Diario, calendario, aprendizaje, profesionales, mascotas perdidas, notificaciones, moderación, exportaciones ni copias de seguridad.
