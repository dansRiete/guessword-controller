# AI Integration Analysis — Guessword Controller

## 1. System Overview (Context for AI Integration)

Guessword is a **bilingual (EN↔RU) vocabulary flashcard backend** built on Spring Boot + PostgreSQL. Understanding its current mechanics is essential before layering AI on top.

### Data Model

| Entity | Key fields | Role |
|--------|-----------|------|
| `Question` | `studyPoints`, `studyPointsMultiplier`, `tag`, `words` | The flashcard unit |
| `Word` | `word`, `language` (en/ru), `transcription` | One side of a card |
| `Answer` | `correct`, `answered` (timestamp), `question` | Answer history |

### Algorithm (QuestionEngine)

- Every question has a `studyPoints` float (default `30.0`). Lower = more learned.
- **Right answer**: `studyPoints -= RIGHT_ANSWER_DECREMENT * multiplier` then multiplier is squared (accelerating progress per streak).
- **Wrong answer**: `studyPoints += WRONG_ANSWER_INCREMENT`, multiplier resets to `1.0`.
- **Threshold**: studyPoints ≤ 3 → "learned". Learned questions surface with only 5% probability.
- Selection: weighted random over a 0–1,000,000 index range based on `studyPoints^1.75`.
- Non-repeatable window: last 300 answered questions are excluded from the next pick.
- Full in-memory cache; randomizer fully recalculated after every answer.

### Current API Surface

```
GET  /api/answer/getNext          → next question (probabilistic)
POST /api/answer/right            → mark correct
POST /api/answer/wrong            → mark wrong
POST /api/answer/previousRight|Wrong → rollback last answer

GET  /api/question                → all questions
POST /api/question                → create one
POST /api/question/txt            → bulk import (format: "word - translation")
PUT  /api/question/{id}           → update
DELETE /api/question/{id}         → soft delete
```

### Current Limitations Relevant to AI

1. Bulk import only supports rigid `"word - translation"` text format.
2. No semantic search — only SQL `LIKE` lookup exists (in the sql-queries misc file, not even exposed as an API).
3. The `tag` field exists but has no automated population logic.
4. Hints, mnemonics, example sentences — none.
5. Answer validation is binary and fully client-driven (server just stores what client says).
6. The spaced-repetition algorithm is rule-based; it doesn't learn from patterns across users.

---

## 2. AI Integration Opportunities (Prioritised)

### Priority 1 — High Value, Moderate Effort

#### 2.1 Semantic Search via RAG + pgvector

**Problem**: You can only look up words by exact SQL LIKE match. "Find words related to transport" is impossible.

**Solution**:
- Enable the `pgvector` extension on the existing PostgreSQL instance.
- Add a `vector(1536)` (or 768 for smaller models) column to the `word` table (or a side-table `word_embedding`).
- At question creation time, generate an embedding for the EN word (or the full pair: `"word / translation"`) via an embedding model.
- Expose `GET /api/question/search?q=...` that converts the query to an embedding and does a `<=>` cosine similarity lookup.

**Stack options**:
- **LangChain4j** (Java): `EmbeddingModel` + `PgVectorEmbeddingStore` — stays inside the Spring Boot process, no sidecar.
- **Spring AI** (Java): similar capability, newer, better Spring Boot 3 integration.
- **Python sidecar**: a small FastAPI service does the embedding; guessword-controller calls it via `RestTemplate`/`WebClient`.

**DB migration needed**:
```sql
CREATE EXTENSION IF NOT EXISTS vector;
ALTER TABLE main.word ADD COLUMN embedding vector(1536);
CREATE INDEX ON main.word USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

**New service method skeleton**:
```java
// QuestionService.java addition
public List<QuestionDto> semanticSearch(String query, int topK) {
    float[] queryEmbedding = embeddingModel.embed(query);
    List<Integer> questionIds = wordRepository.findSimilar(queryEmbedding, topK);
    return questionIds.stream().map(this::findById).toList();
}
```

---

#### 2.2 AI-Powered Hint Generation

**Problem**: When a user answers wrong they have no contextual help — just the answer revealed.

**Solution**: A new endpoint `GET /api/answer/hint/{questionId}` calls an LLM with a carefully crafted prompt:

```
Given the English word "{word}" (translation: "{translation}"), provide:
1. A short mnemonic or memory trick
2. One example sentence using the word in a natural context
3. The transcription if not already known: {transcription}

