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

-- Samarkand hall (groom side only): tables 1B..8B. Seeded exactly once,
-- ever — guarded by schema_migrations rather than INSERT IGNORE alone, so
-- a table the groom side later removes through the app doesn't silently
-- reappear on the next restart.
INSERT IGNORE INTO seating_tables (id, hall, side, table_number, capacity)
SELECT UUID(), 'samarkand', 'groom', n.num, 14
FROM (SELECT 1 AS num UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8) n
WHERE NOT EXISTS (SELECT 1 FROM schema_migrations WHERE migration_name = 'samarkand_tables_v1')$$

INSERT IGNORE INTO schema_migrations (migration_name) VALUES ('samarkand_tables_v1')$$

-- Samarkand tables seat 14, not 12 — bumps the tables seeded above on
-- databases that already had them at 12. Runs once, like the seeding, so
-- a capacity later changed by hand isn't reset on the next restart.
UPDATE seating_tables SET capacity = 14
WHERE hall = 'samarkand' AND capacity = 12
  AND NOT EXISTS (SELECT 1 FROM schema_migrations WHERE migration_name = 'samarkand_capacity_14')$$

INSERT IGNORE INTO schema_migrations (migration_name) VALUES ('samarkand_capacity_14')$$

-- Playlist ---------------------------------------------------------------
-- Likes freeze at 20:50 on each celebration's evening (venue time, UTC+5 —
-- see schema.sql); the two most-liked songs are played after that. Staff
-- can move the time from the DJ page. INSERT IGNORE: a changed time stays.
INSERT IGNORE INTO playlist_settings (hall, likes_close_at) VALUES
    ('tashkent', '2026-10-02 20:50:00'),
    ('samarkand', '2026-10-10 20:50:00')$$

