CREATE TABLE partner_address (
    partner_id     INTEGER REFERENCES partner,
    json           TEXT,
    location       TEXT NOT NULL,
    location_extra TEXT NOT NULL,
    lat            REAL,
    lon            REAL,
    ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (partner_id, location, location_extra)
);

CREATE TRIGGER partner_address_tsupdate
    AFTER UPDATE
    ON partner_address
    FOR EACH ROW
BEGIN
    UPDATE partner_address SET ts = CURRENT_TIMESTAMP WHERE rowid = NEW.rowid;
END;


insert into partner_address
select id, null, location, location_extra, latitude, longitude, ts
from partner
WHERE latitude IS NOT NULL OR location IS NOT NULL;

DROP VIEW IF EXISTS user_partner_feedback_view;
DROP VIEW IF EXISTS user_view;

ALTER TABLE partner DROP COLUMN location;
ALTER TABLE partner DROP COLUMN location_extra;
ALTER TABLE partner DROP COLUMN latitude;
ALTER TABLE partner DROP COLUMN longitude;

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
                 JOIN int_enum e ON gb.enum_id = e.id AND e.type = 'fbgbtype'
        WHERE gb.fbid = fb.id)     AS good,

       -- Bad: enum names (bad=1)
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM user_partner_feedback_bad gb
                 JOIN int_enum e ON gb.enum_id = e.id AND e.type = 'fbgbtype'
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
