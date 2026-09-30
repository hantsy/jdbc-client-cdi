CREATE TABLE db_migrations (
    version INT PRIMARY KEY,
    description NVARCHAR(200) NOT NULL,
    script NVARCHAR(500) NOT NULL,
    status NVARCHAR(16) NOT NULL,
    installed_on DATETIME2 NOT NULL,
    error_message NVARCHAR(1000)
)