-- The band's repertoire, in both halls. Seeded exactly once, like the
-- Samarkand tables, so a song removed through the app stays removed.
INSERT IGNORE INTO songs (id, hall, artist, title, language)
SELECT UUID(), h.hall, s.artist, s.title, s.language
FROM (SELECT 'tashkent' AS hall UNION ALL SELECT 'samarkand') h
CROSS JOIN (
    SELECT 'AC/DC' AS artist, 'Back in Black' AS title, 'en' AS language
    UNION ALL SELECT 'AC/DC', 'Highway to Hell', 'en'
    UNION ALL SELECT 'Aerosmith', 'I Don’t Want to Miss a Thing', 'en'
    UNION ALL SELECT 'Aerosmith', 'Dream On', 'en'
    UNION ALL SELECT 'Агутин', 'На сиреневой луне', 'ru'
    UNION ALL SELECT 'Ария', 'Я свободен', 'ru'
    UNION ALL SELECT 'Backstreet Boys', 'Everybody', 'en'
    UNION ALL SELECT 'Backstreet Boys', 'Show Me the Meaning of Being Lonely', 'en'
    UNION ALL SELECT 'Beatles', 'Come Together', 'en'
    UNION ALL SELECT 'Beatles', 'Hey Jude', 'en'
    UNION ALL SELECT 'Beatles', 'While My Guitar Gently Weeps', 'en'
    UNION ALL SELECT 'Би-2', 'Вечная призрачная встречная', 'ru'
    UNION ALL SELECT 'Би-2', 'Полковнику никто не пишет', 'ru'
    UNION ALL SELECT 'Billie Eilish', 'Bad Guy', 'en'
    UNION ALL SELECT 'Bob Dylan', 'Knockin’ on Heaven’s Door', 'en'
    UNION ALL SELECT 'Bob Marley', 'No Woman, No Cry', 'en'
    UNION ALL SELECT 'Bruno Mars', 'APT.', 'en'
    UNION ALL SELECT 'Bruno Mars', 'Just the Way You Are', 'en'
    UNION ALL SELECT 'Bruno Mars', 'The Lazy Song', 'en'
    UNION ALL SELECT 'Bruno Mars', 'Locked Out of Heaven', 'en'
    UNION ALL SELECT 'Coldplay', 'Adventure of a Lifetime', 'en'
    UNION ALL SELECT 'Coldplay', 'Yellow', 'en'
    UNION ALL SELECT 'Coolio', 'Gangsta’s Paradise', 'en'
    UNION ALL SELECT 'Cranberries', 'Zombie', 'en'
    UNION ALL SELECT 'Dado', 'Allora', 'uz'
    UNION ALL SELECT 'Dado', 'Yuragim', 'uz'
    UNION ALL SELECT 'David Guetta', 'Love Is Gone', 'en'
    UNION ALL SELECT 'DNCE', 'Cake by the Ocean', 'en'
    UNION ALL SELECT 'Ed Sheeran', 'Azizam', 'en'
    UNION ALL SELECT 'Ed Sheeran', 'Shape of You', 'en'
    UNION ALL SELECT 'Elton John', 'Sacrifice', 'en'
    UNION ALL SELECT 'Billy Joel', 'Just the Way You Are', 'en'
    UNION ALL SELECT 'Eminem', 'Lose Yourself', 'en'
    UNION ALL SELECT 'Eric Clapton', 'Change the World', 'en'
    UNION ALL SELECT 'Gorillaz', 'Clint Eastwood', 'en'
    UNION ALL SELECT 'Gorillaz', 'Feel Good Inc', 'en'
    UNION ALL SELECT 'Градусы', 'Кто ты', 'ru'
    UNION ALL SELECT 'Градусы', 'Режиссёр', 'ru'
    UNION ALL SELECT 'Green Day', 'Boulevard of Broken Dreams', 'en'
    UNION ALL SELECT 'Green Day', 'Wake Me Up When September Ends', 'en'
    UNION ALL SELECT 'Guns N’ Roses', 'Sweet Child O’ Mine', 'en'
    UNION ALL SELECT 'Harry Styles', 'Sign of the Times', 'en'
    UNION ALL SELECT 'Jamiroquai', 'Love Foolosophy', 'en'
    UNION ALL SELECT 'Lenny Kravitz', 'I Belong to You', 'en'
    UNION ALL SELECT 'Lenny Kravitz', 'Low', 'en'
    UNION ALL SELECT 'Limp Bizkit', 'Behind Blue Eyes', 'en'
    UNION ALL SELECT 'Limp Bizkit', 'Take a Look Around', 'en'
    UNION ALL SELECT 'Linkin Park', 'In the End', 'en'
    UNION ALL SELECT 'Linkin Park', 'Numb', 'en'
    UNION ALL SELECT 'Lou Bega', 'Mambo No. 5', 'en'
    UNION ALL SELECT 'Måneskin', 'Beggin’', 'en'
    UNION ALL SELECT 'Maroon 5', 'Sunday Morning', 'en'
    UNION ALL SELECT 'Maroon 5', 'This Love', 'en'
    UNION ALL SELECT 'Metallica', 'Nothing Else Matters', 'en'
    UNION ALL SELECT 'Меладзе', 'Салют, Вера', 'ru'
    UNION ALL SELECT 'Меладзе', 'Самба белого мотылька', 'ru'
    UNION ALL SELECT 'Монатик', 'Давай танцуй', 'ru'
    UNION ALL SELECT 'Mor ve Ötesi', 'Bir Derdim Var', 'tr'
    UNION ALL SELECT 'Mor ve Ötesi', 'Deli', 'tr'
    UNION ALL SELECT 'Muse', 'Handler', 'en'
    UNION ALL SELECT 'Muse', 'Knights of Cydonia', 'en'
    UNION ALL SELECT 'Muse', 'Supermassive Black Hole', 'en'
    UNION ALL SELECT 'Мумий Тролль', 'Медведица', 'ru'
    UNION ALL SELECT 'Мумий Тролль', 'Невеста', 'ru'
    UNION ALL SELECT 'Nirvana', 'Come as You Are', 'en'
    UNION ALL SELECT 'Nirvana', 'Smells Like Teen Spirit', 'en'
    UNION ALL SELECT 'Oasis', 'Don’t Look Back in Anger', 'en'
    UNION ALL SELECT 'Oasis', 'Wonderwall', 'en'
    UNION ALL SELECT 'Lil Nas X', 'Old Town Road', 'en'
    UNION ALL SELECT 'Phil Collins', 'Another Day in Paradise', 'en'
    UNION ALL SELECT 'Pink Floyd', 'Another Brick in the Wall', 'en'
    UNION ALL SELECT 'Police', 'Every Breath You Take', 'en'
    UNION ALL SELECT 'Police', 'Message in a Bottle', 'en'
    UNION ALL SELECT 'Police', 'Roxanne', 'en'
    UNION ALL SELECT 'Prince', 'Purple Rain', 'en'
    UNION ALL SELECT 'Robbie Williams', 'Angels', 'en'
    UNION ALL SELECT 'Queen', 'Another One Bites the Dust', 'en'
    UNION ALL SELECT 'Queen', 'The Show Must Go On', 'en'
    UNION ALL SELECT 'Quest Pistols', 'Белая стрекоза', 'ru'
    UNION ALL SELECT 'Radiohead', 'Creep', 'en'
    UNION ALL SELECT 'Rammstein', 'Sonne', 'de'
    UNION ALL SELECT 'Red Hot Chili Peppers', 'Californication', 'en'
    UNION ALL SELECT 'Red Hot Chili Peppers', 'Dani California', 'en'
    UNION ALL SELECT 'Sahar', 'Yomg’ir', 'uz'
    UNION ALL SELECT 'Тохир Садыков', 'Керак эмас', 'uz'
    UNION ALL SELECT 'Sting', 'Seven Days', 'en'
    UNION ALL SELECT 'Sting', 'Shape of My Heart', 'en'
    UNION ALL SELECT 'Stevie Wonder', 'Superstition', 'en'
    UNION ALL SELECT 'Stromae', 'Alors on danse', 'fr'
    UNION ALL SELECT 'System of a Down', 'Aerials', 'en'
    UNION ALL SELECT 'System of a Down', 'Chop Suey!', 'en'
    UNION ALL SELECT 'Tom Jones', 'Sex Bomb', 'en'
    UNION ALL SELECT 'U2', 'With or Without You', 'en'
    UNION ALL SELECT 'Виктор Цой', 'Группа крови', 'ru'
    UNION ALL SELECT 'Виктор Цой', 'Перемен', 'ru'
    UNION ALL SELECT 'Земляне', 'Трава у дома', 'ru'
    UNION ALL SELECT 'Звери', 'До скорой встречи', 'ru'
    UNION ALL SELECT 'Звери', 'Просто такая сильная любовь', 'ru'
    UNION ALL SELECT 'Звери', 'Районы-кварталы', 'ru'
    UNION ALL SELECT 'Britney Spears', '…Baby One More Time', 'en'
    UNION ALL SELECT 'Britney Spears', 'Toxic (rock cover)', 'en'
    UNION ALL SELECT 'Ласковый май', 'Седая ночь (рок-версия)', 'ru'
    UNION ALL SELECT 'Братья Грим', 'Хлопай ресницами', 'ru'
) s
WHERE NOT EXISTS (SELECT 1 FROM schema_migrations WHERE migration_name = 'playlist_songs_v1')$$