Keep the response concise (3-4 lines total).
```

**Integration point**: After a `POST /api/answer/wrong` response is returned, the frontend can optionally call `GET /api/answer/hint/{questionId}`. This keeps it lazy (no LLM call unless requested) and doesn't block the existing answer flow.

**LangChain4j skeleton**:
```java
@Service
public class HintService {
    private final ChatLanguageModel llm;       // injected, e.g. OpenAI or Ollama
    private final QuestionService questionService;

    public String generateHint(Integer questionId) {
        QuestionDto q = questionService.findById(questionId);
        String en = wordOf(q, "en");
        String ru = wordOf(q, "ru");
        return llm.generate("Given the English word \"" + en + "\" (translation: \"" + ru + "\"), " +
            "provide a short mnemonic and one example sentence.");
    }
}
```

**Model choice**: Ollama (local, free, private) is ideal here since the data is personal vocabulary. OpenAI or Anthropic work too.

---

#### 2.3 Translation Validation

**Problem**: `POST /api/question` and `POST /api/question/txt` accept any word pair with zero validation. A typo or wrong translation silently enters the question bank and permanently pollutes the learning session.

**Why LLMs work here (unlike transcription)**: EN↔RU is one of the highest-resource language pairs in training data, and validation is a reasoning/judgment task ("is this semantically equivalent?"), not a precision symbol-output task. Open-source models handle it well.

**Solution**: Async validation after save. Never block the create flow — the user knows their vocabulary; the model acts as a soft quality gate.

Add a `validationStatus` column to `Question`:
```sql
ALTER TABLE main.question ADD COLUMN validation_status VARCHAR(20) DEFAULT 'UNCHECKED';
-- Values: UNCHECKED | OK | FLAGGED
```

Fire a Spring application event from `QuestionService.createQuestion()`:
```java
// QuestionService.createQuestion() — add after save
applicationEventPublisher.publishEvent(new QuestionCreatedEvent(createdQuestion.getId()));
```

```java
@Service
public class TranslationValidationService {

    private final ChatLanguageModel llm;           // Ollama Llama 3 8B is sufficient
    private final QuestionService questionService;

