# Fase 4C.4 — Adaptabilidad y accesibilidad

## Cambios incluidos

- **Tablets y plegables:** el contenido de los destinos principales se centra y limita a 840 dp cuando hay espacio suficiente. Evita líneas excesivamente largas y mantiene el fondo y la navegación ocupando toda la ventana.
- **Móviles compactos:** por debajo de ese umbral se conserva el ancho disponible completo, sin imponer un ancho mínimo ni introducir desplazamiento horizontal deliberado.
- **Controles táctiles:** el tema global de Material 3 establece un tamaño mínimo interactivo de 48 dp para los componentes compatibles, de forma consistente en todas las pantallas.
- **Contraste:** se oscurece el color de texto secundario de la paleta clara para mejorar la lectura de etiquetas, ayudas y metadatos sobre superficies claras.
- **Escalado de texto:** se mantiene el escalado de fuentes del sistema de Compose; no se fija una escala tipográfica que impida al usuario ampliar el texto.

## Criterios de validación manual

| Entorno | Comprobación |
| --- | --- |
| Teléfono compacto (ancho 320–360 dp) | Formularios, tarjetas y navegación no deben provocar desplazamiento horizontal ni cortar acciones. |
| Teléfono estándar | Los controles principales deben poder tocarse cómodamente y el texto debe seguir legible. |
| Tablet / plegable desplegado (>= 840 dp) | El contenido de cada destino debe quedar centrado y no superar 840 dp; la navegación conserva su ancho completo. |
| Fuente del sistema al 130–200 % | Los textos deben crecer sin quedar fijados a tamaños en píxeles ni perder su contenido. |
| Tema claro | Texto secundario y estados deben distinguirse claramente de sus superficies. |
| Accesibilidad del sistema | Verificar TalkBack, orden de foco, etiquetas de iconos y contraste real en dispositivo. |

## Alcance y limitaciones

Esta fase incorpora una base adaptativa transversal y objetivos táctiles Material 3. No sustituye una auditoría visual con dispositivos reales: los componentes personalizados basados únicamente en `Modifier.clickable` pueden necesitar un área mínima explícita, y la navegación lateral específica para tablets queda fuera de este ajuste para no cambiar el flujo de navegación ya existente.

**Estado de verificación:** cambios registrados en GitHub. No se ha ejecutado Gradle ni una prueba en dispositivos en esta sesión; la matriz anterior queda como validación pendiente antes de dar la accesibilidad por auditada al 100 %.
