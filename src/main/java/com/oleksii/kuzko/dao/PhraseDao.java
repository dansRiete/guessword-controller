package com.oleksii.kuzko.dao;

import com.oleksii.kuzko.model.Phrase;
import com.oleksii.kuzko.model.User;
import com.oleksii.kuzko.model.Word;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * @author The Weather Company, An IBM Business
 */
@Repository
public class PhraseDao {

    private final static PostgreSqlPhraseMapper POSTGRESQL_PHRASE_MAPPER = new PostgreSqlPhraseMapper();
    private final static Logger LOGGER = Logger.getLogger(PhraseDao.class);

    private final static String SELECT_ALL_POSTGRES =
            "SELECT\n"
            + "  phrases.id                AS phrase_id,\n"
            + "  phrases.user_login,\n"
            + "  phrases.creation_date,\n"
            + "  phrases.last_access_date,\n"
            + "  phrases.language_1,\n"
            + "  phrases.language_2,\n"
            + "  phrases.probability_factor,\n"
            + "  phrases.probability_multiplier,\n"
            + "  phrases.last_access_date,\n"
            + "  phrases.label,\n"
            + "  phrase_word.id            AS phrase_word_id,\n"
            + "  phrase_word.word_id,\n"
            + "  phrase_word.addition_date AS word_addition_date,\n"
            + "  words.word,\n"
            + "  words.language_code       AS word_language_code,\n"
            + "  words.transcription       AS transcription\n"
            + "FROM phrase_word\n"
            + "  INNER JOIN phrases ON phrase_id = phrases.id\n"
            + "  INNER JOIN words ON phrase_word.word_id = words.id\n"
            + "WHERE phrase_word.is_active = TRUE\n"
            + "      AND phrases.is_active = TRUE\n"
            + "ORDER BY phrase_id, phrase_word_id";

    private final static String SELECT_ALL_MYSQL =
            "SELECT * FROM guessword.words INNER JOIN guessword.users " +
                    "ON user_id = guessword.users.id";

    private final static String INSERT_PHRASE_SQL = "INSERT INTO phrases VALUES\n"
            + "  (:id, :user_login, :is_active, :creation_date, :language_1, :language_2,\n"
            + "   :probability_factor, :probability_multiplier, :last_access_date, :label)";

    //todo do it if not exists
    private final static String INSERT_WORD_SQL = "INSERT INTO words VALUES\n"
            + "  (:word, :language_code, :transcription, :id)";

    private final static String INSERT_PHRASE_WORD_SQL = "INSERT INTO phrase_word VALUES\n"
            + "  (:id, :phrase_id, :word_id, :addition_date, :is_active)";

//    private final static String INSERT_PHRASE =

    private final NamedParameterJdbcTemplate postgresNamedParameterJdbcTemplate;
    private final NamedParameterJdbcTemplate mysqlNamedParameterJdbcTemplate;

    public PhraseDao(
            NamedParameterJdbcTemplate postgresNamedParameterJdbcTemplate,
            @Qualifier("mysqlDatasource") DataSource mysqlDatasource
    ) {
        this.postgresNamedParameterJdbcTemplate = postgresNamedParameterJdbcTemplate;
        this.mysqlNamedParameterJdbcTemplate = new NamedParameterJdbcTemplate(mysqlDatasource);
    }

    @Transactional
    public Phrase createPhrase(Phrase phraseToCreate){

        MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource();

        mapSqlParameterSource.addValue("id", phraseToCreate.getId());
        mapSqlParameterSource.addValue("user_login", "alex");
        mapSqlParameterSource.addValue("is_active", true);
        mapSqlParameterSource.addValue("creation_date", Timestamp.from(phraseToCreate.getCreationDate().toInstant(ZoneOffset.UTC)));
        mapSqlParameterSource.addValue("language_1", /*todo languages*/"en");
        mapSqlParameterSource.addValue("language_2", /*todo languages*/"ru");
        mapSqlParameterSource.addValue("probability_factor", phraseToCreate.getProbabilityFactor());
        mapSqlParameterSource.addValue("probability_multiplier", phraseToCreate.getProbabilityMultiplier());
        mapSqlParameterSource.addValue("last_access_date", phraseToCreate.getLastAccessDate());
        mapSqlParameterSource.addValue("label", phraseToCreate.getLabel());

        postgresNamedParameterJdbcTemplate.update(INSERT_PHRASE_SQL, mapSqlParameterSource);
        List<String> wordsIds = new ArrayList<>();

        for(Word currentWord : phraseToCreate.getWords()){

            String currentWordId = UUID.randomUUID().toString();

            mapSqlParameterSource = new MapSqlParameterSource();

            mapSqlParameterSource.addValue("word", currentWord.getWord());
            mapSqlParameterSource.addValue("language_code", currentWord.getLanguage());
            mapSqlParameterSource.addValue("transcription", currentWord.getTranscription());
            mapSqlParameterSource.addValue("id", currentWordId);

            postgresNamedParameterJdbcTemplate.update(INSERT_WORD_SQL, mapSqlParameterSource);

            wordsIds.add(currentWordId);
        }

        for(String currentWordId : wordsIds){
            mapSqlParameterSource = new MapSqlParameterSource();
            mapSqlParameterSource.addValue("id", UUID.randomUUID().toString());
            mapSqlParameterSource.addValue("phrase_id", phraseToCreate.getId());
            mapSqlParameterSource.addValue("word_id", currentWordId);
            mapSqlParameterSource.addValue("is_active", true);
            mapSqlParameterSource.addValue("addition_date", Timestamp.from(phraseToCreate.getCreationDate().toInstant(ZoneOffset.UTC)));
            postgresNamedParameterJdbcTemplate.update(INSERT_PHRASE_WORD_SQL, mapSqlParameterSource);
        }

        //todo return actual phrase
        return phraseToCreate;
    }

