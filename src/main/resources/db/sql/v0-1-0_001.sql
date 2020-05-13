CREATE TABLE users
(
  user_login        TEXT      NOT NULL,
  email             TEXT      NOT NULL,
  registration_date TIMESTAMP NOT NULL,
  last_access_date  TIMESTAMP,
  is_active         BOOLEAN   NOT NULL
);

CREATE UNIQUE INDEX users_user_login_uindex
  ON users (user_login);

CREATE UNIQUE INDEX users_email_uindex
  ON users (email);

CREATE TABLE phrases
(
  id                     TEXT             NOT NULL
    CONSTRAINT phrases_pkey
    PRIMARY KEY,
  user_login             TEXT             NOT NULL
    CONSTRAINT phrases_users_user_login_fk
    REFERENCES users (user_login),
  is_active              BOOLEAN          NOT NULL,
  creation_date          TIMESTAMP        NOT NULL,
  language_1             TEXT             NOT NULL,
  language_2             TEXT             NOT NULL,
  probability_factor     DOUBLE PRECISION NOT NULL,
  probability_multiplier DOUBLE PRECISION NOT NULL,
  last_access_date       TIMESTAMP,
  label                  TEXT
);

CREATE UNIQUE INDEX phrases_id_uindex
  ON phrases (id);

CREATE TABLE languages
(
  code          TEXT NOT NULL
    CONSTRAINT languages_pkey
    PRIMARY KEY,
  language_name TEXT NOT NULL
);

CREATE UNIQUE INDEX langulages_code_uindex
  ON languages (code);

ALTER TABLE phrases
  ADD CONSTRAINT phrases_languages_code_fk1
FOREIGN KEY (language_1) REFERENCES languages;

ALTER TABLE phrases
  ADD CONSTRAINT phrases_languages_code_fk2
FOREIGN KEY (language_2) REFERENCES languages;

CREATE TABLE words
(
  word          TEXT NOT NULL
    CONSTRAINT words_pkey
    PRIMARY KEY,
  language_code TEXT
    CONSTRAINT words_languages_code_fk
    REFERENCES languages,
  transcription TEXT
);

CREATE UNIQUE INDEX words_word_uindex
  ON words (word);

CREATE TABLE phrases_words
(
  phrase_word_id TEXT      NOT NULL
    CONSTRAINT phrases_words_pkey
    PRIMARY KEY,
  phrase_id      TEXT      NOT NULL
    CONSTRAINT phrases_words_phrases_id_fk
    REFERENCES phrases,
  word           TEXT      NOT NULL
    CONSTRAINT phrases_words_words_word_fk
    REFERENCES words,
  addition_date  TIMESTAMP NOT NULL,
  is_active      BOOLEAN   NOT NULL
);

CREATE UNIQUE INDEX phrases_words_phrase_word_id_uindex
  ON phrases_words (phrase_word_id);

CREATE TABLE answers
(
  user_login      TEXT      NOT NULL
    CONSTRAINT answers_users_user_login_fk
    REFERENCES users (user_login),
  phrase_id       TEXT      NOT NULL
    CONSTRAINT answers_phrases_id_fk
    REFERENCES phrases,
  is_answer_right BOOLEAN   NOT NULL,
  date            TIMESTAMP NOT NULL
);

