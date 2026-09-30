CREATE TABLE db_migrations (
    version NUMBER(10) PRIMARY KEY,
    description VARCHAR2(200) NOT NULL,
    script VARCHAR2(500) NOT NULL,
    status VARCHAR2(16) NOT NULL,
    installed_on TIMESTAMP NOT NULL,
    error_message VARCHAR2(1000)
)