    @Async
    @EventListener
    public void onQuestionCreated(QuestionCreatedEvent event) {
        QuestionDto q = questionService.findById(event.getQuestionId());
        String en = wordOf(q, "en");
        String ru = wordOf(q, "ru");

        String verdict = llm.generate(
            "Is \"" + ru + "\" a correct or acceptable Russian translation of the English word \"" + en + "\"?\n" +
            "Answer with exactly one word: YES, NO, or PARTIAL."
        ).trim().toUpperCase();

        ValidationStatus status = switch (verdict) {
            case "YES" -> ValidationStatus.OK;
            case "NO"  -> ValidationStatus.FLAGGED;
            default    -> ValidationStatus.UNCHECKED;  // PARTIAL or unexpected response
        };
        questionService.updateValidationStatus(q.getId(), status);
    }
}
```

**Review endpoint**: `GET /api/question?validationStatus=FLAGGED` — lets the user inspect and correct or dismiss flagged pairs.

**Three model strategies (pick one):**

| Strategy | How | Best for |
|----------|-----|---------|
| **LLM judgment** | Ask Llama 3 8B / Mistral directly | Common vocabulary, simple setup |
| **Translation model + similarity** | Helsinki-NLP `opus-mt-en-ru` translates EN→RU; embed both translations; cosine similarity > 0.8 → OK | Handles synonyms gracefully (e.g. "pot" → "кастрюля" vs "горшок" both pass) |
| **Wiktionary API first, LLM fallback** | Dictionary lookup for common words; LLM only for misses | Zero inference cost for 80% of words |

**Recommended**: Wiktionary API → LLM fallback. Most everyday vocabulary is covered by Wiktionary for free; the LLM only fires on rarer words.

**Integration with bulk import**: The same `TranslationValidationService` validates pairs coming from `/api/question/txt` and the future `/api/question/ai-import` — one service, three entry points.

---

#### 2.4 Auto-tagging

**Problem**: The `tag` field on `Question` exists but is never auto-populated. Manually tagging every word pair is tedious.

**Solution**: At question creation time, call an LLM to classify the word pair into a semantic category.

```
Classify the vocabulary word pair "{en} / {ru}" into one of these categories:
[travel, food, business, technology, emotions, nature, body, time, household, other]
Reply with only the category name.
```

This can be done synchronously at creation (small, cheap prompt) or asynchronously (background job for existing untagged questions).

**Batch backfill**: A `POST /api/question/autoTag` endpoint or a startup-time job that processes all `tag IS NULL` questions in batches.

---

### Priority 2 — High Value, Higher Effort

#### 2.5 Natural Language Bulk Import

**Problem**: Current `/question/txt` parser requires strict `"word - translation"` format. It's brittle (see `extractQuestion`'s regex).

**Solution**: Accept free-form input and let an LLM structure it.

```
POST /api/question/ai-import
Body: "I want to learn words about cooking: pot, pan, spatula..."
```

LLM prompt:
```
Extract English vocabulary from the following text and provide translations to Russian.
Return ONLY a JSON array in this format:
[{"en": "pot", "ru": "кастрюля", "transcription": "pɒt"}, ...]
Text: "{userInput}"
```

Parse the JSON response and feed into the existing `createQuestion` flow. The transcription field (currently manual) gets auto-populated as a bonus.

**Risk**: LLM hallucinated translations — always log the raw response and expose a `GET /api/question/pending-review` queue so the user can confirm before finalisation.

---

#### 2.6 Conversational Practice Mode

**Problem**: The app only does Q&A flashcards. There is no practice using words in real sentences.

**Solution**: A new endpoint `POST /api/chat` that maintains a conversation session and instructs the LLM to:
1. Hold a short dialogue using only words from the user's active (non-learned, studyPoints > 3) vocabulary.
2. Gently correct grammar mistakes.
3. Track which words were successfully used and decrement their `studyPoints` accordingly.

**Session state**: A `ChatSession` in-memory map (or Redis for persistence) keyed by session ID.

```java
// simplified
String systemPrompt = "You are a helpful English teacher. " +
    "In this conversation, try to naturally use these English words: " +
    String.join(", ", activeVocabulary) + ". " +
    "Correct any grammar mistakes gently. Keep responses short (2-3 sentences).";
```

This is the most complex integration but also the most engaging UX upgrade.

---

#### 2.7 LLM-Enhanced Spaced Repetition

**Problem**: The current algorithm (studyPoints ± fixed deltas) is hand-tuned. It doesn't adapt to patterns like "user always struggles with phrasal verbs" or "user performs worse in the evening."

**Solution** (longer-term): Feed answer history into an LLM or lightweight ML model to predict the optimal next review interval per word. This is essentially SuperMemo SM-18 territory — an LLM won't beat it for pure scheduling, but an LLM *can* analyze answer streaks and surface insight:

```
GET /api/insights → "You struggle most with phrasal verbs (get up, take off). 
                     Your accuracy drops after 30 minutes of study."
```

---

### Priority 3 — Nice-to-have

#### 2.7 Auto-Transcription for New Words

The `transcription` field is currently populated manually. An LLM (GPT-4o, Claude) can generate IPA phonetic transcription:

```
Provide the IPA phonetic transcription for the English word "ubiquitous". 
Reply only with the transcription, e.g. /juːˈbɪk.wɪ.təs/
```

Free-tier Wiktionary API is another option — no LLM cost and deterministic.

#### 2.8 Duplicate Detection

Before saving a new question, embed it and do a similarity search. If cosine similarity > 0.95 with an existing word, warn the user. Prevents the question bank from accumulating near-duplicate cards.

---

## 3. Recommended Stack

### Java-native (least disruption to existing codebase)

```kotlin
// build.gradle.kts additions
implementation("dev.langchain4j:langchain4j-spring-boot-starter:0.35.0")
implementation("dev.langchain4j:langchain4j-pgvector:0.35.0")
// Pick one LLM provider:
implementation("dev.langchain4j:langchain4j-open-ai:0.35.0")
// OR for local/free:
implementation("dev.langchain4j:langchain4j-ollama:0.35.0")
```

LangChain4j integrates natively with Spring Boot 3 (auto-configuration, `@Bean` injection of `ChatLanguageModel`, `EmbeddingModel`, `EmbeddingStore`).

**Spring AI** is the alternative — same idea, backed by the Spring team, potentially better long-term.

### Python sidecar (if richer LangChain ecosystem is needed)

```
guessword-controller (Java) ──HTTP──▶ ai-service (Python FastAPI)
                                          ├── LangChain / LangGraph
                                          ├── sentence-transformers
                                          └── pgvector via psycopg2
