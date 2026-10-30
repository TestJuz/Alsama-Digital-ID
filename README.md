# Alsama Digital ID

Aplicación Android nativa (Kotlin + Jetpack Compose) para credenciales dinámicas de Alsama Tours Costa Rica.

- **Android:** `android/` — credencial, contacto/QR, panel de empleados, edición y HCE NFC experimental.
- **Backend:** se desarrolla independientemente en `backend/` y se integra mediante la API `/v1`.
- **Seguridad:** los datos y el estado laboral se verifican en el servidor; el QR es un enlace de consulta, no prueba offline.
- **Costo de desarrollo:** US$0, distribución por APK de GitHub Actions (sin Google Wallet / Play Store).

## Estado

Android source + CI; **no se afirma que el backend esté desplegado** ni que el NFC funcione con todos los teléfonos. Ver `android/README.md`.