drop table if exists main.word;
drop table if exists main.answer;
drop table if exists main.question;
drop table if exists security.principal;
drop schema if exists main;
drop schema if exists security;
create schema main;
create schema security;

create table security.principal
(
    user_id varchar(255) not null
        constraint principal_pkey
            primary key,
    email varchar(255) not null,
    name varchar(255) not null,
    password varchar(255) not null
);

alter table security.principal owner to alexkz;



create table main.question
(
    id                     integer          not null
        constraint question_pkey
            primary key,
    created                timestamp        not null,
    last_accessed          timestamp,
    study_points     double precision not null,
    study_points_multiplier double precision not null,
    tag                    varchar(255)
);

create table main.word
(
    id            integer      not null
        constraint word_pkey
            primary key,
    language      varchar(255) not null,
    transcription varchar(255),
    word          varchar(255) not null,
    question_id   integer
        constraint fkcy96wjgxmw4rqcft408txuhfd
            references main.question
);

create table main.answer
(
    id          integer   not null
        constraint answer_pkey
            primary key,
    answered    timestamp not null,
    correct     boolean   not null,
    question_id integer
        constraint fk8frr4bcabmmeyyu60qt7iiblo
            references main.question
);