    public List<Phrase> getAll() {
        return postgresNamedParameterJdbcTemplate
                .query(SELECT_ALL_POSTGRES, POSTGRESQL_PHRASE_MAPPER);
    }

    public List<Phrase> getAllMysql() {
        return mysqlNamedParameterJdbcTemplate.query(SELECT_ALL_MYSQL, new MysqlPhraseMapper(false));
    }

    private final static class PostgreSqlPhraseMapper implements ResultSetExtractor<List<Phrase>> {

        @Override
        public List<Phrase> extractData(ResultSet rs) throws SQLException, DataAccessException {
            List<Phrase> phrases = new ArrayList<>();
            Phrase phrase = null;
            String phraseId = null;
            List<Word> words = new ArrayList<>();
            while (rs.next()){
                String currentPhraseId = rs.getString("phrase_id");
                if(phrase != null && !currentPhraseId.equals(phraseId)){
                    phrases.add(phrase);
                    words = new ArrayList<>();
                }
                words.add(
                            new Word(
                                    rs.getString("word_id"),
                                    rs.getString("word"),
                                    rs.getString("word_language_code"),
                                    rs.getString("transcription"),
                                    LocalDateTime.ofInstant(
                                            Instant.ofEpochMilli(rs.getTimestamp("word_addition_date").getTime()), ZoneId.of("UTC")
                                    )
                            )
                );
                phrase = new Phrase(
                        rs.getString("phrase_id"),
                        LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(rs.getTimestamp("creation_date").getTime()),
                                ZoneId.of("UTC")
                        ),
                        rs.getDouble("probability_factor"),
                        rs.getDouble("probability_multiplier"),
                        rs.getString("label"),
                        rs.getString("user_login"),
                        null,
                        Collections.unmodifiableList(words),
                        rs.getTimestamp("last_access_date") == null ? null : LocalDateTime.ofInstant(
                                        Instant.ofEpochMilli(rs.getTimestamp("last_access_date").getTime()), ZoneId.of("UTC")
                        )
                );
                if(rs.isLast()){
                    phrases.add(phrase);
                }
                phraseId = currentPhraseId;

            }
            return phrases;
        }
    }


    private final static class MysqlPhraseMapper implements ResultSetExtractor<List<Phrase>> {

        private final boolean includeUser;

        public MysqlPhraseMapper(boolean includeUser) {
            this.includeUser = includeUser;
        }

        @Override
        public List<Phrase> extractData(ResultSet rs) throws SQLException, DataAccessException {

            List<Phrase> phrases = new ArrayList<>();
            while (rs.next()) {

                final String forWord = rs.getString("for_word");
                final String natWord = rs.getString("nat_word");
                final String transcription = rs.getString("transcr");
                final String label = rs.getString("label") != null && rs.getString("label").equals("")
                        ? null : rs.getString("label");
                final User user = new User(rs.getString("login"), rs.getString("email"), rs.getString("name"), rs.getString("password"));


                LocalDateTime lastAccessDate = DateTimeUtils.toLocalDateTime(rs.getTimestamp("last_accs_date"));
                double probabilityFactor = new BigDecimal(rs.getFloat("prob_factor"))
                        .setScale(2, RoundingMode.UP).doubleValue();
                double probabilityMultiplier = new BigDecimal(rs.getFloat("rate"))
                        .setScale(2, RoundingMode.UP).doubleValue();

                if (forWord.contains("/") && natWord.contains("/")) {

                    final String[] forWords = forWord.split("/");
                    final String[] natWords = natWord.split("/");
                    if (forWords.length != natWords.length) {
                        LOGGER.error("forWords.length != natWords.length, forWord = " + forWord + ", natWord = " + natWord);
                        continue;
                    }
                    for (int i = 0; i < forWords.length; i++) {
                        Word foreignWord = new Word(null, forWords[i], "en", transcription, null);
                        Word nativeWord = new Word(null, natWords[i], "ru", null, null);
                        List<Word> words = Collections.unmodifiableList(Arrays.asList(foreignWord, nativeWord));
                        phrases.add(
                                new Phrase(
                                        UUID.randomUUID().toString(),
                                        DateTimeUtils.toLocalDateTime(rs.getTimestamp("create_date")),
                                        probabilityFactor,
                                        probabilityMultiplier,
                                        label,
                                        user.getLogin(), includeUser ? user : null,
                                        words,
                                        lastAccessDate
                                )
                        );
                    }

                } else {

                    List<Word> words = new ArrayList<>();


                    if (forWord.contains("/")) {
                        List<Word> engWords = Arrays.stream(forWord.split("/"))
                                .map(wordLiteral -> new Word(null, wordLiteral, "en", transcription, null))
                                .collect(Collectors.toList());
                        words.addAll(engWords);
                    } else {
                        words.add(new Word(null, forWord, "en", transcription, null));
                    }

                    if (natWord.contains("/")) {
                        List<Word> natWords = Arrays.stream(natWord.split("/"))
                                .map(wordLiteral -> new Word(null, wordLiteral, "ru", null, null))
                                .collect(Collectors.toList());
                        words.addAll(natWords);
                    } else {
                        words.add(new Word(null, natWord, "ru", null, null));
                    }

                    phrases.add(new Phrase(
                            UUID.randomUUID().toString(),
                            DateTimeUtils.toLocalDateTime(rs.getTimestamp("create_date")),
                            probabilityFactor,
                            probabilityMultiplier,
                            label,
                            user.getLogin(),
                            includeUser ? user : null,
                            Collections.unmodifiableList(words),
                            lastAccessDate
                    ));
                }


            }
            return phrases;
        }
    }

}
