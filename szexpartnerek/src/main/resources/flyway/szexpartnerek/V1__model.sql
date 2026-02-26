PRAGMA foreign_keys = ON;

-- ENUM table for all referenced types
CREATE TABLE int_enum
(
    id       TINYINT NOT NULL,
    type     TEXT    NOT NULL, -- e.g. 'properties', 'likes', 'looking', 'massage', 'answers'
    name     TEXT    NOT NULL,
    PRIMARY KEY (id, type)
);

-- Create user tables (not the advertisers, but the people who use services)
CREATE TABLE user
(
    id     INTEGER PRIMARY KEY,
    name   TEXT NOT NULL,
    age    TEXT,
    height INTEGER,
    weight INTEGER,
    size   INTEGER,
    gender TEXT,
    regd   DATE,
    ts     DATETIME DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO user
VALUES (0, 'Törölt', null, null, null, null, null, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
CREATE TRIGGER update_user_ingestion_date
    AFTER UPDATE
    ON user
    FOR EACH ROW
    WHEN NEW.ts = OLD.ts
BEGIN
    UPDATE user SET ts = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
CREATE TABLE tmp_user_like
(
    user_id INTEGER NOT NULL REFERENCES user (id),
    like    TEXT    NOT NULL,
    PRIMARY KEY (user_id, like)
);
-- hu.detox.szexpartnerek.Main partner table
CREATE TABLE partner
(
    id              INTEGER PRIMARY KEY,
    call_number     TEXT,          -- nullable if inactive
    name            TEXT NOT NULL,
    pass            TEXT,
    about           TEXT,
    active_info     TEXT NOT NULL, -- date, true or false
    expect          TEXT,
    age             TEXT,          -- can be "25+"
    height          SMALLINT,
    weight          SMALLINT,
    breast          SMALLINT,
    waist           SMALLINT,
    hips            SMALLINT,
    location        TEXT,
    location_extra  TEXT,
    latitude        REAL,
    longitude       REAL,
    looking_age_min SMALLINT,
    looking_age_max SMALLINT,
    ts              DATETIME DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO partner(id, name, active_info)
VALUES (0, 'Törölt', 'false');

CREATE TRIGGER update_partner_ingestion_date
    AFTER UPDATE
    ON partner
    FOR EACH ROW
    WHEN NEW.ts = OLD.ts
BEGIN
    UPDATE partner SET ts = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
-- Phone props: links phone to int_enum (props)
CREATE TABLE partner_phone_prop
(
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    enum_id    TINYINT NOT NULL,
    PRIMARY KEY (partner_id, enum_id)
);


-- Partner props: links partner to int_enum (props)
CREATE TABLE partner_prop
(
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    enum_id    TINYINT NOT NULL,
    PRIMARY KEY (partner_id, enum_id)
);

-- Open hours table
CREATE TABLE partner_open_hour
(
    id         INTEGER PRIMARY KEY,
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    onday      TINYINT NOT NULL, -- 0=Monday, 6=Sunday
    hours      TEXT    NOT NULL
);

-- Languages table
CREATE TABLE partner_lang
(
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    lang       TEXT    NOT NULL,
    PRIMARY KEY (partner_id, lang)
);

-- Answers table: one partner can have many answers, each answer is linked to int_enum (answers)
CREATE TABLE partner_answer
(
    id         INTEGER PRIMARY KEY,
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    enum_id    TINYINT NOT NULL,
    answer     TEXT    NOT NULL
);

-- Looking table: links partner to int_enum (looking)
CREATE TABLE partner_looking
(
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    enum_id    TINYINT NOT NULL,
    PRIMARY KEY (partner_id, enum_id)
);

-- Massages table: links partner to int_enum (massage)
CREATE TABLE partner_massage
(
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    enum_id    TINYINT NOT NULL,
    PRIMARY KEY (partner_id, enum_id)
);

-- Likes table: links partner to int_enum (likes) with status (no, yes, ask)
CREATE TABLE partner_like
(
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    enum_id    TINYINT NOT NULL,
    option     TEXT    NOT NULL CHECK (option IN ('no', 'yes', 'ask')),
    PRIMARY KEY (partner_id, enum_id)
);


-- Images table: one partner can have many images
CREATE TABLE partner_img
(
    id         INTEGER PRIMARY KEY,
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    ondate     DATE, -- nullable
    path       TEXT    NOT NULL
);

-- Activity log table
CREATE TABLE partner_activity
(
    id          INTEGER PRIMARY KEY,
    partner_id  INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    ondate      DATE    NOT NULL,
    description TEXT    NOT NULL
);

-- partner_prop: type = 'properties'
CREATE TRIGGER ppropetchk_insert
    BEFORE INSERT ON partner_prop
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'partner_prop: attempted to insert enum_id not found or not unique for type=properties')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'properties') != 1;
END;
CREATE TRIGGER ppropetchk_update
    BEFORE UPDATE ON partner_prop
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'partner_prop: attempted to update enum_id not found or not unique for type=properties')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'properties') != 1;
END;

