# cambios25 · Personalización, acceso y experiencia

## Implementado
- Preferencias persistentes de apariencia en `pawly_appearance`.
- Modo oscuro mediante esquema Material 3.
- Cuatro colores de acento: bosque, océano, baya y atardecer.
- Panel de ajustes accesible desde la barra superior común.
- Preferencia de idioma guardada: español, inglés y catalán.
- Barra superior común en la aplicación principal.
- CI Android activado para la rama `cambios25`.

## Acceso y proveedores
El proyecto ya dispone de acceso con correo/contraseña y Google mediante Credential Manager y Firebase Authentication, así como recuperación de contraseña por correo. Google depende de OAuth, SHA-1/SHA-256 y del archivo local `app/google-services.json`.

No se ha añadido Apple ni teléfono en esta iteración: requieren configuración de proveedor, flujos de verificación y pruebas reales. No se deben presentar como opciones funcionales hasta completarlos.

## Internacionalización: estado real
La preferencia de idioma se guarda, pero la traducción completa de los textos hardcodeados de la aplicación todavía no está implementada. El siguiente paso es migrar progresivamente los textos a recursos Android (`strings.xml`) y añadir recursos `values-en` y `values-ca`, validando pluralización y formatos de fecha.

## Limitaciones y pruebas
- La paleta de acento afecta al esquema Material 3; algunos componentes heredados aún usan colores fijos.
- El modo oscuro está iniciado a nivel de tema, pero hay pantallas antiguas con fondos explícitos claros que requieren adaptación visual.
- Las preferencias son locales al dispositivo, no se sincronizan con la cuenta.
- No se ha confirmado compilación local ni prueba en dispositivo en esta rama.

## Siguiente fase recomendada
1. Completar traducción real de toda la interfaz (ES/EN/CA).
2. Adaptar todas las pantallas al tema oscuro y paletas.
3. Añadir edición del perfil de cuenta, avatar y preferencias de privacidad en un único centro de ajustes.
4. Evaluar Apple y teléfono tras configurar proveedores Firebase y sus requisitos.
5. Rediseñar Inicio, Salud y Comunidad con componentes visuales consistentes y estados vacíos mejorados.
