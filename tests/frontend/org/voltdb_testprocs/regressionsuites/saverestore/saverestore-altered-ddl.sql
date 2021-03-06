-- This table is unchanged from saverestore-ddl.sql
CREATE TABLE PARTITION_TESTER (
  PT_ID INTEGER DEFAULT '0' NOT NULL,
  PT_NAME VARCHAR(16) DEFAULT NULL,
  PT_INTVAL INTEGER DEFAULT NULL,
  PT_FLOATVAL FLOAT DEFAULT NULL,
  PRIMARY KEY (PT_ID)
);

-- This table is unchanged from saverestore-ddl.sql
CREATE TABLE REPLICATED_TESTER (
  RT_ID INTEGER DEFAULT '0' NOT NULL,
  RT_NAME VARCHAR(32) DEFAULT NULL,
  RT_INTVAL INTEGER DEFAULT NULL,
  RT_FLOATVAL FLOAT DEFAULT NULL,
  PRIMARY KEY (RT_ID)
);

-- This table is unchanged from saverestore-ddl.sql
CREATE VIEW MATVIEW (PT_INTVAL, NUM) AS SELECT PT_INTVAL, COUNT(*) FROM PARTITION_TESTER GROUP BY PT_INTVAL;

-- This was not previously materialized
CREATE VIEW BECOMES_MATERIALIZED (PT_INTVAL, NUM) AS SELECT PT_INTVAL, COUNT(*) FROM PARTITION_TESTER GROUP BY PT_INTVAL;

-- This table vanished
--CREATE TABLE GETS_REMOVED (
--  ID INTEGER,
--  INTVAL INTEGER,
--  PRIMARY KEY (ID)
--);

-- This table got created
CREATE TABLE GETS_CREATED (
  ID INTEGER,
  INTVAL INTEGER,
  PRIMARY KEY (ID)
);

-- This table will change columns
CREATE TABLE CHANGE_COLUMNS (
  ID INTEGER NOT NULL,
--  BYEBYE INTEGER --this column is gone now
  HASDEFAULT INTEGER DEFAULT '1234', --this column is added
  HASNULL INTEGER -- this column is added
);

-- This table's columns change types
CREATE TABLE CHANGE_TYPES (
  ID INTEGER,
  BECOMES_INT INTEGER, -- this column becomes an int
  BECOMES_FLOAT FLOAT, -- this column becomes a float
  BECOMES_TINY TINYINT --this column becomes a tinyint
);

-- Table for super big rows that test max supported storage
CREATE TABLE JUMBO_ROW (
 PKEY          INTEGER      NOT NULL,
 STRING1       VARCHAR(1048576),
 STRING2       VARCHAR(1048564),
 PRIMARY KEY (PKEY)
);