-- partner_answer: type = 'answers'
CREATE TRIGGER paetchk_insert
    BEFORE INSERT ON partner_answer
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'partner_answer: attempted to insert enum_id not found or not unique for type=answers')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'answers') != 1;
END;
CREATE TRIGGER paetchk_update
    BEFORE UPDATE ON partner_answer
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'partner_answer: attempted to update enum_id not found or not unique for type=answers')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'answers') != 1;
END;

-- partner_looking: type = 'looking'
CREATE TRIGGER plooketchk_insert
    BEFORE INSERT ON partner_looking
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'partner_looking: attempted to insert enum_id not found or not unique for type=looking')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'looking') != 1;
END;
CREATE TRIGGER plooketchk_update
    BEFORE UPDATE ON partner_looking
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'partner_looking: attempted to update enum_id not found or not unique for type=looking')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'looking') != 1;
END;

-- partner_massage: type = 'massage'
CREATE TRIGGER pmassetchk_insert
    BEFORE INSERT ON partner_massage
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'partner_massage: attempted to insert enum_id not found or not unique for type=massage')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'massage') != 1;
END;
CREATE TRIGGER pmassetchk_update
    BEFORE UPDATE ON partner_massage
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'partner_massage: attempted to update enum_id not found or not unique for type=massage')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'massage') != 1;
END;

-- partner_like: type = 'likes'
CREATE TRIGGER plikeetchk_insert
    BEFORE INSERT ON partner_like
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'partner_like: attempted to insert enum_id not found or not unique for type=likes')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'likes') != 1;
END;
CREATE TRIGGER plikeetchk_update
    BEFORE UPDATE ON partner_like
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'partner_like: attempted to update enum_id not found or not unique for type=likes')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'likes') != 1;
END;

-- partner_phone_prop: type = 'properties'
CREATE TRIGGER pphonepetchk_insert
    BEFORE INSERT ON partner_phone_prop
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'partner_phone_prop: attempted to insert enum_id not found or not unique for type=properties')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'properties') != 1;
END;
CREATE TRIGGER pphonepetchk_update
    BEFORE UPDATE ON partner_phone_prop
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'partner_phone_prop: attempted to update enum_id not found or not unique for type=properties')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'properties') != 1;
END;

CREATE TABLE IF NOT EXISTS partner_list
(
    tag   TEXT    NOT NULL,
    partner_id    INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    name  TEXT    NOT NULL,
    age   TEXT,
    image TEXT,
    PRIMARY KEY (tag, partner_id)
);

CREATE VIEW partner_view AS
SELECT p.*,
       -- All props
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id AND e.type = 'properties'
        WHERE pp.partner_id = p.id)    AS properties,
       -- All likes
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_like pl
                 JOIN int_enum e ON pl.enum_id = e.id AND e.type = 'likes'
        WHERE pl.partner_id = p.id)    AS likes,
       -- All massages
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_massage pm
                 JOIN int_enum e ON pm.enum_id = e.id AND e.type = 'massage'
        WHERE pm.partner_id = p.id)    AS massages,
       -- All languages
       (SELECT GROUP_CONCAT(plang.lang, ', ')
        FROM partner_lang plang
        WHERE plang.partner_id = p.id) AS languages,
       -- All looking
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_looking plook
                 JOIN int_enum e ON plook.enum_id = e.id AND e.type = 'looking'
        WHERE plook.partner_id = p.id) AS looking,
       -- All open hours
       (SELECT GROUP_CONCAT(onday || ': ' || hours, ', ')
        FROM partner_open_hour poh
        WHERE poh.partner_id = p.id)   AS open_hours
FROM partner p;

-- Feedback tables
CREATE TABLE user_partner_feedback
(
    id         INTEGER PRIMARY KEY,
    user_id    INTEGER  NOT NULL REFERENCES user (id) ON DELETE CASCADE,
    enum_id    INTEGER  NOT NULL,
    partner_id INTEGER REFERENCES partner (id) ON DELETE CASCADE,
    name       TEXT,
    age        TINYINT,
    after_name TEXT,
    useful     INTEGER  NOT NULL,
    log        DATETIME NOT NULL,
    ts         DATETIME default CURRENT_TIMESTAMP
);

