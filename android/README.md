# Android — Alsama Digital ID

Proyecto **100 % Kotlin / Jetpack Compose**, `minSdk 26`, compile SDK 35.

## Funciones

- Credencial corporativa con nombre, puesto y fotografía de Sebastián (solo previsualización si la API está desconectada).
- Solicitud de verificación en línea a `/v1/public/staff/{slug}`; **nunca presenta una credencial sin verificar como activa**.
- QR verificable a `/p/{slug}` y botón vCard si hay conexión y autorización de publicación.
- Login de administrador en `/v1/auth/login`; listados, altas, edición, activación y desactivación.
- La URL del backend se configura en Ajustes y se puede preconfigurar durante la compilación con `-PalsamaApiUrl=https://...workers.dev`.
- NFC HCE **experimental**: emula una etiqueta NFC Forum Type 4 URI en lectores compatibles. No reemplaza Android Beam ni garantiza compartir entre cualquier teléfono.

## Contrato de backend

`GET /v1/health`, `GET /v1/public/staff/{slug}`, `GET /p/{slug}`, `GET /v1/public/staff/{slug}/contact.vcf`,
`POST /v1/auth/login`, `POST /v1/auth/logout`, `GET/POST /v1/admin/employees`,
`PUT /v1/admin/employees/{id}`, `PATCH /v1/admin/employees/{id}/status`.

La respuesta de login usa `accessToken`. Las respuestas del directorio contienen `employees`. `Employee` usa campos JSON `id`, `slug`, `fullName`, `jobTitle`, `email`, `phone`, `photoUrl`, `active`, `publicVisible`. Los campos se validan en servidor.

## Compilación gratuita

El workflow `.github/workflows/android.yml` compila un APK debug en GitHub Actions y lo ofrece como artefacto. No necesita cuenta de Google Play. El APK debug es solo para pruebas; la distribución final requiere firma de release adecuada y gestión de actualizaciones.

No se incluyen credenciales administrativas. La conexión Android↔Cloudflare se considera **pendiente** hasta que se verifique un endpoint desplegado y se hagan pruebas en dispositivo real.