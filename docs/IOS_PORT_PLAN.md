# Plan de portabilidad de Pawly a iOS

## Decisión

Crear una aplicación iOS nativa con SwiftUI en `iosApp/`, manteniendo intacta la app Android en `app/`. La aplicación Android actual concentra la interfaz y varias APIs específicas de Android en `PawlyApp.kt`; intentar convertirla directamente a Kotlin Multiplatform obligaría a separar primero cámara, galería, permisos, persistencia, ubicación y Firebase Android.

Para el objetivo actual (instalación personal en un iPhone), SwiftUI ofrece una ruta más simple de probar y mantener.

## Fases

1. **Base iOS:** estructura XcodeGen, entrada SwiftUI, navegación y primeras pantallas locales.
2. **Datos y persistencia:** perfiles de perros, rutina, salud, favoritos y diario, con aislamiento por perro y persistencia local.
3. **Firebase:** Firebase Auth y Firestore con el mismo proyecto y esquema que Android; empezar por correo/contraseña y después Google si se necesita.
4. **Fotografías:** selector/cámara de iOS y subida directa a Cloudinary mediante preset unsigned. Nunca incluir el API Secret en la app.
5. **Comunidad:** perfiles, perros, publicaciones, comentarios, likes, seguidores y mensajes, respetando las reglas actuales de Firestore.
6. **Pruebas:** compilación en Xcode, ejecución en simulador y dispositivo real, revisión de permisos, errores, accesibilidad y consumo.
7. **Instalación:** firma con Apple ID personal para uso propio. App Store no es necesaria para probarla en un dispositivo propio.

## Estado

- [x] Rama independiente `ios-iphone` creada desde `cambios11`.
- [x] Esqueleto SwiftUI y definición XcodeGen añadidos.
- [ ] Generar proyecto Xcode y verificar compilación en macOS.
- [ ] Añadir configuración Firebase iOS sin publicar el plist.
- [ ] Implementar sincronización y funciones completas.
- [ ] Instalar y probar en el iPhone.

## Restricciones

- No modificar `main`, `cambios10` ni `cambios11`.
- No activar Firebase Storage ni Cloud Functions/Blaze por defecto.
- No declarar una función como terminada hasta compilarla y probarla.
- No reemplazar la versión Android ni migrar sus datos de forma destructiva.
