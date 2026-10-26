# UkiList

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Fork de UKIKU](https://img.shields.io/badge/Fork%20de-jordyamc%2FUKIKU-orange)](https://github.com/jordyamc/UKIKU)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)

**UkiList** (`com.altem1.ukilist`) es un fork no oficial de [UKIKU](https://github.com/jordyamc/UKIKU) enfocado en la integración y sincronización automática con [AniList](https://anilist.co), libre de anuncios.

---

## Novedades respecto a UKIKU original

- **Sincronización con AniList (OAuth2)**:
  - **Importación de biblioteca**: importa tu lista de animes desde AniList y marca automáticamente los episodios vistos en la base de datos local.
  - **Scrobble automático**: registra en AniList cada episodio que terminas de ver en el reproductor (al alcanzar el 85 % o finalizar el video) mediante `WorkManager`.
  - **Sincronización manual por episodio**: marca o desmarca episodios individualmente con indicador de estado de sincronización (nube por capítulo).
  - **Gestor de excepciones de AniList**: pantalla dedicada para reintentar episodios fallidos, asociar manualmente un anime con su entrada de AniList o descartar errores.
- **Sin publicidad ni rastreadores de anuncios**: se eliminaron las integraciones de anuncios y Dropbox del proyecto original.
- **Compilación autónoma**:
  - Módulo `:dtpv` ([DoubleTapPlayerView](https://github.com/vkay94/DoubleTapPlayerView)) integrado localmente y portado a **AndroidX Media3**.
  - Valores de Firebase Remote Config servidos localmente sin depender del backend original.
  - Identificador de aplicación independiente (`com.altem1.ukilist`) para poder instalarlo junto con UKIKU original sin conflicto de firmas ni paquetes.

---

## Compilación

1. Clona este repositorio:
   ```bash
   git clone https://github.com/Altem1/UkiList.git
   cd UkiList
   ```
2. Crea un cliente de API en [AniList Developer Settings](https://anilist.co/settings/developer) con el **Redirect URL**:
   ```
   ukiku://anilist-callback
   ```
3. En la raíz del proyecto, asegúrate de definir tu `ANILIST_CLIENT_ID` en `local.properties`:
   ```properties
   ANILIST_CLIENT_ID=tu_client_id
   ```
4. Compila el APK con Gradle (requiere JDK 17):
   ```bash
   ./gradlew assembleDebug
   ```

---

## Créditos y agradecimientos

Este proyecto existe gracias al trabajo de código abierto de sus autores originales:

- **[UKIKU](https://github.com/jordyamc/UKIKU)** — Proyecto original creado y desarrollado por **Jordy Alexis Mendoza Caballero** ([@jordyamc](https://github.com/jordyamc)).
- **[DoubleTapPlayerView](https://github.com/vkay94/DoubleTapPlayerView)** — Librería de gestos de doble toque creada por **Viktor Krez** ([@vkay94](https://github.com/vkay94)), incluida en este repositorio bajo el módulo `:dtpv` (portada a Media3).
- **[AniList](https://anilist.co)** — Sincronización y metadatos de seguimiento proporcionados a través de la [API pública GraphQL de AniList](https://github.com/AniList/ApiV2-GraphQL-Docs).
- **Fork (`UkiList`)** — Mantenido por **Altem01** ([@Altem1](https://github.com/Altem1)).

---

## Licencia

Este proyecto se distribuye bajo la **Licencia MIT**, conservando íntegros los derechos de autor y avisos de licencia del proyecto original [UKIKU](https://github.com/jordyamc/UKIKU) ([`LICENSE`](LICENSE)):

```text
MIT License

Copyright (c) 2018 Jordy Alexis Mendoza Caballero

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
