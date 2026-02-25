-- Partner related extensions
DROP VIEW IF EXISTS partner_ext_view;
DROP TABLE IF EXISTS partner_ext;

CREATE TABLE partner_ext AS
SELECT p.id as rowid,
       IIF(p.del,'false',p.active_info) as active_info,
       p.call_number,p.name,p.pass,p.about,p.expect,
       p.age,CONCAT(COALESCE(p.looking_age_min,18),'-',COALESCE(p.looking_age_max,999)) as ages,
       p.height,p.weight,p.breast,p.waist,p.hips,
       (SELECT COALESCE(json,
                        IIF(lat IS NULL, NULL, CONCAT(lat,',',lon)),
                        CONCAT(location, IIF(location_extra='', '', CONCAT('; ',location_extra))))
        FROM partner_address pa WHERE pa.partner_id=p.id ORDER BY ts DESC LIMIT 1) AS location,
       -- place: combine (CSAK_)WEBCAM_SZEX, CSAK_NALAD, CSAK_NALAM, NALAM_NALAD from props and AUTOS_KALAND, BULIBA, BUCSUBA, CSAK_WEBCAM_SZEX from looking
       (SELECT GROUP_CONCAT(placeval, ', ')
        FROM (SELECT max(replace(e.name, 'CSAK_', '')) AS placeval
              FROM main.partner_like pli
                       JOIN int_enum e ON pli.enum_id = e.id
              WHERE pli.partner_id = p.id
                AND e.type = 'likes'
                AND e.name LIKE '%WEBCAM%'
                AND pli.option != 'no'
                AND p.active_info='true'
              UNION ALL
              SELECT e.name AS placeval
              FROM partner_prop pp
                       JOIN int_enum e ON pp.enum_id = e.id
              WHERE pp.partner_id = p.id
                AND e.type = 'properties'
                AND e.name IN ('CSAK_NALAD', 'CSAK_NALAM', 'NALAM_NALAD')
              UNION ALL
              SELECT e.name AS placeval
              FROM partner_looking pl
                       JOIN int_enum e ON pl.enum_id = e.id
              WHERE pl.partner_id = p.id
                AND (e.type = 'looking'
                  AND e.name IN ('AUTOS_KALAND', 'BULIBA_IS', 'BUCSUBA', 'ESCORT_KISERET_IS')))
        order by placeval asc)                                  AS place,
       -- Haj prefix and postfix
       (SELECT GROUP_CONCAT(core, '_') AS hair
        FROM (SELECT REPLACE(REPLACE(e.name, '_HAJ', ''), 'HAJ_', '') AS core
              FROM partner_prop pp
                       JOIN int_enum e ON pp.enum_id = e.id
              WHERE pp.partner_id = p.id
                AND e.type = 'properties'
                AND (e.name LIKE 'HAJ_%' OR e.name LIKE '%_HAJ')
              ORDER BY e.name LIKE 'HAJ_%' DESC, e.name
              LIMIT 2))                                         AS hair,
       -- Eye (szem): remove _SZEM postfix
       (SELECT REPLACE(e.name, '_SZEM', '')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'properties'
          AND e.name LIKE '%_SZEM'
        LIMIT 1)                                                AS eyes,
       -- Breast (cici): remove _CICI postfix
       (SELECT REPLACE(e.name, '_CICI', '')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'properties'
          AND e.name LIKE '%_CICI'
        LIMIT 1)                                                AS breasts,
       -- Body type (alkat): remove _ALKAT postfix
       (SELECT REPLACE(e.name, '_ALKAT', '')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'properties'
          AND e.name LIKE '%_ALKAT'
        LIMIT 1)                                                AS body,
       -- Intim: remove INTIM_ prefix
       (SELECT REPLACE(e.name, 'INTIM_', '')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'properties'
          AND e.name LIKE 'INTIM_%'
        LIMIT 1)                                                AS intim,
       -- Orientation: HETERO, HOMO, BISZEX (no prefix/postfix)
       (SELECT e.name
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'properties'
          AND e.name IN ('HETERO', 'HOMO', 'BISZEX')
        LIMIT 1)                                                AS orientation,
       -- Gender: LANY, FIU, PAR, TRANSZSZEXUALIS (no prefix/postfix)
       (SELECT e.name
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'properties'
          AND e.name IN ('LANY', 'PAR', 'FIU', 'TRANSZSZEXUALIS')
        LIMIT 1)                                                AS gender,
       -- sex: combine DOMINA, SZEXPARTNER, AKTUS_VELEM_KIZART, MASSZAZS, CSAK_MASSZAZS from props
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'properties'
          AND (e.name LIKE '%DOMINA%'
            OR e.name IN ('SZEXPARTNER', 'AKTUS_VELEM_KIZART', 'MASSZAZS', 'CSAK_MASSZAZS'))
        ORDER BY e.name)                                        AS act,
       -- Aircond
       CASE
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'properties'
                          AND e.name = 'LEGKONDICIONALT_LAKAS') THEN 1
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'properties'
                          AND e.name = 'NEM_LEGKONDICIONALT_LAKAS') THEN 0
           ELSE NULL
           END                                                  AS aircond,
       -- Smoker
       CASE
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'properties'
                          AND e.name = 'DOHANYZOM') THEN 1
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'properties'
                          AND e.name = 'NEM_DOHANYZOM') THEN 0
           ELSE NULL
           END                                                  AS smoker,
       -- SMS
       CASE
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'properties'
                          AND e.name = 'SMSRE_VALASZOLOK') THEN 1
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'properties'
                          AND e.name = 'SMSRE_NEM_VALASZOLOK') THEN 0
           ELSE NULL
           END                                                  AS sms,
       -- length: join *_ORARA in looking, remove postfix
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_looking pl
                 JOIN int_enum e ON pl.enum_id = e.id
        WHERE pl.partner_id = p.id
          AND e.type = 'looking'
          AND (e.name LIKE '%_ORARA' OR e.name = 'TOBB_NAPRA')) AS lengths,
       -- francia: join FRANCIA_* in likes, remove prefix
       (SELECT GROUP_CONCAT(REPLACE(CONCAT(iif(pl.option='ask','?',''),e.name), 'FRANCIA_', ''), ', ')
        FROM partner_like pl
                 JOIN int_enum e ON pl.enum_id = e.id
        WHERE pl.partner_id = p.id
          AND e.type = 'likes'
          AND e.name LIKE 'FRANCIA_%' AND pl.option != 'no')                          AS french,
       -- client_type logic
       COALESCE(
           -- 1. If any of TOBBEST, NOT, FERFIT, PART in looking, use those
               (SELECT GROUP_CONCAT(e.name, ', ')
                FROM partner_looking pl
                         JOIN int_enum e ON pl.enum_id = e.id
                WHERE pl.partner_id = p.id
                  AND e.type = 'looking'
                  AND e.name IN ('TOBBEST', 'NOT', 'FERFIT', 'PART')),
           -- 2. If BISZEX and PAR in props, set to 'NOT, FERFIT'
               CASE
                   WHEN EXISTS (SELECT 1
                                FROM partner_prop pp
                                         JOIN int_enum e ON pp.enum_id = e.id
                                WHERE pp.partner_id = p.id
                                  AND e.type = 'properties'
                                  AND e.name = 'BISZEX')
                       AND EXISTS (SELECT 1
                                   FROM partner_prop pp
                                            JOIN int_enum e ON pp.enum_id = e.id
                                   WHERE pp.partner_id = p.id
                                     AND e.type = 'properties'
                                     AND e.name = 'PAR')
                       THEN 'NOT, FERFIT'
                   ELSE NULL
                   END,
           -- 3. HOMO/HETERO + FIU/LANY/TRANSZSZEXUALIS
               CASE
                   -- HOMO + FIU -> fiut
                   WHEN EXISTS (SELECT 1
                                FROM partner_prop pp
                                         JOIN int_enum e ON pp.enum_id = e.id
                                WHERE pp.partner_id = p.id
                                  AND e.type = 'properties'
                                  AND e.name = 'HOMO')
                       AND EXISTS (SELECT 1
                                   FROM partner_prop pp
                                            JOIN int_enum e ON pp.enum_id = e.id
                                   WHERE pp.partner_id = p.id
                                     AND e.type = 'properties'
                                     AND e.name = 'FIU')
                       THEN 'FERFIT'
                   -- HOMO + (LANY vagy TRANSZSZEXUALIS) -> lanyt
                   WHEN EXISTS (SELECT 1
                                FROM partner_prop pp
                                         JOIN int_enum e ON pp.enum_id = e.id
                                WHERE pp.partner_id = p.id
                                  AND e.type = 'properties'
                                  AND e.name = 'HOMO')
                       AND EXISTS (SELECT 1
                                   FROM partner_prop pp
                                            JOIN int_enum e ON pp.enum_id = e.id
                                   WHERE pp.partner_id = p.id
                                     AND e.type = 'properties'
                                     AND e.name IN ('LANY', 'TRANSZSZEXUALIS'))
                       THEN 'NOT'
                   -- HETERO + FIU -> lanyt
                   WHEN EXISTS (SELECT 1
                                FROM partner_prop pp
                                         JOIN int_enum e ON pp.enum_id = e.id
                                WHERE pp.partner_id = p.id
                                  AND e.type = 'properties'
                                  AND e.name = 'HETERO')
                       AND EXISTS (SELECT 1
                                   FROM partner_prop pp
                                            JOIN int_enum e ON pp.enum_id = e.id
                                   WHERE pp.partner_id = p.id
                                     AND e.type = 'properties'
                                     AND e.name = 'FIU')
                       THEN 'NOT'
                   -- HETERO + (LANY vagy TRANSZSZEXUALIS) -> fiut
                   WHEN EXISTS (SELECT 1
                                FROM partner_prop pp
                                         JOIN int_enum e ON pp.enum_id = e.id
                                WHERE pp.partner_id = p.id
                                  AND e.type = 'properties'
                                  AND e.name = 'HETERO')
                       AND EXISTS (SELECT 1
                                   FROM partner_prop pp
                                            JOIN int_enum e ON pp.enum_id = e.id
                                   WHERE pp.partner_id = p.id
                                     AND e.type = 'properties'
                                     AND e.name IN ('LANY', 'TRANSZSZEXUALIS'))
                       THEN 'FERFIT'
                   ELSE NULL
                   END
       )                                                        AS client_type,
       p.ts
