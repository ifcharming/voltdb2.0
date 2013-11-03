create table table1r_el_only (
 column1_bigint      bigint not null,
 column2_integer     integer,
 column3_smallint    smallint,
 column4_tinyint     tinyint,
 column5_float       float,
 column6_decimal     decimal,
 column8_varchar     varchar(100),
 column9_timestamp   timestamp,
 column10_bigint     bigint,
 primary key (column1_bigint)
);
create view v_table1r_el_only (column2_integer, num_rows)
as select column2_integer as column2_integer,
      count(*) as num_rows
from table1r_el_only
group by column2_integer;
