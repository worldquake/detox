create table tmp_partner_rating
(
    partner_id   INTEGER references partner on delete cascade,
    type       TINYINT NOT NULL,
    rating     REAL NOT NULL,
    primary key (partner_id, type)
);