FROM partner p where p.id > 0 AND p.active_info='true' AND p.del = false;

CREATE VIEW partner_ext_view AS
SELECT p.rowid, ROUND(tpr.r, 1) as rating,
       p.client_type,
       p.active_info,
       p.call_number, p.name, p.pass, p.about,
       p.expect, p.age, p.ages, p.height, p.weight, p.breast, p.waist, p.hips,
       p.location, p.place,
       p.hair, p.eyes, p.breasts, p.body, p.intim,
       p.orientation, p.gender,
       p.act,
       p.aircond, p.smoker, p.sms, p.lengths,
       p.french,
       -- Props
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
            AND e.type = 'properties'
            AND NOT (name LIKE '%_HAJ' OR name LIKE 'HAJ_%' OR name LIKE '%_SZEM'
                OR name LIKE '%_CICI' OR name LIKE '%_ALKAT'
                OR name LIKE 'INTIM_%' OR name IN ('HETERO', 'HOMO', 'BISZEX')
                OR name IN ('LANY', 'PAR', 'FIU', 'TRANSZSZEXUALIS')
                OR name IN ('LEGKONDICIONALT_LAKAS', 'NEM_LEGKONDICIONALT_LAKAS')
                OR name IN ('DOHANYZOM', 'NEM_DOHANYZOM')
                OR name IN ('SMSRE_VALASZOLOK', 'SMSRE_NEM_VALASZOLOK')
                OR name IN ('SZEXPARTNER', 'DOMINA', 'AKTUS_VELEM_KIZART', 'MASSZAZS', 'CSAK_MASSZAZS')
                OR name IN ('CSAK_NALAD', 'CSAK_NALAM', 'NALAM_NALAD', 'ESCORT_KISERET_IS'))
        WHERE pp.partner_id = p.rowid)                                                    AS properties,
       -- Likes (flt out FRANCIA_* and handled keys)
       (SELECT GROUP_CONCAT(CONCAT(IIF(pl.option IS NULL,'',IIF(pl.option='yes', '!', IIF(pl.option='no','-','?'))), e.name), ', ')
        FROM partner_like pl
                 JOIN int_enum e ON pl.enum_id = e.id
            AND e.type = 'likes'
        WHERE pl.partner_id = p.rowid
          AND NOT (e.name LIKE 'FRANCIA_%'
            OR e.name LIKE '%WEBCAM%'))                                                AS likes,
       -- Massages
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_massage pm
                 JOIN int_enum e ON pm.enum_id = e.id
            AND e.type = 'massage'
        WHERE pm.partner_id = p.rowid)                                                    AS massages,
       -- Languages
       (SELECT GROUP_CONCAT(plang.lang, ', ')
        FROM partner_lang plang
        WHERE plang.partner_id = p.rowid)                                                 AS extra_languages,
       -- Looking (flt out *_ORARA and handled keys)
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_looking plook
                 JOIN int_enum e ON plook.enum_id = e.id
            AND e.type = 'looking'
        WHERE plook.partner_id = p.rowid
          AND NOT (e.name LIKE '%_ORARA' OR e.name = 'TOBB_NAPRA')
          AND e.name NOT IN (
            'NOT', 'FERFIT', 'PART', 'TOBBEST',
            'SOS', 'FEL', 'EGY', 'TOBB',
            'NALAM_NALAD', 'CSAK_NALAD', 'CSAK_NALAM', 'ESCORT_KISERET_IS',
            'AUTOS_KALAND', 'BULIBA', 'BUCSUBA', 'CSAK_WEBCAM_SZEX')) AS looking,
       -- Open hours
       (SELECT GROUP_CONCAT(onday || ': ' || hours, ', ')
        FROM partner_open_hour poh
        WHERE poh.partner_id = p.rowid)                   AS open_hours,
    p.ts
