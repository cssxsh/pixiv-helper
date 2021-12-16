-- Illust Data
CREATE TABLE IF NOT EXISTS users
(
    `uid`     INTEGER NOT NULL,
    `name`    TEXT    NOT NULL COLLATE RTRIM,
    `account` TEXT DEFAULT NULL COLLATE RTRIM,
    PRIMARY KEY (`uid`),
    UNIQUE (`account`)
);
CREATE TABLE IF NOT EXISTS artworks
(
    `pid`             INTEGER  NOT NULL,
    `uid`             INTEGER  NOT NULL,
    `title`           TEXT     NOT NULL,
    `caption`         TEXT     NOT NULL,
    `create_at`       INTEGER  NOT NULL,
    -- page_count max 200
    `page_count`      SMALLINT NOT NULL,
    -- sanity_level 0 2 4 6 7
    `sanity_level`    TINYINT  NOT NULL,
    `type`            TINYINT  NOT NULL,
    `width`           SMALLINT NOT NULL,
    `height`          SMALLINT NOT NULL,
    `total_bookmarks` INTEGER  NOT NULL DEFAULT 0,
    `total_comments`  INTEGER  NOT NULL DEFAULT 0,
    `total_view`      INTEGER  NOT NULL DEFAULT 0,
    `age`             TINYINT  NOT NULL DEFAULT 0,
    `is_ero`          BOOLEAN  NOT NULL DEFAULT FALSE,
    `deleted`         BOOLEAN  NOT NULL DEFAULT FALSE,
    PRIMARY KEY (`pid`),
    FOREIGN KEY (`uid`) REFERENCES users (`uid`) ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS user_id ON users (`uid`);
CREATE TABLE IF NOT EXISTS tags
(
    `pid`             INTEGER NOT NULL,
    `name`            TEXT    NOT NULL COLLATE RTRIM,
    `translated_name` TEXT COLLATE RTRIM,
    PRIMARY KEY (`pid`, `name`),
    FOREIGN KEY (`pid`) REFERENCES artworks (`pid`) ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS tag_name ON tags (`name`);
CREATE INDEX IF NOT EXISTS tag_translated_name ON tags (`translated_name`);
CREATE TABLE IF NOT EXISTS files
(
    `pid`   INTEGER NOT NULL,
    `index` TINYINT NOT NULL,
    `md5`   TEXT    NOT NULL COLLATE NOCASE,
    `url`   TEXT    NOT NULL COLLATE NOCASE,
    -- file size max 32MB
    `size`  INTEGER NOT NULL,
    PRIMARY KEY (`pid`, `index`),
    FOREIGN KEY (`pid`) REFERENCES artworks (`pid`) ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS file_md5 ON files (`md5`);
CREATE TABLE IF NOT EXISTS twitter
(
    `screen` TEXT    NOT NULL COLLATE NOCASE,
    `uid`    INTEGER NOT NULL,
    PRIMARY KEY (`screen`)
);

-- User Data
CREATE TABLE IF NOT EXISTS statistic_ero
(
    `sender`    INTEGER NOT NULL,
    `group`     INTEGER,
    `pid`       INTEGER NOT NULL,
    `timestamp` INTEGER NOT NULL,
    PRIMARY KEY (`sender`, `timestamp`)
);
CREATE TABLE IF NOT EXISTS statistic_tag
(
    `sender`    INTEGER NOT NULL,
    `group`     INTEGER,
    `pid`       INTEGER,
    `tag`       TEXT    NOT NULL COLLATE RTRIM,
    `timestamp` INTEGER NOT NULL,
    PRIMARY KEY (`sender`, `timestamp`)
);
CREATE TABLE IF NOT EXISTS statistic_search
(
    `md5`        TEXT    NOT NULL COLLATE NOCASE,
    `similarity` REAL    NOT NULL,
    `pid`        INTEGER NOT NULL,
    `title`      TEXT    NOT NULL,
    `uid`        INTEGER NOT NULL,
    `name`       TEXT    NOT NULL,
    PRIMARY KEY (`md5`)
);
CREATE TABLE IF NOT EXISTS statistic_alias
(
    `name` TEXT    NOT NULL COLLATE RTRIM,
    `uid`  INTEGER NOT NULL,
    PRIMARY KEY (`name`)
);
CREATE TABLE IF NOT EXISTS statistic_task
(
    `task`      TEXT    NOT NULL COLLATE NOCASE,
    `pid`       INTEGER NOT NULL,
    `timestamp` INTEGER NOT NULL,
    PRIMARY KEY (`task`, `pid`)
);

-- view
CREATE VIEW IF NOT EXISTS statistic_user AS
SELECT `uid`, COUNT(*) AS `count`, COUNT(is_ero OR null) AS `ero`
FROM artworks
WHERE NOT deleted
GROUP BY uid;