-- Illust Data
IF NOT EXISTS(SELECT [name]
              FROM sys.tables
              WHERE [name] = 'users')
CREATE TABLE users
(
    [uid]     INTEGER      NOT NULL,
    [name]    NVARCHAR(15) NOT NULL COLLATE LATIN1_100_CI_AI_UTF8,
    [account] VARCHAR(32)  NOT NULL COLLATE LATIN1_100_BIN,
    PRIMARY KEY ([uid])
);
IF NOT EXISTS(SELECT [name]
              FROM sys.tables
              WHERE [name] = 'artworks')
CREATE TABLE artworks
(
    [pid]             INTEGER      NOT NULL,
    [uid]             INTEGER      NOT NULL,
    [title]           NVARCHAR(32) NOT NULL,
    [caption]         VARCHAR(MAX) NOT NULL,
    [create_at]       INTEGER      NOT NULL,
    -- page_count max 200
    [page_count]      SMALLINT     NOT NULL,
    -- sanity_level 0 2 4 6 7
    [sanity_level]    TINYINT      NOT NULL,
    [type]            TINYINT      NOT NULL,
    [width]           SMALLINT     NOT NULL,
    [height]          SMALLINT     NOT NULL,
    [total_bookmarks] INTEGER      NOT NULL DEFAULT 0,
    [total_comments]  INTEGER      NOT NULL DEFAULT 0,
    [total_view]      INTEGER      NOT NULL DEFAULT 0,
    [age]             TINYINT      NOT NULL DEFAULT 0,
    [is_ero]          BIT          NOT NULL DEFAULT FALSE,
    [deleted]         BIT          NOT NULL DEFAULT FALSE,
    PRIMARY KEY ([pid]),
    FOREIGN KEY ([uid]) REFERENCES users ([uid]) ON UPDATE CASCADE ON DELETE CASCADE,
    INDEX user_id ([uid])
);
IF NOT EXISTS(SELECT [name]
              FROM sys.tables
              WHERE [name] = 'tags')
CREATE TABLE tags
(
    [pid]             INTEGER      NOT NULL,
    [name]            NVARCHAR(30) NOT NULL COLLATE LATIN1_100_BIN,
    [translated_name] VARCHAR(MAX) COLLATE LATIN1_100_BIN,
    PRIMARY KEY ([pid], [name]),
    FOREIGN KEY ([pid]) REFERENCES artworks ([pid]) ON UPDATE CASCADE ON DELETE CASCADE,
    INDEX tag_name ([name]),
    INDEX tag_translated_name ([translated_name])
);
IF NOT EXISTS(SELECT [name]
              FROM sys.tables
              WHERE [name] = 'files')
CREATE TABLE files
(
    [pid]   INTEGER      NOT NULL,
    [index] TINYINT      NOT NULL,
    [md5]   CHAR(32)     NOT NULL COLLATE LATIN1_100_CI_AI,
    [url]   VARCHAR(255) NOT NULL COLLATE LATIN1_100_CI_AI,
    -- file size max 32MB size  INTEGER      NOT NULL,
    PRIMARY KEY ([pid], [index]),
    FOREIGN KEY ([pid]) REFERENCES artworks ([pid]) ON UPDATE CASCADE ON DELETE CASCADE,
    INDEX file_md5 ([md5])
);
IF NOT EXISTS(SELECT [name]
              FROM sys.tables
              WHERE [name] = 'twitter')
CREATE TABLE twitter
(
    [screen] VARCHAR(15) NOT NULL COLLATE LATIN1_100_CI_AI,
    [uid]    INTEGER     NOT NULL,
    PRIMARY KEY ([screen])
);

-- User Data
IF NOT EXISTS(SELECT [name]
              FROM sys.tables
              WHERE [name] = 'statistic_ero')
CREATE TABLE statistic_ero
(
    [sender]    BIGINT  NOT NULL,
    [group]     INTEGER,
    [pid]       INTEGER NOT NULL,
    [timestamp] INTEGER NOT NULL,
    PRIMARY KEY ([sender], [timestamp])
);
IF NOT EXISTS(SELECT [name]
              FROM sys.tables
              WHERE [name] = 'statistic_tag')
CREATE TABLE statistic_tag
(
    [sender]    BIGINT       NOT NULL,
    [group]     INTEGER,
    [pid]       INTEGER,
    [tag]       NVARCHAR(30) NOT NULL COLLATE LATIN1_100_CI_AI_UTF8,
    [timestamp] INTEGER      NOT NULL,
    PRIMARY KEY ([sender], [timestamp])
);
IF NOT EXISTS(SELECT [name]
              FROM sys.tables
              WHERE [name] = 'statistic_search')
CREATE TABLE statistic_search
(
    [md5]        CHAR(32)      NOT NULL COLLATE LATIN1_100_CI_AI,
    [similarity] NUMERIC(6, 4) NOT NULL,
    [pid]        INTEGER       NOT NULL,
    [title]      NVARCHAR(64)  NOT NULL,
    [uid]        INTEGER       NOT NULL,
    [name]       NVARCHAR(15)  NOT NULL,
    PRIMARY KEY (md5)
);
IF NOT EXISTS(SELECT [name]
              FROM sys.tables
              WHERE [name] = 'statistic_alias')
CREATE TABLE statistic_alias
(
    [name] NVARCHAR(15) NOT NULL COLLATE LATIN1_100_CI_AI_UTF8,
    [uid]  INTEGER      NOT NULL,
    PRIMARY KEY ([name])
);
IF NOT EXISTS(SELECT [name]
              FROM sys.tables
              WHERE [name] = 'statistic_task')
CREATE TABLE statistic_task
(
    [task]      VARCHAR(64) NOT NULL COLLATE LATIN1_100_CI_AI_UTF8,
    [pid]       INTEGER     NOT NULL,
    [timestamp] INTEGER     NOT NULL,
    PRIMARY KEY ([task], [pid])
);

-- view
IF NOT EXISTS(SELECT [name]
              FROM sys.views
              WHERE [name] = 'statistic_user')
CREATE VIEW statistic_user AS
SELECT [uid], COUNT(*) AS [count], COUNT([is_ero] OR null) AS [ero]
FROM artworks
WHERE NOT deleted
GROUP BY uid;