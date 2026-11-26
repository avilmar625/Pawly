# Seguimiento de Pawly iOS

La rama de entrega es `codex/ios-auditoria-integral-2026-11`, desde `bcc39e10`. La [matriz de los 22 requisitos, arquitectura y pruebas pendientes](ios/AUDITORIA_2026_11.md) recoge el detalle por módulo.

| Bloque | Estado | Evidencia y siguiente acción |
|---|---|---|
| Interfaz, agenda, mapas, perfiles, copias y siete idiomas | Implementado con las salvedades de la matriz | 18 commits hasta `4cf35f0d`. Debug y Release para simulador. Cinco pruebas Swift autónomas, 552 claves por idioma, 23 casos de reglas y 50 casos del Worker. |
| Navegación y copias con Firebase local | Verificado en el iPhone 17e simulado | Tres XCTest completados, incluidos cambio de mascota y lengua, conservación del calendario, ojo de contraseña y restauración repetida con datos modificados. |
| CI en macOS alojado | Corrección en curso | La compilación, la comprobación de APNs y el Worker pasaron. El [job de Xcode](https://github.com/avilmar625/Pawly/actions/runs/45778065379) encontró arranque en frío de Firestore y el aviso tardío del llavero. Las [reglas](https://github.com/avilmar625/Pawly/actions/runs/45778065403) pasaron. |
| Multimedia | Prueba de integración en curso | Se recuperaron del clon el transporte sustituible y la validación de las URL de entrega. Falta probar JPEG y vídeo con respuesta controlada e índice de propiedad real en el emulador. |
| Icono de instalación | En curso | La referencia oficial de Android es `app/src/main/res/drawable/ic_pawly.xml`, vinculada desde el manifiesto. Huella crema sobre verde bosque. Falta compilar y verificar AppIcon y sus tres apariencias. |
| Validación externa | Pendiente | Cámara y micrófono físicos, presets Cloudinary de staging, Google OAuth, correo, APNs, dos dispositivos Android/iOS y servicios efectivos de eliminación de cuenta. |

**Siguiente paso.** Corregir las dos diferencias de CI, terminar el test multimedia y verificar el icono instalado en el simulador. Confirmar el SHA remoto y las dos Actions al cerrar.
