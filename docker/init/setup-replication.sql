-- Setup replication user and permissions
CREATE USER replicator REPLICATION LOGIN CONNECTION LIMIT 1 ENCRYPTED PASSWORD 'replicator123';

-- Create replication slot for standby
SELECT pg_create_physical_replication_slot('standby_slot');

-- Grant necessary permissions
GRANT CONNECT ON DATABASE filesync TO replicator;
GRANT USAGE ON SCHEMA public TO replicator;

-- Configure for streaming replication
ALTER SYSTEM SET wal_level = 'replica';
ALTER SYSTEM SET max_wal_senders = 3;
ALTER SYSTEM SET max_replication_slots = 3;
ALTER SYSTEM SET hot_standby = on;
ALTER SYSTEM SET archive_mode = on;
ALTER SYSTEM SET archive_command = 'test ! -f /var/lib/postgresql/data/archive/%f && cp %p /var/lib/postgresql/data/archive/%f';

-- Reload configuration
SELECT pg_reload_conf();
