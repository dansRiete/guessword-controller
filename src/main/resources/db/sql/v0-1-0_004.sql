CREATE TABLE public.users_languages
(
    id TEXT PRIMARY KEY NOT NULL,
    language_code TEXT NOT NULL,
    user_login TEXT NOT NULL,
    CONSTRAINT users_languages_languages_code_fk FOREIGN KEY (language_code) REFERENCES languages (code),
    CONSTRAINT users_languages_users_user_login_fk FOREIGN KEY (user_login) REFERENCES users (user_login)
);
CREATE UNIQUE INDEX users_languages_id_uindex ON public.users_languages (id);