INSERT IGNORE INTO schema_migrations (migration_name) VALUES ('playlist_songs_v1')$$

-- Quests (Tashkent) -----------------------------------------------------
-- The evening's quests, with their reward in ducats. Each is proven with a
-- photo or video the guest uploads, which a banker checks before paying.
-- created_at is staggered so they list in this order. Seeded once, ever
-- (like the songs), so a quest admins later edit or delete stays that way.
INSERT INTO quests (id, hall, title, description, media_type, reward, created_at)
SELECT UUID(), 'tashkent', q.title, q.description, q.media_type, q.reward,
       CURRENT_TIMESTAMP - INTERVAL (100 - q.num) SECOND
FROM (
    SELECT 1 AS num,
           'Сделать снимок на полароид, вклеить его в альбом и написать пожелание или совет молодожёнам' AS title,
           'Сфотографируйте страницу альбома с вашим снимком и пожеланием.' AS description,
           'photo' AS media_type, 1 AS reward
    UNION ALL SELECT 2, 'Сделать групповое фото всего стола с самой смешной гримасой', NULL, 'photo', 1
    UNION ALL SELECT 3, 'Записать 10-секундное видео с гостем, у которого самые крутые танцевальные движения (кроме жениха и невесты)', NULL, 'video', 1
    UNION ALL SELECT 4, 'Пригласить на танец человека, с которым вы не знакомы, и сделать совместное селфи на танцполе', NULL, 'photo', 2
    UNION ALL SELECT 5, 'Нарисовать портрет молодожёнов за одну минуту', 'Подсказка: бумагу и маркеры можно найти в джунглях. Сфотографируйте готовый портрет.', 'photo', 2
    UNION ALL SELECT 6, 'Сделать фото, на котором в одном кадре есть представители трёх разных поколений', NULL, 'photo', 2
    UNION ALL SELECT 7, 'Найти гостя, который родился в том же месяце, что и вы, и сделать совместное селфи', NULL, 'photo', 3
    UNION ALL SELECT 8, 'Найти пару, которая в браке более 10 лет, и записать 15-секундное видео с их главным секретом счастливой семейной жизни', NULL, 'video', 3
    UNION ALL SELECT 9, 'Познакомить двух свободных людей со стороны жениха и со стороны невесты и сделать совместное фото втроём', NULL, 'photo', 5
) q
WHERE NOT EXISTS (SELECT 1 FROM schema_migrations WHERE migration_name = 'tashkent_quests_v1')$$

INSERT IGNORE INTO schema_migrations (migration_name) VALUES ('tashkent_quests_v1')$$

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

-- A demo DJ for the Tashkent hall: sees only that hall's playlist (dj.html).
INSERT IGNORE INTO admins (id, username, password_hash, role, is_active, created_by, hall)
SELECT UUID(), 'dj_tashkent', '$2b$10$NqrXhq5NIG7gUHQ72yYjeuRwRmGB/lcKO6GPHGd8AFWw6f81VAyna', 'dj', 1, id, 'tashkent'
FROM admins WHERE username = 'super_admin'$$

-- Demo bankers, one per hall: pay out guests' quest ducats (bank.html).
-- Each sets their own 4-digit PIN on that page after signing in.
INSERT IGNORE INTO admins (id, username, password_hash, role, is_active, created_by, hall)
SELECT UUID(), 'bank_tashkent', '$2b$10$NqrXhq5NIG7gUHQ72yYjeuRwRmGB/lcKO6GPHGd8AFWw6f81VAyna', 'banker', 1, id, 'tashkent'
FROM admins WHERE username = 'super_admin'$$

INSERT IGNORE INTO admins (id, username, password_hash, role, is_active, created_by, hall)
SELECT UUID(), 'bank_samarkand', '$2b$10$NqrXhq5NIG7gUHQ72yYjeuRwRmGB/lcKO6GPHGd8AFWw6f81VAyna', 'banker', 1, id, 'samarkand'
FROM admins WHERE username = 'super_admin'$$
