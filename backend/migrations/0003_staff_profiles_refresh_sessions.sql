-- Role-like employee profile category. Administrative permissions remain in admins,
-- never determined by a client-provided value or job title alone.
ALTER TABLE employees ADD COLUMN profile_type TEXT NOT NULL DEFAULT 'sales'
  CHECK(profile_type IN ('sales','driver','admin','other'));
UPDATE employees SET profile_type='admin'
  WHERE lower(email) IN (SELECT lower(email) FROM admins);

-- Persist authentication across short-lived access token expiry.
-- Only SHA-256 hashes of refresh credentials are stored server-side.
CREATE TABLE IF NOT EXISTS refresh_sessions (
  token_hash TEXT PRIMARY KEY NOT NULL,
  actor_type TEXT NOT NULL CHECK(actor_type IN ('admin','employee')),
  actor_id TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_actor ON refresh_sessions(actor_type,actor_id);
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_exp ON refresh_sessions(expires_at);
