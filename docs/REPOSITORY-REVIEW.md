# Revisión de los repositorios de Kino

Inventario de 39 repositorios públicos consultado el 1 de noviembre de 2026. Los commits y contratos examinados están en [repositories.json](repositories.json). Los repositorios de plugins se revisaron por su README, manifiesto, entry y, donde existían, sus pruebas y SDK. No se ejecutaron sus servicios de producción ni scripts de despliegue.

| Repositorio | Función | API / capacidades |
|---|---|---|
| [cloudflare-quick-tunnel-kotlin](https://github.com/kinotvapp/cloudflare-quick-tunnel-kotlin/tree/22a00aeb58393fd36df1148ad296d8c096b1e70b) | Transporte de túneles JVM/Android | — |
| [kino-cloudstream-bridge](https://github.com/kinotvapp/kino-cloudstream-bridge/tree/62e73063938fe5beb363799c56ba5f405b8ac83b) | Host Android IPC aislado para extensiones CloudStream | — |
| [kino-light](https://github.com/kinotvapp/kino-light/tree/8f5d71a16b12ff17ad8f6f259ef63dba92765a0a) | Demo de UI; plugins y TV son pantallas de muestra | — |
| [kino-plugin-archive](https://github.com/kinotvapp/kino-plugin-archive/tree/016503f53e9d71ae02dbff0b53074c6fdcdb11a8) | Plugin Kino | 3: search, home, browse, episodes, resolve, download |
| [kino-plugin-archive-audio](https://github.com/kinotvapp/kino-plugin-archive-audio/tree/540c1cadd6fcc0695eea9ae68cff0d23a2159044) | Plugin Kino | 8: search, home, browse, episodes, resolve, download |
| [kino-plugin-audio-test](https://github.com/kinotvapp/kino-plugin-audio-test/tree/4eab4e08f08734e5bf411cc31b7cd64048fbb3e2) | Plugin Kino | 8: home, search, episodes, resolve |
| [kino-plugin-news](https://github.com/kinotvapp/kino-plugin-news/tree/bcb4043cc2513a4a86cae87e3e2650a5424f5673) | Plugin Kino | 14: screen, library, schedule, shared, ai |
| [kino-plugin-own-server](https://github.com/kinotvapp/kino-plugin-own-server/tree/40d0441542d79e632d62c5fc0c892efa7c5731f3) | Plugin Kino | 7: search, home, browse, episodes, resolve, download, channels, scopedSearch, migrate, meta, subtitles, tracking, segments |
| [kino-plugin-poster-pro](https://github.com/kinotvapp/kino-plugin-poster-pro/tree/5fc689d8b4bd520c929a3525141b21f0b6036307) | Plugin Kino | 9: meta |
| [kino-plugin-sdk](https://github.com/kinotvapp/kino-plugin-sdk/tree/29653a1706499bf857eaa95700a09b4bf473ca39) | SDK Node, contrato, firmas, sandbox y pruebas | — |
| [kino-plugin-seenr](https://github.com/kinotvapp/kino-plugin-seenr/tree/726ec7346773c2dd2bd5a480e79e3174fe035561) | Plugin Kino | 7: tracking |
| [kino-plugin-store](https://github.com/kinotvapp/kino-plugin-store/tree/897d1774ba5cdbce612513835e42111d6037bd33) | Plugin Kino | 15: pluginStore |
| [kino-plugin-subtitlecat](https://github.com/kinotvapp/kino-plugin-subtitlecat/tree/29f8c7116a3ca9f2da4c2ebc1fa4dcc0e65eb229) | Plugin Kino | 6: subtitles |
| [kino-plugin-sync-gdrive](https://github.com/kinotvapp/kino-plugin-sync-gdrive/tree/8bd76be1f188e0d73bcd097c27efb8008266c79e) | Plugin Kino | 14: syncProvider, screen |
| [kino-plugin-sync-gram](https://github.com/kinotvapp/kino-plugin-sync-gram/tree/af1d4dcfc4248defd8108742d061bdba39a4f90a) | Plugin Kino | 14: syncProvider, screen |
| [kino-plugin-sync-gram-account](https://github.com/kinotvapp/kino-plugin-sync-gram-account/tree/649575043826a9a92cca2a378f80a48449fef442) | Plugin Kino | 14: syncProvider, socket, storageLarge, screen |
| [kino-plugin-sync-server](https://github.com/kinotvapp/kino-plugin-sync-server/tree/55a7e171b70179062a8a42fe23671e55e9f68ab1) | Plugin Kino | 12: syncProvider |
| [kino-plugin-tmdb](https://github.com/kinotvapp/kino-plugin-tmdb/tree/736cb309e785d1a429422cba2110d53e8bbccb6f) | Plugin Kino | 7: search, home, browse, episodes, resolve, meta |
| [kino-plugin-ui-gallery](https://github.com/kinotvapp/kino-plugin-ui-gallery/tree/b959254ad052e979405f12dde2569430dbc095d8) | Plugin Kino | 14: screen |
| [kino-plugin-video-gram](https://github.com/kinotvapp/kino-plugin-video-gram/tree/7905bdcfc66c3918934bc3f0958b03c3a908a352) | Plugin Kino | 14: home, search, browse, scopedSearch, resolve, socket, byteSource, catalog, imageSource, screen |
| [kino-plugin-watch-party](https://github.com/kinotvapp/kino-plugin-watch-party/tree/0ce2d489aedf05bd1315514f02b9dd77226fcfe0) | Plugin Kino | 14: playerPanelTab, room, qrScan, playerControl |
| [kino-plugins](https://github.com/kinotvapp/kino-plugins/tree/eefa3721b04d80b780f821540a5515be41ac9ba3) | Guía pública, contratos y ejemplos | — |
| [kino-resolver-example](https://github.com/kinotvapp/kino-resolver-example/tree/01ed0bfbc6c93addc97d1a6813a2256ee0ec8234) | Resolvedor Android AIDL y servidor HTTP de referencia | — |
| [kino-resolver-example-plugin](https://github.com/kinotvapp/kino-resolver-example-plugin/tree/b220a9bda8a0b31b9bec7489cbddaf7d15de802b) | Plugin Kino | 11: search, home, resolve, externalResolve |
| [kino-social](https://github.com/kinotvapp/kino-social/tree/b60719383a351f62ecae316734fe56e14bf41d71) | Plugin Kino | 16: screen, library, tracking, identity, ai, schedule, notifications, playerPanelTab, coins |
| [kino-social-backend](https://github.com/kinotvapp/kino-social-backend/tree/7c6bc74d12685923e94f9f3cb06625ad03d8587e) | Servidor de Social; autenticación con tokens/atestación de Kino | — |
| [kino-theme](https://github.com/kinotvapp/kino-theme/tree/22186bdd0dbef70771b17555f915d260d31a63d0) | Plugin Kino | 17: tema declarativo |
| [kino-theme-aguijon-de-acero](https://github.com/kinotvapp/kino-theme-aguijon-de-acero/tree/2af107750139c25c30eb8891ba8dabc5abb9c58d) | Plugin Kino | 17: screen |
| [kino-theme-cine-negro](https://github.com/kinotvapp/kino-theme-cine-negro/tree/d0559e57f1669605f21a1dbeb76a9697c20113a0) | Plugin Kino | 17: screen |
| [kino-theme-gala-vampirica](https://github.com/kinotvapp/kino-theme-gala-vampirica/tree/9dee3c8bc53decf310efab9c74636242072ad44f) | Plugin Kino | 17: screen |
| [kino-theme-gran-tesoro](https://github.com/kinotvapp/kino-theme-gran-tesoro/tree/2f3a77cb80a545c4f009217183d30e24794ef578) | Plugin Kino | 17: screen |
| [kino-theme-hechizo-boreal](https://github.com/kinotvapp/kino-theme-hechizo-boreal/tree/1dcc3786e5d46491f09584760c9d6f20edd00e89) | Plugin Kino | 17: screen |
| [kino-theme-justiciero-nocturno](https://github.com/kinotvapp/kino-theme-justiciero-nocturno/tree/fa874ec61578b8f7d247dfbee2e412a4565cd18a) | Plugin Kino | 17: screen |
| [kino-theme-ki-desatado](https://github.com/kinotvapp/kino-theme-ki-desatado/tree/87907cbb299973b5c658d1387879e796afc43772) | Plugin Kino | 17: screen |
| [kino-theme-lluvia-de-codigo](https://github.com/kinotvapp/kino-theme-lluvia-de-codigo/tree/dca8f3944e936cae81f85aab3640a2b8450376d3) | Plugin Kino | 17: screen |
| [kino-theme-reactor-escarlata](https://github.com/kinotvapp/kino-theme-reactor-escarlata/tree/a5c988e154c898227763ee7a94a31b9492af36fb) | Plugin Kino | 17: screen |
| [kino-tv](https://github.com/kinotvapp/kino-tv/tree/cdc17509c5365092582ca258a28e31d3ad7bfead) | Licencia GPL y oferta de código por solicitud | — |
| [kinotvapp.github.io](https://github.com/kinotvapp/kinotvapp.github.io/tree/e26de193a587e356cea87a188fcb65c723fc1855) | Sitio y documentación de distribución | — |
| [winget-pkgs](https://github.com/kinotvapp/winget-pkgs) | Distribución winget; no es un runtime ni un plugin | — |

## Diferencias que condicionan el fork

- La demo no contiene el motor productivo de TV ni de plugins. La integración usa contratos públicos y no se presenta como copia de código privado.
- El SDK consultado anuncia API 15, mientras Social exige 16 y los temas 17. La instalación verifica cada manifiesto.
- CloudStream y los resolvedores son apps Android con contratos IPC, no archivos JavaScript portables a Desktop. Se conservan las capacidades originales de Nuvio, pero no se afirma compatibilidad con el IPC de Kino.
- Social exige un gate token y atestaciones firmadas por Kino: un cliente independiente no puede emitirlas. Su servicio necesita una vía oficial para clientes compatibles.
- Los proveedores sync transportan estado de Kino; no se deben aplicar sus blobs sin una conversión al esquema de Nuvio.
- La tienda mezcla plugins Kino, addons Stremio y comunidad. Una entrada de catálogo no sustituye la validación del manifiesto real.
- Los temas de Kino describen colores, formas y tipografías que no tienen una correspondencia exacta con los temas de Nuvio.
