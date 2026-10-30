# Alsama Digital ID API

Cloudflare Worker + D1. Base URL: `https://alsama-digital-id-api.alsama-digital-id.workers.dev`

## Employee and administrator sign-in

Employees and administrators sign in to `POST /v1/auth/login` with `{"email":"...","password":"..."}`. The response contains `accessToken`, `role` (`admin` or `employee`) and `employee` data only for an authenticated employee. The employee's role is determined server-side. Passwords and session tokens are never committed.

- **Employee card:** `GET /v1/me` uses the Bearer token to load **only the employee bound to that session**, even when the employee is not publicly visible. An inactive employee immediately receives HTTP 401.
- **Administrator:** `GET/POST /v1/admin/employees`, `GET/PUT /v1/admin/employees/{id}`, `PATCH /v1/admin/employees/{id}/status`. Employee tokens always receive HTTP 401 on administrative endpoints.
- **Initial activation:** an authenticated admin requests `POST /v1/admin/employees/{id}/invite` (with an empty JSON body). The backend returns a 64-character one-use activation code, the employee email and an expiration timestamp 24 hours out. Only a SHA-256 hash is persisted in D1. Admin shares the code privately, outside GitHub.
- **Employee sets password:** `POST /v1/auth/activate` body `{"email":"employee@example.com","code":"64-hex-code","password":"at-least-14-characters"}`. Activation consumes the invite, saves salted PBKDF2-SHA256 password hash and invalidates earlier employee sessions.
- **Change password:** authenticated employee calls `POST /v1/auth/change-password` with `{"currentPassword":"...","newPassword":"..."}`, then signs in again.
- **Logout:** `POST /v1/auth/logout` invalidates the bearer token for the active role.
- **Public verifier:** `GET /p/{slug}`, `GET /v1/public/staff/{slug}` and `GET /v1/public/staff/{slug}/contact.vcf` stay public **only** if employee is active and publicVisible. QR verification remains intentionally public; access to the Android employee profile requires login.

## Provisioning, deployment, safety

Apply `backend/migrations/0001_init.sql` if not yet present and `backend/migrations/0002_employee_login.sql` to the actual production D1 **before** deploying the new Worker. Preserve existing admin accounts, users and Worker secrets. Employee email must be unique case-insensitively. Existing employees Alexandra Wong and Sebastián will not receive passwords automatically; each requires a new admin-issued activation code.

The first administrator is configured by the existing one-time `POST /v1/auth/bootstrap` endpoint and a temporary `BOOTSTRAP_TOKEN` Worker secret. Remove that secret once initialization succeeds. The password hash configuration in the GitHub source must match the deployed password hashing configuration (100,000 PBKDF2 iterations for new sign-ins). Admin sessions expire after 8 hours; employee sessions likewise. Rate-limiting uses D1 per email and IP.

**Important limitation:** an activation code privately delivered by an admin establishes account identity within Alsama, but does **not** independently verify control of the email inbox. Automated verification email is not implemented, and generic/shared inboxes such as `info@` should not be treated as individual identity without an explicit company policy.

## Development and verification

`npm install`; `npx wrangler d1 migrations apply alsama-digital-id --local`; `npx wrangler dev --local`. CI uses a disposable local D1 and checks enrollment, employee isolation, revocation and password changes. This is not production deployment; production requires running the remote migration and deploying the Worker.

Cloudflare free plans have quota and usage conditions. No Google Wallet integration.
