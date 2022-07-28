-- Illust Data
CREATE TABLE IF NOT EXISTS "users"
(
    "uid"     INTEGER     NOT NULL,
    "name"    VARCHAR(15) NOT NULL,
    "account" VARCHAR(32) DEFAULT NULL,
    PRIMARY KEY ("uid"),
    UNIQUE ("account")
);
CREATE TABLE IF NOT EXISTS "artworks"
(
    "pid"             INTEGER  NOT NULL,
    "uid"             INTEGER  NOT NULL,
    "title"           TEXT     NOT NULL,
    "caption"         TEXT     NOT NULL,
    "create_at"       INTEGER  NOT NULL,
    -- page_count max 200
    "page_count"      SMALLINT NOT NULL,
    -- sanity_level 0 2 4 6 7
    "sanity_level"    SMALLINT NOT NULL,
    "type"            SMALLINT NOT NULL,
    "width"           SMALLINT NOT NULL,
    "height"          SMALLINT NOT NULL,
    "total_bookmarks" INTEGER  NOT NULL DEFAULT 0,
    "total_comments"  INTEGER  NOT NULL DEFAULT 0,
    "total_view"      INTEGER  NOT NULL DEFAULT 0,
    "age"             SMALLINT NOT NULL DEFAULT 0,
    "is_ero"          BOOLEAN  NOT NULL DEFAULT FALSE,
    "deleted"         BOOLEAN  NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("pid"),
    FOREIGN KEY ("uid") REFERENCES "users" ("uid") ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS "user_index" ON "artworks" ("uid");
CREATE TABLE IF NOT EXISTS "tag"
(
    "name"            VARCHAR(50) NOT NULL,
    "translated_name" TEXT DEFAULT NULL,
    "tid"             SERIAL      NOT NULL,
    PRIMARY KEY ("name"),
    UNIQUE ("tid")
);
CREATE TABLE IF NOT EXISTS "artwork_tag"
(
    "pid" INTEGER NOT NULL,
    "tid" INTEGER NOT NULL,
    FOREIGN KEY ("pid") REFERENCES "artworks" ("pid") ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY ("tid") REFERENCES "tag" ("tid") ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS "files"
(
    "pid"   INTEGER  NOT NULL,
    "index" SMALLINT NOT NULL,
    "md5"   CHAR(32) NOT NULL,
    "url"   TEXT     NOT NULL,
    -- file size max 32MB
    "size"  INTEGER  NOT NULL,
    PRIMARY KEY ("pid", "index"),
    FOREIGN KEY ("pid") REFERENCES "artworks" ("pid") ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS "md5_index" ON "files" ("md5");
CREATE TABLE IF NOT EXISTS "twitter"
(
    "screen" VARCHAR(15) NOT NULL,
    "uid"    INTEGER     NOT NULL,
    PRIMARY KEY ("screen")
);

-- User Data
CREATE TABLE IF NOT EXISTS "statistic_ero"
(
    "sender"    INTEGER NOT NULL,
    "group"     INTEGER,
    "pid"       INTEGER NOT NULL,
    "timestamp" INTEGER NOT NULL,
    PRIMARY KEY ("sender", "timestamp")
);
CREATE TABLE IF NOT EXISTS "statistic_tag"
(
    "sender"    INTEGER     NOT NULL,
    "group"     INTEGER,
    "pid"       INTEGER,
    "tag"       VARCHAR(30) NOT NULL,
    "timestamp" INTEGER     NOT NULL,
    PRIMARY KEY ("sender", "timestamp")
);
CREATE TABLE IF NOT EXISTS "statistic_search"
(
    "md5"        CHAR(32)      NOT NULL,
    "similarity" NUMERIC(6, 4) NOT NULL,
    "pid"        INTEGER       NOT NULL,
    "title"      VARCHAR(64)   NOT NULL,
    "uid"        INTEGER       NOT NULL,
    "name"       VARCHAR(15)   NOT NULL,
    PRIMARY KEY ("md5")
);
CREATE TABLE IF NOT EXISTS "statistic_alias"
(
    "name" VARCHAR(15) NOT NULL,
    "uid"  INTEGER     NOT NULL,
    PRIMARY KEY ("name")
);
CREATE TABLE IF NOT EXISTS "statistic_task"
(
    "task"      VARCHAR(64) NOT NULL,
    "pid"       INTEGER     NOT NULL,
    "timestamp" INTEGER     NOT NULL,
    PRIMARY KEY ("task", "pid")
);

-- view
CREATE OR REPLACE VIEW "statistic_user" AS
SELECT "uid", COUNT(*) AS "count", COUNT("is_ero" OR null) AS "ero"
FROM "artworks"
WHERE NOT "deleted"
GROUP BY "uid";