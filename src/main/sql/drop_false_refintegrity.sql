PRAGMA foreign_keys = OFF;

PRAGMA foreign_key_check;

SELECT "table", COUNT(*) AS violation_count
FROM pragma_foreign_key_check
GROUP BY "table";

DELETE FROM partner_prop
WHERE rowid IN (
    SELECT rowid FROM pragma_foreign_key_check WHERE "table" = 'partner_prop'
);

DELETE FROM user_partner_feedback
WHERE rowid IN (
    SELECT rowid FROM pragma_foreign_key_check WHERE "table" = 'user_partner_feedback'
);

DELETE FROM partner_looking
WHERE rowid IN (
    SELECT rowid FROM pragma_foreign_key_check WHERE "table" = 'partner_looking'
);

DELETE FROM user_partner_feedback_gb
WHERE rowid IN (
    SELECT rowid FROM pragma_foreign_key_check WHERE "table" = 'user_partner_feedback_gb'
);
DELETE FROM user_partner_feedback_details
WHERE rowid IN (
    SELECT rowid FROM pragma_foreign_key_check WHERE "table" = 'user_partner_feedback_details'
);


DELETE FROM user_partner_feedback_rating
WHERE rowid IN (
    SELECT rowid FROM pragma_foreign_key_check WHERE "table" = 'user_partner_feedback_rating'
);
DELETE FROM partner_activity
WHERE rowid IN (
    SELECT rowid FROM pragma_foreign_key_check WHERE "table" = 'partner_activity'
);

DELETE FROM partner_answer
WHERE rowid IN (
    SELECT rowid FROM pragma_foreign_key_check WHERE "table" = 'partner_answer'
);

DELETE FROM partner_img
WHERE rowid IN (
    SELECT rowid FROM pragma_foreign_key_check WHERE "table" = 'partner_img'
);

DELETE FROM partner_like
WHERE rowid IN (
    SELECT rowid FROM pragma_foreign_key_check WHERE "table" = 'partner_like'
);

DELETE FROM partner_massage
WHERE rowid IN (
    SELECT rowid FROM pragma_foreign_key_check WHERE "table" = 'partner_massage'
);

DELETE FROM partner_open_hour
WHERE rowid IN (
    SELECT rowid FROM pragma_foreign_key_check WHERE "table" = 'partner_open_hour'
);
PRAGMA foreign_keys = ON;