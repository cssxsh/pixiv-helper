-- Illust Data
CREATE TABLE IF NOT EXISTS users
(
    `uid`     INTEGER      NOT NULL,
    `name`    NVARCHAR(15) NOT NULL,
    `account` VARCHAR(32) DEFAULT NULL,
    PRIMARY KEY (`uid`),
    UNIQUE KEY (`account`)
);
CREATE TABLE IF NOT EXISTS artworks
(
    `pid`             INTEGER      NOT NULL,
    `uid`             INTEGER      NOT NULL,
    `title`           NVARCHAR(32) NOT NULL,
    `caption`         TEXT         NOT NULL,
    `create_at`       INTEGER      NOT NULL,
    -- page_count max 200
    `page_count`      SMALLINT     NOT NULL,
    -- sanity_level 0 2 4 6 7
    `sanity_level`    TINYINT      NOT NULL,
    `type`            TINYINT      NOT NULL,
    `width`           SMALLINT     NOT NULL,
    `height`          SMALLINT     NOT NULL,
    `total_bookmarks` INTEGER      NOT NULL DEFAULT 0,
    `total_comments`  INTEGER      NOT NULL DEFAULT 0,
    `total_view`      INTEGER      NOT NULL DEFAULT 0,
    `age`             TINYINT      NOT NULL DEFAULT 0,
    `is_ero`          BOOLEAN      NOT NULL DEFAULT FALSE,
    `deleted`         BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (`pid`),
    FOREIGN KEY (`uid`) REFERENCES users (`uid`) ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE TABLE `tag`
(
    `name`            VARCHAR(30)      NOT NULL,
    `translated_name` VARCHAR(64)      NULL DEFAULT NULL,
    `tid`             INTEGER UNSIGNED NOT NULL AUTO_INCREMENT,
    PRIMARY KEY (`name`),
    UNIQUE INDEX `tid` (`tid`)
);
CREATE TABLE `artwork_tag`
(
    `pid` INTEGER UNSIGNED NOT NULL,
    `tid` INTEGER UNSIGNED NOT NULL,
    FOREIGN KEY (`pid`) REFERENCES artworks (`pid`) ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (`tid`) REFERENCES tag (`tid`) ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS files
(
    `pid`   INTEGER      NOT NULL,
    `index` TINYINT      NOT NULL,
    `md5`   CHAR(32)     NOT NULL,
    `url`   VARCHAR(255) NOT NULL,
    -- file size max 32MB
    `size`  INTEGER      NOT NULL,
    PRIMARY KEY (`pid`, `index`),
    FOREIGN KEY (`pid`) REFERENCES artworks (`pid`) ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS twitter
(
    `screen` VARCHAR(15) NOT NULL,
    `uid`    INTEGER     NOT NULL,
    PRIMARY KEY (`screen`)
);

-- User Data
CREATE TABLE IF NOT EXISTS statistic_ero
(
    `sender`    BIGINT  NOT NULL,
    `group`     INTEGER,
    `pid`       INTEGER NOT NULL,
    `timestamp` INTEGER NOT NULL,
    PRIMARY KEY (`sender`, `timestamp`)
);
CREATE TABLE IF NOT EXISTS statistic_tag
(
    `sender`    BIGINT      NOT NULL,
    `group`     INTEGER,
    `pid`       INTEGER,
    `tag`       VARCHAR(30) NOT NULL,
    `timestamp` INTEGER     NOT NULL,
    PRIMARY KEY (`sender`, `timestamp`)
);
CREATE TABLE IF NOT EXISTS statistic_search
(
    `md5`        NCHAR(32)     NOT NULL,
    `similarity` NUMERIC(6, 4) NOT NULL,
    `pid`        INTEGER       NOT NULL,
    `title`      NVARCHAR(64)  NOT NULL,
    `uid`        INTEGER       NOT NULL,
    `name`       NVARCHAR(15)  NOT NULL,
    PRIMARY KEY (`md5`)
);
CREATE TABLE IF NOT EXISTS statistic_alias
(
    `name` NVARCHAR(15) NOT NULL,
    `uid`  INTEGER      NOT NULL,
    PRIMARY KEY (`name`)
);
CREATE TABLE IF NOT EXISTS statistic_task
(
    `task`      VARCHAR(64) NOT NULL,
    `pid`       INTEGER     NOT NULL,
    `timestamp` INTEGER     NOT NULL,
    PRIMARY KEY (`task`, `pid`)
);

-- view
CREATE OR REPLACE VIEW statistic_user AS
SELECT `uid`, COUNT(*) AS `count`, COUNT(is_ero OR null) AS `ero`
FROM artworks
WHERE NOT deleted
GROUP BY uid;