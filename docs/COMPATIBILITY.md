# Compatibilidad real

La instalación acepta manifiestos API 1–17, verifica entry y firmas y prueba los exports. Esto no
equivale a implementar todo el host de todas esas versiones.

| Área | Estado y alcance |
|---|---|
| Módulos ES | QuickJS real; un entry, imports de plataforma no disponibles |
| home/search/browse/episodes/resolve | Ejecución y explorador en Mis plugins; paginación de browse |
| Canales | liveCategories/liveChannels/liveSearch, inline/ref/M3U, categorías, favoritos |
| Programación | guide y XMLTV/gzip; ids separados por plugin |
| Configuración | text/url/password/toggle/select; listas JSON; action y settingsStatus |
| Almacenamiento | Persistencia por plugin, TTL, cuotas; storageLarge |
| Red | HTTP de servidores configurados, HTTPS/hosts declarados, redirects y DNS comprobados |
| Reproducción | Adaptadores a los reproductores Nuvio; proxy con cabeceras/Range/HLS/DASH |
| sign | Función por solicitud; sin red ni almacenamiento en esa llamada |
| Firmas de autor | Ed25519 y clave conservada al actualizar; entry exacto y repo vinculados |
| Tiendas | Catálogo oficial/comunidad, fuentes del autor y storeSync de tiendas instaladas |
| Pantallas | Subconjunto básico de texto, contenedores, listas y botones; falta el kit completo |
| Criptografía | Hash/HMAC, codificación y aleatoriedad; faltan cifrados/firmas/secrets avanzados |
| Catálogo | Persistencia JSON básica; no sustituye toda la semántica SQLite de Kino |
| meta/subtitles/tracking/segments | Exports ejecutables; falta conectarlos a los eventos/repositorios Nuvio |
| download/playlists | Instalables; falta conectar estas funciones al gestor de Nuvio |
| syncProvider | Falta conversión de estados y transporte al esquema de sincronización Nuvio |
| socket/byteSource/imageSource | Falta el host completo de sockets y streams servidos por plugin |
| externalResolve/CloudStream IPC | Falta el protocolo de enlace con las apps complementarias de Kino |
| room/playerPanelTab/playerControl | Falta integrar las salas, paneles y control del reproductor |
| library/schedule/shared/ai | Falta integrar biblioteca, tareas, resultados compartidos e IA |
| identity/notifications/coins | Falta el servicio de identidad/atestación y su ecosistema |
| Temas API 17 | Manifiestos y módulos instalables; los tokens no se aplican a la UI Nuvio |
| DRM Kino | Rechazo explícito al reproducir hasta conectar la licencia al reproductor |

La instalación no activa automáticamente servicios ni permisos nuevos del sistema. Los campos de
configuración avanzados link/QR y validaciones declarativas complejas aún no tienen UI completa.
La reconexión con resolución renovada de canales, el zapping dentro del reproductor y las reglas
de foco/Volver de todos los componentes requieren más trabajo para igualar Kino.

Los 29 módulos del autor se verificaron como fuentes, con sus firmas y exports; se probó instalación
y reapertura. Solo el servidor de referencia se ejercitó de extremo a extremo con credenciales de
prueba. No se probaron cuentas privadas, servicios de pago ni autenticación productiva de terceros.

Kino Social valida tokens y atestaciones de Kino en su backend. Su interoperabilidad funcional
requiere cooperación del proveedor; no basta un cambio de cliente.
