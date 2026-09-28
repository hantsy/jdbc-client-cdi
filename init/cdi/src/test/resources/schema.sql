-- Idempotent so the extension can run more than once against the same database.
CREATE TABLE IF NOT EXISTS engineers (
    id   INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);
