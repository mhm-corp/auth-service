CREATE UNIQUE INDEX IF NOT EXISTS idx_users_id ON users (id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users (email);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username ON users (username);