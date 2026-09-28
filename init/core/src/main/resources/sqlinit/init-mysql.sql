CREATE TABLE db_migrations (
    version INT PRIMARY KEY,
    description VARCHAR(200) NOT NULL,
    script VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL,
    installed_on DATETIME NOT NULL,
    error_message VARCHAR(1000)
)