```

This allows using the full Python LangChain ecosystem (agents, LangGraph for stateful flows, etc.) while keeping the Java service unchanged except for one new `AiServiceClient`.

---

## 4. Database Changes Required

```sql
-- Enable pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- Add embedding storage (option A: on word table)
ALTER TABLE main.word ADD COLUMN embedding vector(1536);
CREATE INDEX word_embedding_idx ON main.word USING ivfflat (embedding vector_cosine_ops);

-- Example sentences table (for RAG hint retrieval)
CREATE TABLE main.word_example (
    id SERIAL PRIMARY KEY,
    word_id INTEGER REFERENCES main.word(id),
    example TEXT NOT NULL,
    source VARCHAR(50)  -- 'llm-generated', 'user', etc.
);

-- Chat session table (for conversational mode)
CREATE TABLE main.chat_session (
    id VARCHAR(36) PRIMARY KEY,
    messages JSONB NOT NULL,
    created TIMESTAMPTZ DEFAULT NOW(),
    last_active TIMESTAMPTZ DEFAULT NOW()
);
```

---

## 5. Implementation Roadmap

| Phase | Feature | Estimated effort | Value |
|-------|---------|-----------------|-------|
| 1 | pgvector setup + word embeddings at creation | 1–2 days | Enables 2.1, 2.8 |
| 1 | Semantic search endpoint | 1 day | High |
| 1 | Translation validation (async, Wiktionary + LLM fallback) | 1 day | High |
| 1 | Hint generation endpoint | 1 day | High |
| 2 | Auto-tagging on create | 0.5 day | Medium |
| 2 | Auto-transcription | 0.5 day | Medium |
| 3 | Natural language bulk import | 2 days | High |
| 3 | Duplicate detection | 1 day | Medium |
| 4 | Conversational practice mode | 3–5 days | Very High |
| 4 | LLM-powered insights | 2–3 days | Medium |

---

## 6. Key Architectural Considerations

### Latency
- Embedding generation at question creation: ~100–300ms → acceptable (write path, not on answer loop).
- Hint generation: 1–3s → expose as a separate optional call, never block `getNext`.
- Never put LLM calls in the hot path (`getNext` / `right` / `wrong`).

### Privacy
- Word pairs are personal vocabulary data. Use **Ollama + a local model** (e.g. `nomic-embed-text` for embeddings, `llama3` for chat) to keep data on-device if privacy is a concern.
- If using OpenAI/Anthropic, word pairs themselves are low-sensitivity, but be aware of API data retention policies.

### Cost
- Embeddings: OpenAI `text-embedding-3-small` is ~$0.02/1M tokens. Embedding 10,000 word pairs costs < $0.01.
- Chat (hints, import): GPT-4o-mini or Claude Haiku is cheap enough for personal use.
- Ollama: free, runs locally, sufficient quality for this use case.

### Existing Cache Compatibility
- The `questionsCache` in `QuestionService` loads at startup. Embedding lookup bypasses this cache (it's a DB vector search). That's fine — embeddings serve a different query pattern (semantic) than the randomiser (probability-weighted).
- If auto-tagging is added, the cache must be invalidated/updated for the tagged question: `questionsCache.put(id, updated)` — already the pattern used in `updateQuestion`.

---

## 7. Minimal First Step (Proof of Concept)

The smallest useful AI addition with the least risk:

1. Add `langchain4j-spring-boot-starter` + `langchain4j-ollama` to `build.gradle.kts`.
2. Configure Ollama locally (or OpenAI key in `application-local.yml`).
3. Create `HintService` with a single `generateHint(questionId)` method.
4. Add `GET /api/answer/hint/{questionId}` to `AnswerController`.

This touches no existing logic, adds one new dependency, and delivers an immediately useful feature. It also validates the LangChain4j + Spring Boot 3 wiring before committing to the heavier pgvector changes.
