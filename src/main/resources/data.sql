-- =====================================================================
-- data.sql (MariaDB) — runs after schema.sql on every Spring Boot start.
--
-- Uses the same custom statement separator as schema.sql (see schema.sql header and
-- application.yml: spring.sql.init.separator) — that setting is global
-- to both files, so this one has to follow it too even though nothing
-- here actually needs it for multi-statement bodies. Caution: this means
-- no value inserted here may ever contain that separator as a literal substring, or
-- Spring's script splitter will cut the statement there by mistake.
--
-- Uses INSERT IGNORE so re-running on an already-seeded database is a
-- no-op rather than a startup failure. The demo bride_side/groom_side
-- admins and sample guest are optional fixtures — delete that block
-- for a production deployment and keep only the 8 tables + super admin.
-- =====================================================================

-- The head table has a NULL table_number (it isn't numbered like the
-- others), and MariaDB's UNIQUE index treats NULLs as distinct from each
-- other — so INSERT IGNORE alone would silently insert a duplicate head
-- table on every restart. Guard it explicitly instead.
INSERT INTO seating_tables (id, side, table_number, capacity)
SELECT UUID(), 'head', NULL, 2
WHERE NOT EXISTS (SELECT 1 FROM seating_tables WHERE side = 'head')$$

-- Starter tables for each side — real UNIQUE(side, table_number) values,
-- so INSERT IGNORE correctly no-ops on repeat runs. Admins add more of
-- their own via the app from here on (POST /api/seating/tables) — this
-- is just a reasonable starting point, not a fixed total.
INSERT IGNORE INTO seating_tables (id, side, table_number, capacity) VALUES
    (UUID(), 'bride', 1, 12), (UUID(), 'bride', 2, 12),
    (UUID(), 'groom', 1, 12), (UUID(), 'groom', 2, 12)$$

-- Super admin bootstrap account -----------------------------------------
-- TEST HASH ONLY — replace before deploying anywhere reachable.
-- This corresponds to plaintext password: test-password-123
INSERT IGNORE INTO admins (id, username, password_hash, role, is_active)
VALUES (UUID(), 'super_admin', '$2b$10$NqrXhq5NIG7gUHQ72yYjeuRwRmGB/lcKO6GPHGd8AFWw6f81VAyna', 'super_admin', 1)$$

-- ---------------------------------------------------------------------
-- Optional demo fixtures — safe to delete for production
-- ---------------------------------------------------------------------

INSERT IGNORE INTO admins (id, username, password_hash, role, is_active, created_by, side)
SELECT UUID(), 'bride_side', '$2b$10$NqrXhq5NIG7gUHQ72yYjeuRwRmGB/lcKO6GPHGd8AFWw6f81VAyna', 'admin', 1, id, 'bride'
FROM admins WHERE username = 'super_admin'$$

INSERT IGNORE INTO admins (id, username, password_hash, role, is_active, created_by, side)
SELECT UUID(), 'groom_side', '$2b$10$NqrXhq5NIG7gUHQ72yYjeuRwRmGB/lcKO6GPHGd8AFWw6f81VAyna', 'admin', 1, id, 'groom'
FROM admins WHERE username = 'super_admin'$$

-- Backfill side for bride_side/groom_side on a database that already had
-- these rows from before the side column existed — INSERT IGNORE above
-- only fires for a brand-new row, it never touches an existing one, so on
-- an upgraded (not fresh) database these would otherwise be stuck at NULL
-- forever, silently breaking every side-restricted feature (seating,
-- table management) despite schema.sql/data.sql having "correctly" run.
UPDATE admins SET side = 'bride' WHERE username = 'bride_side' AND side IS NULL$$
UPDATE admins SET side = 'groom' WHERE username = 'groom_side' AND side IS NULL$$

INSERT IGNORE INTO guests (id, admin_id, display_name, is_group, party_size, group_members, landing_slug)
SELECT UUID(), id, 'Jane Doe', 0, 1, NULL, 'demo-slug-jane-doe'
FROM admins WHERE username = 'bride_side'$$

INSERT IGNORE INTO guests (id, admin_id, display_name, is_group, party_size, group_members, landing_slug)
SELECT UUID(), id, 'The Miller Family', 1, 4, 'Tom Miller,Ann Miller,Lucy Miller,Ben Miller', 'demo-slug-miller-family'
FROM admins WHERE username = 'bride_side'$$

INSERT IGNORE INTO guests (id, admin_id, display_name, is_group, party_size, group_members, landing_slug)
SELECT UUID(), id, 'Carlos Rivera', 0, 1, NULL, 'demo-slug-carlos-rivera'
FROM admins WHERE username = 'groom_side'$$