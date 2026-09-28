-- Fails on the second statement so the whole transaction has to roll back.
INSERT INTO applied (name) VALUES ('broken');
INSERT INTO table_that_does_not_exist (id) VALUES (1);
