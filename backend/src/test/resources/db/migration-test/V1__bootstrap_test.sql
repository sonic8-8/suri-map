CREATE TABLE bootstrap_migration_probe (
  id INTEGER PRIMARY KEY,
  label VARCHAR(64) NOT NULL
);

INSERT INTO bootstrap_migration_probe (id, label)
VALUES (1, 'flyway-test-profile');