CREATE TRIGGER update_upfeedback_ingestion_date
    AFTER UPDATE
    ON user_partner_feedback
    FOR EACH ROW
    WHEN NEW.ts = OLD.ts
BEGIN
    UPDATE user_partner_feedback SET ts = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

CREATE TABLE user_partner_feedback_rating
(
    fbid    INTEGER NOT NULL REFERENCES user_partner_feedback (id) ON DELETE CASCADE,
    enum_id INTEGER NOT NULL, -- references int_enum(id) where int_enum.type='fbrtype'
    val     TINYINT,          -- nullable
    PRIMARY KEY (fbid, enum_id)
);

CREATE TABLE user_partner_feedback_bad
(
    fbid    INTEGER NOT NULL REFERENCES user_partner_feedback (id) ON DELETE CASCADE,
    enum_id INTEGER NOT NULL, -- references int_enum(id) where int_enum.type='fbbadtype'
    PRIMARY KEY (fbid, enum_id)
);

CREATE TABLE user_partner_feedback_good
(
    fbid    INTEGER NOT NULL REFERENCES user_partner_feedback (id) ON DELETE CASCADE,
    enum_id INTEGER NOT NULL, -- references int_enum(id) where int_enum.type='fbgoodtype'
    PRIMARY KEY (fbid, enum_id)
);

CREATE TABLE user_partner_feedback_details
(
    fbid    INTEGER NOT NULL REFERENCES user_partner_feedback (id) ON DELETE CASCADE,
    enum_id INTEGER NOT NULL, -- references int_enum(id) where int_enum.type='fbdtype'
    val     TEXT    NOT NULL,
    PRIMARY KEY (fbid, enum_id)
);

-- user_partner_feedback: type = 'fbtype'
CREATE TRIGGER upfbechk_insert
    BEFORE INSERT ON user_partner_feedback
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'user_partner_feedback: attempted to insert enum_id not found or not unique for type=fbtype')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'fbtype') != 1;
END;
CREATE TRIGGER upfbechk_update
    BEFORE UPDATE ON user_partner_feedback
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'user_partner_feedback: attempted to update enum_id not found or not unique for type=fbtype')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'fbtype') != 1;
END;

-- user_partner_feedback_rating: type = 'fbrtype'
CREATE TRIGGER upfb_ratingechk_insert
    BEFORE INSERT ON user_partner_feedback_rating
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'user_partner_feedback_rating: attempted to insert enum_id not found or not unique for type=fbrtype')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'fbrtype') != 1;
END;
CREATE TRIGGER upfb_ratingechk_update
    BEFORE UPDATE ON user_partner_feedback_rating
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'user_partner_feedback_rating: attempted to update enum_id not found or not unique for type=fbrtype')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'fbrtype') != 1;
END;

-- user_partner_feedback_good: type = 'fbgoodtype'
CREATE TRIGGER upfb_gechk_insert
    BEFORE INSERT ON user_partner_feedback_good
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'user_partner_feedback_good: attempted to insert enum_id not found or not unique for type=fbgoodtype')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'fbgoodtype') != 1;
END;
CREATE TRIGGER upfb_gechk_update
    BEFORE UPDATE ON user_partner_feedback_good
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'user_partner_feedback_good: attempted to update enum_id not found or not unique for type=fbgoodtype')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'fbgoodtype') != 1;
END;

-- user_partner_feedback_bad: type = 'fbbadtype'
CREATE TRIGGER upfb_bechk_insert
    BEFORE INSERT ON user_partner_feedback_bad
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'user_partner_feedback_bad: attempted to insert enum_id not found or not unique for type=fbbadtype')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'fbbadtype') != 1;
END;
CREATE TRIGGER upfb_bechk_update
    BEFORE UPDATE ON user_partner_feedback_bad
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'user_partner_feedback_bad: attempted to update enum_id not found or not unique for type=fbbadtype')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'fbbadtype') != 1;
END;

-- user_partner_feedback_details: type = 'fbdtype'
CREATE TRIGGER upfb_detailsechk_insert
    BEFORE INSERT ON user_partner_feedback_details
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'user_partner_feedback_details: attempted to insert enum_id not found or not unique for type=fbdtype')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'fbdtype') != 1;
END;
CREATE TRIGGER upfb_detailsechk_update
    BEFORE UPDATE ON user_partner_feedback_details
    FOR EACH ROW
