-- Illust Data
CREATE TABLE IF NOT EXISTS `users`
(
    `uid`     INTEGER UNSIGNED NOT NULL,
    `name`    VARCHAR(15)      NOT NULL COLLATE 'utf8mb4_unicode_ci',
    `account` VARCHAR(32) DEFAULT NULL COLLATE 'ascii_general_ci',
    PRIMARY KEY (`uid`),
    UNIQUE (`account`)
) DEFAULT CHARACTER SET 'utf8mb4';
CREATE TABLE IF NOT EXISTS `artworks`
(
    `pid`             INTEGER UNSIGNED  NOT NULL,
    `uid`             INTEGER UNSIGNED  NOT NULL,
    `title`           VARCHAR(32)       NOT NULL,
    `caption`         TEXT              NOT NULL,
    `create_at`       INTEGER UNSIGNED  NOT NULL,
    -- page_count max 200
    `page_count`      TINYINT UNSIGNED  NOT NULL,
    -- sanity_level 0 2 4 6 7
    `sanity_level`    TINYINT UNSIGNED  NOT NULL,
    `type`            TINYINT UNSIGNED  NOT NULL,
    `width`           SMALLINT UNSIGNED NOT NULL,
    `height`          SMALLINT UNSIGNED NOT NULL,
    `total_bookmarks` INTEGER UNSIGNED  NOT NULL DEFAULT 0,
    `total_comments`  INTEGER UNSIGNED  NOT NULL DEFAULT 0,
    `total_view`      INTEGER UNSIGNED  NOT NULL DEFAULT 0,
    `age`             TINYINT UNSIGNED  NOT NULL DEFAULT 0,
    `is_ero`          BOOLEAN           NOT NULL DEFAULT FALSE,
    `deleted`         BOOLEAN           NOT NULL DEFAULT FALSE,
    PRIMARY KEY (`pid`),
    FOREIGN KEY (`uid`) REFERENCES `users` (`uid`) ON UPDATE CASCADE ON DELETE CASCADE,
    INDEX (`uid`)
) DEFAULT CHARACTER SET 'utf8mb4';
CREATE TABLE IF NOT EXISTS `tags`
(
    `pid`             INTEGER UNSIGNED NOT NULL,
    `name`            VARCHAR(30)      NOT NULL COLLATE 'utf8mb4_bin',
    `translated_name` VARCHAR(64) DEFAULT NULL COLLATE 'utf8mb4_unicode_ci',
    PRIMARY KEY (`pid`, `name`),
    FOREIGN KEY (`pid`) REFERENCES `artworks` (`pid`) ON UPDATE CASCADE ON DELETE CASCADE,
    INDEX (`name`),
    INDEX (`translated_name`)
) DEFAULT CHARACTER SET 'utf8mb4';
CREATE TABLE IF NOT EXISTS `tag`
(
    `name`            VARCHAR(50)      NOT NULL COLLATE 'utf8mb4_bin',
    `translated_name` TINYTEXT DEFAULT NULL COLLATE 'utf8mb4_unicode_ci',
    `tid`             INTEGER UNSIGNED NOT NULL AUTO_INCREMENT,
    PRIMARY KEY (`name`),
    UNIQUE (`tid`)
) DEFAULT CHARACTER SET 'utf8mb4';
CREATE TABLE IF NOT EXISTS `artwork_tag`
(
    `pid` INTEGER UNSIGNED NOT NULL,
    `tid` INTEGER UNSIGNED NOT NULL,
    FOREIGN KEY (`pid`) REFERENCES `artworks` (`pid`) ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (`tid`) REFERENCES `tag` (`tid`) ON UPDATE CASCADE ON DELETE CASCADE
) DEFAULT CHARACTER SET 'utf8mb4';
CREATE TABLE IF NOT EXISTS `files`
(
    `pid`   INTEGER UNSIGNED NOT NULL,
    `index` TINYINT UNSIGNED NOT NULL,
    `md5`   CHAR(32)         NOT NULL COLLATE 'ascii_general_ci',
    `url`   TINYTEXT         NOT NULL COLLATE 'ascii_general_ci',
    -- file size max 32MB
    `size`  INTEGER UNSIGNED NOT NULL,
    PRIMARY KEY (`pid`, `index`),
    FOREIGN KEY (`pid`) REFERENCES `artworks` (`pid`) ON UPDATE CASCADE ON DELETE CASCADE,
    INDEX (`md5`)
);
CREATE TABLE IF NOT EXISTS `twitter`
(
    `screen` VARCHAR(15)      NOT NULL COLLATE 'ascii_general_ci',
    `uid`    INTEGER UNSIGNED NOT NULL,
    PRIMARY KEY (`screen`)
);

-- User Data
CREATE TABLE IF NOT EXISTS `statistic_ero`
(
    `sender`    INTEGER UNSIGNED NOT NULL,
    `group`     INTEGER UNSIGNED,
    `pid`       INTEGER UNSIGNED NOT NULL,
    `timestamp` INTEGER UNSIGNED NOT NULL,
    PRIMARY KEY (`sender`, `timestamp`)
);
CREATE TABLE IF NOT EXISTS `statistic_tag`
(
    `sender`    INTEGER UNSIGNED NOT NULL,
    `group`     INTEGER UNSIGNED,
    `pid`       INTEGER UNSIGNED,
    `tag`       VARCHAR(30)      NOT NULL,
    `timestamp` INTEGER UNSIGNED NOT NULL,
    PRIMARY KEY (`sender`, `timestamp`)
);
CREATE TABLE IF NOT EXISTS `statistic_search`
(
    `md5`        CHAR(32)         NOT NULL COLLATE 'ascii_general_ci',
    `similarity` NUMERIC(6, 4)    NOT NULL,
    `pid`        INTEGER UNSIGNED NOT NULL,
    `title`      VARCHAR(64)      NOT NULL,
    `uid`        INTEGER UNSIGNED NOT NULL,
    `name`       VARCHAR(15)      NOT NULL,
    PRIMARY KEY (`md5`)
) DEFAULT CHARACTER SET 'utf8mb4';
CREATE TABLE IF NOT EXISTS `statistic_alias`
(
    `name` VARCHAR(15)      NOT NULL,
    `uid`  INTEGER UNSIGNED NOT NULL,
    PRIMARY KEY (`name`)
);
CREATE TABLE IF NOT EXISTS `statistic_task`
(
    `task`      VARCHAR(64)      NOT NULL COLLATE 'utf8mb4_unicode_ci',
    `pid`       INTEGER UNSIGNED NOT NULL,
    `timestamp` INTEGER UNSIGNED NOT NULL,
    PRIMARY KEY (`task`, `pid`)
);

-- view
CREATE OR REPLACE VIEW `statistic_user` AS
SELECT `uid`, COUNT(*) AS `count`, COUNT(is_ero OR null) AS `ero`
FROM `artworks`
WHERE NOT `deleted`
GROUP BY `uid`;