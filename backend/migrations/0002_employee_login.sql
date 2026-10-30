-- Employee email is a login identifier: it must be unique (case-insensitive).
CREATE UNIQUE INDEX IF NOT EXISTS idx_employees_unique_login_email
  ON employees(lower(email));
CREATE TABLE IF NOT EXISTS employee_credentials (
  employee_id TEXT PRIMARY KEY NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
  password_hash TEXT NOT NULL,
  password_salt TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS employee_invites (
  employee_id TEXT PRIMARY KEY NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
  code_hash TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS employee_sessions (
  token_hash TEXT PRIMARY KEY NOT NULL,
  employee_id TEXT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_employee_sessions_exp ON employee_sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_employee_sessions_employee ON employee_sessions(employee_id);
