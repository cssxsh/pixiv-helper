-- Illust Data
CREATE TABLE IF NOT EXISTS users
(
    `uid`     INTEGER     NOT NULL,
    `name`    VARCHAR(15) NOT NULL COLLATE 'utf8mb4_unicode_ci',
    `account` VARCHAR(32) NOT NULL COLLATE 'ascii_general_ci',
    PRIMARY KEY (`uid`)
);
CREATE TABLE IF NOT EXISTS artworks
(
    `pid`             INTEGER     NOT NULL,
    `uid`             INTEGER     NOT NULL,
    `title`           VARCHAR(32) NOT NULL,
    `caption`         TEXT        NOT NULL,
    `create_at`       INTEGER     NOT NULL,
    -- page_count max 200
    `page_count`      SMALLINT    NOT NULL,
    -- sanity_level 0 2 4 6 7
    `sanity_level`    TINYINT     NOT NULL,
    `type`            TINYINT     NOT NULL,
    `width`           SMALLINT    NOT NULL,
    `height`          SMALLINT    NOT NULL,
    `total_bookmarks` INTEGER     NOT NULL DEFAULT 0,
    `total_comments`  INTEGER     NOT NULL DEFAULT 0,
    `total_view`      INTEGER     NOT NULL DEFAULT 0,
    `age`             TINYINT     NOT NULL DEFAULT 0,
    `is_ero`          BOOLEAN     NOT NULL DEFAULT FALSE,
    `deleted`         BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (`pid`),
    FOREIGN KEY (`uid`) REFERENCES users (`uid`) ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS tags
(
    `pid`             INTEGER     NOT NULL,
    `name`            VARCHAR(30) NOT NULL COLLATE 'utf8mb4_bin',
    `translated_name` VARCHAR(64) COLLATE 'utf8mb4_bin',
    PRIMARY KEY (`pid`, `name`),
    FOREIGN KEY (`pid`) REFERENCES artworks (`pid`) ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS files
(
    `pid`   INTEGER      NOT NULL,
    `index` TINYINT      NOT NULL,
    `md5`   CHAR(32)     NOT NULL COLLATE 'ascii_general_ci',
    `url`   VARCHAR(200) NOT NULL COLLATE 'ascii_general_ci',
    -- file size max 32MB
    `size`  INTEGER      NOT NULL,
    PRIMARY KEY (`pid`, `index`),
    FOREIGN KEY (`pid`) REFERENCES artworks (`pid`) ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS file_md5 ON files (`md5`);

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
    `sender`    INTEGER     NOT NULL,
    `group`     INTEGER,
    `pid`       INTEGER,
    `tag`       VARCHAR(30) NOT NULL,
    `timestamp` INTEGER     NOT NULL,
    PRIMARY KEY (`sender`, `timestamp`)
);
CREATE TABLE IF NOT EXISTS statistic_search
(
    `md5`        CHAR(32)     NOT NULL COLLATE 'ascii_general_ci',
    `similarity` NUMERIC(6, 4) NOT NULL,
    `pid`        INTEGER       NOT NULL,
    `title`      VARCHAR(32)   NOT NULL,
    `uid`        INTEGER       NOT NULL,
    `name`       VARCHAR(15)   NOT NULL,
    PRIMARY KEY (`md5`)
);
CREATE TABLE IF NOT EXISTS statistic_alias
(
    `name` VARCHAR(15) NOT NULL,
    `uid`  INTEGER     NOT NULL,
    PRIMARY KEY (`name`)
);
CREATE TABLE IF NOT EXISTS statistic_task
(
    `task`      VARCHAR(32) NOT NULL COLLATE 'ascii_general_ci',
    `pid`       INTEGER     NOT NULL,
    `timestamp` INTEGER     NOT NULL,
    PRIMARY KEY (`task`, `pid`)
);