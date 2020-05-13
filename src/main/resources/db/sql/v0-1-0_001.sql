create table if not exists mainapp.client
(
	client_login text not null
		constraint client_pk
			primary key,
	email text not null,
	registration_date timestamp not null,
	last_access timestamp,
	is_active boolean not null,
	password integer not null
);

alter table mainapp.client owner to postgres;

create unique index if not exists client_client_login_uindex
	on mainapp.client (client_login);

create table if not exists mainapp.language
(
	language_code text not null
		constraint language_pk
			primary key,
	name text not null
);

alter table mainapp.language owner to postgres;

create unique index if not exists language_language_code_uindex
	on mainapp.language (language_code);

create table if not exists mainapp.question
(
	id serial not null
		constraint question_pk
			primary key,
	client_login text not null,
	is_active boolean not null,
	created timestamp not null,
	probability_factor double precision not null,
	probability_multiplier double precision not null,
	last_accessed timestamp,
	tag text
);

alter table mainapp.question owner to postgres;

create table if not exists mainapp.answer
(
    id serial not null
        constraint answer_pk
            primary key,
    client_login text not null
		constraint answer_client_id_fk
			references mainapp.client
				on update restrict on delete restrict,
	question_id integer not null
		constraint answer_question_id_fk
			references mainapp.question
				on update restrict on delete restrict,
	is_right boolean not null,
	answered timestamp not null

);

alter table mainapp.answer owner to postgres;

create unique index if not exists answer_id_uindex
	on mainapp.answer (id);

create unique index if not exists question_id_uindex
	on mainapp.question (id);

create table if not exists mainapp.word
(
	id serial not null
		constraint word_pk
			primary key,
	question_id integer not null
		constraint word_question_id_fk
			references mainapp.question
				on update restrict on delete restrict,
	word text not null,
	added timestamp not null,
	is_active boolean not null,
	language_code text not null
		constraint word_language_code_fk
			references mainapp.language
				on update restrict on delete restrict,
	transcription text
);

alter table mainapp.word owner to postgres;

create unique index if not exists word_id_uindex
	on mainapp.word (id);

