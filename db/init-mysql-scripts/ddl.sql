create table if not exists guest
(
	id int auto_increment
		primary key,
	for_word varchar(250) not null,
	nat_word varchar(250) not null,
	transcr varchar(100) null,
	prob_factor double null,
	label varchar(50) null,
	create_date datetime null,
	last_accs_date datetime null,
	exactmatch tinyint(1) null,
	index_start double null,
	index_end double null,
	rate double null
);

create table if not exists guest_stat
(
	date datetime not null,
	ms int not null,
	event varchar(30) not null,
	id int not null,
	learnt int null
);

create table if not exists statistics
(
	date datetime not null,
	ms int not null,
	event varchar(30) not null,
	id bigint not null,
	learnt int null
);

create table if not exists users
(
	login varchar(64) charset utf8mb4 not null,
	email varchar(50) charset utf8 not null,
	name varchar(50) charset utf8 null,
	password varchar(50) charset utf8 null,
	constraint users_login_uindex
		unique (login)
);

alter table users
	add primary key (login);

create table if not exists words
(
	id bigint not null,
	for_word varchar(250) not null,
	nat_word varchar(250) not null,
	transcr varchar(100) null,
	prob_factor double null,
	label varchar(50) null,
	create_date datetime null,
	last_accs_date datetime null,
	index_start double null,
	index_end double null,
	rate double null,
	is_deleted tinyint default 0 not null,
	user_id varchar(64) charset utf8mb4 not null,
	constraint unique_id
		unique (id),
	constraint user_fk
		foreign key (user_id) references users (login)
);

alter table words
	add primary key (id);

create table if not exists questions
(
	id bigint auto_increment
		primary key,
	answer varchar(250) null,
	date datetime not null,
	phrase_key bigint not null,
	answered_correctly tinyint null,
	user_id varchar(64) charset utf8mb4 not null,
	init_probability_factor double null,
	answered_probability_factor double null,
	init_multiplier double null,
	answered_multiplier double null,
	constraint phrase_fk
		foreign key (phrase_key) references words (id),
	constraint user_question_fk
		foreign key (user_id) references users (login)
);

create index user_fk_idx
	on questions (user_id);

