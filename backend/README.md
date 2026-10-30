# Alsama Digital ID API

Cloudflare Worker + D1. All API payload keys are camelCase. Deployed API: https://alsama-digital-id-api.alsama-digital-id.workers.dev

## Endpoints
GET /v1/health; GET /v1/public/staff/:slug; GET /v1/public/staff/:slug/contact.vcf; GET /p/:slug; POST /v1/auth/bootstrap (one-time); POST /v1/auth/login; POST /v1/auth/logout; GET/POST /v1/admin/employees; GET/PUT /v1/admin/employees/:id; PATCH /v1/admin/employees/:id/status.

## Security and initialization
Bootstrap is disabled unless the environment contains a secret named BOOTSTRAP_TOKEN. Set an unpredictable 32-byte-or-longer value as a Worker secret, then **once** POST /v1/auth/bootstrap with header X-Bootstrap-Token and JSON {"email":"administrator@example.com","password":"unique-long-passphrase"}. It refuses to register another admin once one exists. Remove the BOOTSTRAP_TOKEN secret afterward. No bootstrap secret or administrator password belongs in Git or Android.

Passwords are stored as salted PBKDF2-HMAC-SHA256 (210,000 iterations). 8-hour bearer tokens are random; only SHA-256 token hashes are stored in D1. Logout deletes its stored session. Failed logins are limited to five per email/IP per 15-minute window via D1; distributed attackers may require additional Cloudflare rate limiting. Employee contact fields are public only when active and publicVisible=true. Inactive slugs return 404. All admin endpoints use Authorization: Bearer <accessToken>.

## Android integration
Use HTTPS base URL above, Retrofit/OkHttp with application/json. POST /v1/auth/login and retain accessToken in protected app storage, not source or APK. Supply bearer token only to admin requests. Handle 401 by reauthentication, 409 for duplicate slug, 422 for validation, 429 for rate limits. On logout send POST /v1/auth/logout and clear local token. QR/NFC points to GET /p/:slug. Never cache credentials offline as employment proof; always check public URL online.

## Deployment
From backend/: npm install; npx wrangler d1 migrations apply alsama-digital-id --remote; npx wrangler deploy. Public Worker URL is independent of alsamatourscr.com DNS. CORS_ORIGIN defaults to https://alsamatourscr.com for browser requests; native Android HTTP clients do not need CORS. Never commit .dev.vars, .env, access tokens or passwords. Free tier has Cloudflare quotas and is not guaranteed free when quotas or plan change.

## Data privacy
Photographs use optional external HTTPS URLs; no image-upload endpoint is provided. publicVisible=false suppresses email and phone in JSON and HTML; vCard returns 404. The company must authorize publication of employee contact details. The seeded Sebastian entry is publicVisible=true as requested.
