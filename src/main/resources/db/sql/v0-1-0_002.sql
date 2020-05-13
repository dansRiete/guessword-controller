ALTER TABLE public.phrase_word
  ALTER COLUMN phrase_word_id TYPE TEXT USING phrase_word_id :: TEXT;
ALTER TABLE public.phrase_word
  ALTER COLUMN id DROP DEFAULT;