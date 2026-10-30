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

## Employee accounts (new)

The app starts with a shared **Login** screen. `admin` sees the administration directory; `employee` is routed only to their own profile from `GET /v1/me`, never from a user-submitted slug. **Activar cuenta de empleado** accepts an invite code issued in administration via **Crear acceso**; each invite expires in 24 hours and can be redeemed once. **Mi cuenta** changes a password or logs out. Sessions are held in memory; restarting the app requires a new login.

Requires backend migration `0002_employee_login.sql` and a coordinated Cloudflare deployment. Older APKs do not implement the login and activate screens. No email-sending or automatic email ownership verification is provided.

## Unified account access

Everyone signs in using only their email and a single password. Android no longer asks the person to choose Admin or Employee. The API returns `isAdmin` based on the administrator record, not a selection supplied by the phone. Every user with an employee record starts on **Mi credencial**; approved administrators also see **Administración** in the navigation and have a **Mi cuenta** screen for password changes. An administrator's existing password is their single account password; do not create a separate employee password for the same email. Employee activation remains available only for email addresses without an administrator account.

An administrator can review other employees' private records only via authenticated administrative routes, not by guessing public employee URLs. The token is kept in memory and expires on the server. 
