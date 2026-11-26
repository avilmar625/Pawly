# Seguimiento de Pawly iOS

La rama de entrega es `codex/ios-auditoria-integral-2026-11`, desde `bcc39e10`. La [matriz de los 22 requisitos, arquitectura y pruebas pendientes](ios/AUDITORIA_2026_11.md) recoge el detalle por módulo.

| Bloque | Estado | Evidencia y siguiente acción |
|---|---|---|
| Interfaz, agenda, mapas, perfiles, copias y siete idiomas | Implementado con las salvedades de la matriz | 18 commits hasta `4cf35f0d`. Debug y Release para simulador. Cinco pruebas Swift autónomas, 552 claves por idioma, 23 casos de reglas y 50 casos del Worker. |
| Navegación y copias con Firebase local | Verificado en el iPhone 17e simulado | Cuatro XCTest completados tras integrar multimedia, incluidos cambio de mascota y lengua, conservación del calendario, ojo de contraseña y restauración repetida con datos modificados. |
| CI en macOS alojado | Corrección implementada; resultado de la rama enlazado abajo | La compilación, la comprobación de APNs y el Worker pasaron. El [job de Xcode](https://github.com/avilmar625/Pawly/actions/runs/45778065379) encontró arranque en frío de Firestore y el aviso tardío del llavero. Las [reglas](https://github.com/avilmar625/Pawly/actions/runs/45778065403) pasaron. Se inicia el iPhone antes de compilar, se establece la conexión inicial del SDK con un documento efímero y se evita guardar contraseñas ficticias en el llavero del simulador. |
| Multimedia | Integración local verificada | La prueba pasó en iPhone simulado. Una imagen PNG se prepara como JPEG y una muestra de cabecera MP4 recorre el transporte controlado. El emulador Firestore conserva dos índices con URL, tipo y propietario. El contrato rechaza hosts, cuentas y carpetas distintos. La red de Cloudinary y la captura física quedan pendientes. |
| Icono de instalación | Implementado y verificado | Huella oficial de Android, tres PNG de 1024 px. El plist instalado declara AppIcon para iPhone/iPad. El catálogo compilado identifica las variantes principal opaca, oscura transparente y tintada monocroma. Verificado visualmente en la pantalla de inicio del iPhone 17e simulado. |
| Mensajería | Historial recuperable y prueba integrada superada | Se cargan bloques de 200 a petición. El mensaje entrante sigue activando el desplazamiento con la ventana llena. Los acuses se limitan a los mensajes recibidos y cargados. El iPhone simulado verificó el orden, el reemplazo de un mensaje en la ventana y la recuperación del historial con el SDK y las reglas reales en el emulador. |
| Validación externa | Pendiente | Cámara y micrófono físicos, presets Cloudinary de staging, Google OAuth, correo, APNs, dos dispositivos Android/iOS y servicios efectivos de eliminación de cuenta. |

**Siguiente paso.** Para la validación externa, usar un iPhone y cuentas de staging para cámara, OAuth, APNs y sincronización con Android. Conservar los datos de producción. La matriz enlazada identifica también el trabajo de producto pendiente.


**Estado de la rama en GitHub.** Los indicadores se actualizan tras el push que toca la app. [![iOS CI](https://github.com/avilmar625/Pawly/actions/workflows/ios.yml/badge.svg?branch=codex%2Fios-auditoria-integral-2026-11)](https://github.com/avilmar625/Pawly/actions/workflows/ios.yml?query=branch%3Acodex%2Fios-auditoria-integral-2026-11) · [![Reglas Firestore](https://github.com/avilmar625/Pawly/actions/workflows/pawly-validation.yml/badge.svg?branch=codex%2Fios-auditoria-integral-2026-11)](https://github.com/avilmar625/Pawly/actions/workflows/pawly-validation.yml?query=branch%3Acodex%2Fios-auditoria-integral-2026-11)
