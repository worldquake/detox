--DELETE FROM tmp_partner_rating;

INSERT INTO tmp_partner_rating (partner_id, type, rating)
WITH user_weight AS (
    SELECT CAST(4-AVG(val) AS TINYINT)+1 as variance, f.user_id
    FROM user_partner_feedback_rating r
             JOIN user_partner_feedback f ON r.fbid = f.id
    GROUP BY f.user_id
), rates AS (
    SELECT AVG(val) as rate, f.partner_id, f.user_id
    FROM user_partner_feedback_rating r
             JOIN user_partner_feedback f ON r.fbid = f.id
    WHERE r.enum_id != 0 AND partner_id>0 -- Kornyezet irrelevans
    GROUP BY f.partner_id
)
SELECT
    p.id, 2,
    ROUND((CASE WHEN u.variance > 0 THEN rates.rate
               ELSE CASE WHEN rates.rate < 2.5 THEN rates.rate ELSE 2.5 END END), 1)
FROM rates
         INNER JOIN user_weight u ON u.user_id = rates.user_id
         INNER JOIN partner p ON p.id = rates.partner_id
WHERE p.id>0 AND u.variance>0
ON CONFLICT DO UPDATE SET rating=excluded.rating;

INSERT INTO tmp_partner_rating (partner_id, type, rating)
WITH bad_counts AS (
    SELECT count(*) AS cnt, f.partner_id
    FROM user_partner_feedback f
             INNER JOIN user_partner_feedback_bad b ON b.fbid = f.id AND b.enum_id != 12 -- Lepukkant lakas irrelevans
    GROUP BY f.partner_id
), good_counts AS (
    SELECT count(*) AS cnt, f.partner_id
    FROM user_partner_feedback f
             LEFT JOIN user_partner_feedback_good g ON g.fbid = f.id
    GROUP BY f.partner_id
)
SELECT
    p.id AS partner_id, 1,
    CASE
        WHEN bad_counts.cnt IS NULL THEN good_counts.cnt
        WHEN good_counts.cnt IS NULL THEN bad_counts.cnt
        ELSE COALESCE(good_counts.cnt, 0) - COALESCE(bad_counts.cnt, 0)*2 END AS rating
FROM partner p
         LEFT JOIN bad_counts ON p.id = bad_counts.partner_id
         LEFT JOIN good_counts ON p.id = good_counts.partner_id
WHERE p.id>0 AND rating IS NOT NULL
ON CONFLICT DO UPDATE SET rating=excluded.rating;

UPDATE tmp_partner_rating SET rating=0 WHERE rating<0;
UPDATE tmp_partner_rating SET rating=5 WHERE rating>5;