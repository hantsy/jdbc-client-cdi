-- Idempotent so the extension can run more than once against the same database.
DELETE FROM engineers;
INSERT INTO engineers (id, name) VALUES (1, 'Ada');
INSERT INTO engineers (id, name) VALUES (2, 'Grace');