FROM partner_ext p
LEFT JOIN (SELECT partner_id, AVG(rating) AS r FROM tmp_partner_rating GROUP BY partner_id) tpr ON tpr.partner_id = p.rowid
WHERE p.rowid > 0;

-- Create user_like related extensions
DROP VIEW IF EXISTS user_like_view;
DROP VIEW IF EXISTS user_view;
DROP TABLE IF EXISTS user_like;

CREATE TABLE user_like
(
    user_id INTEGER NOT NULL REFERENCES user (id),
    like_id TINYINT NOT NULL,
    PRIMARY KEY (user_id, like_id)
);
INSERT INTO user_like (user_id, like_id)
SELECT ul.user_id, ie.id
FROM tmp_user_like ul
         JOIN int_enum ie ON ie.type = 'likes' AND ie.name = ul.like;


CREATE VIEW user_like_view AS
SELECT ul.rowid,user_id,
       GROUP_CONCAT(ie.name, ', ') AS likes
FROM user_like ul
         JOIN int_enum ie ON ul.like_id = ie.id AND ie.type = 'likes'
GROUP BY user_id;

CREATE VIEW user_view AS
SELECT u.id as rowid, u.name,u.age,u.height,u.weight,u.size,u.gender,u.regd,u.ts,u.del,
       ulv.likes,
       (SELECT upfv.location
        FROM user_partner_feedback_view upfv
        WHERE upfv.user_id = u.id
          AND upfv.location IS NOT NULL
        ORDER BY upfv.ts DESC
        LIMIT 1) AS location -- assumed location
FROM user u
         LEFT JOIN user_like_view ulv ON u.id = ulv.user_id
WHERE u.id > 0;
