ALTER TABLE public.phrases_words DROP CONSTRAINT phrases_words_words_word_fk;
ALTER TABLE public.phrases_words RENAME COLUMN word TO word_id;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
ALTER TABLE public.words ADD id TEXT NOT NULL DEFAULT uuid_generate_v1();
ALTER TABLE public.words DROP CONSTRAINT words_pkey;
ALTER TABLE public.words ADD CONSTRAINT words_id_pk PRIMARY KEY (id);

ALTER TABLE public.phrases_words ADD CONSTRAINT phrases_words_words_word_fk
FOREIGN KEY (word_id) REFERENCES public.words (id)