BEGIN
    SELECT RAISE(ABORT, 'user_partner_feedback_details: attempted to update enum_id not found or not unique for type=fbdtype')
    WHERE (SELECT COUNT(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'fbdtype') != 1;
END;

CREATE VIEW user_partner_feedback_view AS
WITH ratings AS (SELECT fb.id,
                        fb.partner_id,
                        GROUP_CONCAT(e.name || ': ' || r.val, ', ') AS rating
                 FROM user_partner_feedback fb
                          LEFT JOIN user_partner_feedback_rating r ON r.fbid = fb.id
                          LEFT JOIN int_enum e ON r.enum_id = e.id AND e.type = 'fbrtype'
                 GROUP BY fb.id),
     important_props AS (SELECT fb.id,
                                COUNT(DISTINCT e.name) AS prop_count
                         FROM user_partner_feedback fb
                                  LEFT JOIN partner_prop pp ON pp.partner_id = fb.partner_id
                                  LEFT JOIN int_enum e ON pp.enum_id = e.id AND e.type = 'properties'
                         WHERE e.name IN (
                                          'ELLENORZOTT_TELEFON', 'ELLENORZOTT_KEPEK', 'ELLENORZOTT_KOR',
                                          'VAN_FRISS_KEPE', 'VAN_ARCOS_KEPE', 'NINCS_PANASZ', 'GYAKRAN_BELEP',
                                          'STABIL_HIRDETO', 'VALTOZATLAN_VAROS', 'VALTOZATLAN_NEV', 'VALTOZATLAN_SZAM'
                             )
                         GROUP BY fb.id),
     max_props AS (SELECT 11 AS max_count)
SELECT fb.id as rowid,
       fb.user_id, fb.enum_id, fb.partner_id,
       fb.useful,
       rt.rating,

       -- Good: enum names (bad=0)
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM user_partner_feedback_good gb
                 JOIN int_enum e ON gb.enum_id = e.id AND e.type = 'fbgoodtype'
        WHERE gb.fbid = fb.id)     AS good,

       -- Bad: enum names (bad=1)
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM user_partner_feedback_bad gb
                 JOIN int_enum e ON gb.enum_id = e.id AND e.type = 'fbbadtype'
        WHERE gb.fbid = fb.id)     AS bad,

       -- Details: each as "ENUMNAME: val", separated by double newline
       (SELECT GROUP_CONCAT(e.name || ': ' || d.val, CHAR(10) || CHAR(10))
        FROM user_partner_feedback_details d
                 JOIN int_enum e ON d.enum_id = e.id AND e.type = 'fbdtype'
        WHERE d.fbid = fb.id) AS details,
    fb.log, fb.ts
FROM user_partner_feedback fb
         LEFT JOIN partner p ON fb.partner_id = p.id
         JOIN ratings rt ON fb.id = rt.id
         LEFT JOIN important_props ip ON fb.id = ip.id
         JOIN max_props mp
WHERE rt.rating IS NOT NULL
  AND fb.partner_id > 0;

CREATE TRIGGER ienum_delete_cascade
    BEFORE DELETE ON int_enum
    FOR EACH ROW
BEGIN
    DELETE FROM partner_prop WHERE enum_id = OLD.id AND 'properties' = OLD.type;
    DELETE FROM partner_phone_prop WHERE enum_id = OLD.id AND 'properties' = OLD.type;
    DELETE FROM partner_answer WHERE enum_id = OLD.id AND 'answers' = OLD.type;
    DELETE FROM partner_looking WHERE enum_id = OLD.id AND 'looking' = OLD.type;
    DELETE FROM partner_massage WHERE enum_id = OLD.id AND 'massage' = OLD.type;
    DELETE FROM partner_like WHERE enum_id = OLD.id AND 'likes' = OLD.type;
    DELETE FROM user_partner_feedback WHERE enum_id = OLD.id AND 'fbtype' = OLD.type;
    DELETE FROM user_partner_feedback_rating WHERE enum_id = OLD.id AND 'fbrtype' = OLD.type;
    DELETE FROM user_partner_feedback_good WHERE enum_id = OLD.id AND 'fbgoodtype' = OLD.type;
    DELETE FROM user_partner_feedback_bad WHERE enum_id = OLD.id AND 'fbbadtype' = OLD.type;
    DELETE FROM user_partner_feedback_details WHERE enum_id = OLD.id AND 'fbdtype' = OLD.type;
END;
