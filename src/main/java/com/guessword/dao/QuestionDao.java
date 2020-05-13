package com.guessword.dao;

import com.guessword.entity.Word;
import com.guessword.entity.Question;
import com.guessword.entity.User;
import com.guessword.utils.DateTimeUtils;
import org.apache.commons.lang3.StringUtils;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import javax.sql.DataSource;

@Repository
public class QuestionDao {

    private final static Logger LOGGER = Logger.getLogger(QuestionDao.class);

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
                    "ON user_id = guessword.users.login WHERE user_id != 'aleks'";

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

    public QuestionDao(
            NamedParameterJdbcTemplate postgresNamedParameterJdbcTemplate,
            @Qualifier("mysqlDatasource") DataSource mysqlDatasource
    ) {
        this.postgresNamedParameterJdbcTemplate = postgresNamedParameterJdbcTemplate;
        this.mysqlNamedParameterJdbcTemplate = new NamedParameterJdbcTemplate(mysqlDatasource);
    }

    @Transactional
    public Question createPhrase(Question questionToCreate) {

        MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource();

        mapSqlParameterSource.addValue("id", questionToCreate.getId());
        mapSqlParameterSource.addValue("user_login", "alex");
        mapSqlParameterSource.addValue("is_active", true);
        /*mapSqlParameterSource
                .addValue("creation_date", Timestamp.from(questionToCreate.getCreationDate().toInstant(ZoneOffset.UTC)));*/
        mapSqlParameterSource.addValue("language_1", /*todo languages*/"en");
        mapSqlParameterSource.addValue("language_2", /*todo languages*/"ru");
        mapSqlParameterSource.addValue("probability_factor", questionToCreate.getStudyPoints());
        mapSqlParameterSource.addValue("probability_multiplier", questionToCreate.getStudyPointsMultiplier());
        mapSqlParameterSource.addValue("last_access_date", questionToCreate.getLastAccessed());
        mapSqlParameterSource.addValue("label", questionToCreate.getTag());

        postgresNamedParameterJdbcTemplate.update(INSERT_PHRASE_SQL, mapSqlParameterSource);
        List<String> wordsIds = new ArrayList<>();

        for (Word currentWord : questionToCreate.getWords()) {

            String currentWordId = UUID.randomUUID().toString();

            mapSqlParameterSource = new MapSqlParameterSource();

            mapSqlParameterSource.addValue("word", currentWord.getWord());
            mapSqlParameterSource.addValue("language_code", currentWord.getLanguage());
            mapSqlParameterSource.addValue("transcription", currentWord.getTranscription());
            mapSqlParameterSource.addValue("id", currentWordId);

            postgresNamedParameterJdbcTemplate.update(INSERT_WORD_SQL, mapSqlParameterSource);

            wordsIds.add(currentWordId);
        }

        for (String currentWordId : wordsIds) {
            mapSqlParameterSource = new MapSqlParameterSource();
            mapSqlParameterSource.addValue("id", UUID.randomUUID().toString());
            mapSqlParameterSource.addValue("phrase_id", questionToCreate.getId());
            mapSqlParameterSource.addValue("word_id", currentWordId);
            mapSqlParameterSource.addValue("is_active", true);
            /*mapSqlParameterSource.addValue("addition_date",
                    Timestamp.from(questionToCreate.getCreationDate().toInstant(ZoneOffset.UTC)));*/
            postgresNamedParameterJdbcTemplate.update(INSERT_PHRASE_WORD_SQL, mapSqlParameterSource);
        }

        //todo return actual phrase
        return questionToCreate;
    }

    public List<Question> getAllMysql() {
        return mysqlNamedParameterJdbcTemplate.query(SELECT_ALL_MYSQL, new MysqlPhraseMapper(false));
    }

    private final static class MysqlPhraseMapper implements ResultSetExtractor<List<Question>> {

        private final boolean includeUser;

        public MysqlPhraseMapper(boolean includeUser) {
            this.includeUser = includeUser;
        }

        @Override
        public List<Question> extractData(ResultSet rs) throws SQLException, DataAccessException {

            List<Question> questions = new ArrayList<>();
            int rsCounter = 0;
            while (rs.next()) {
                rsCounter++;

                final String forWord = rs.getString("for_word");
                final String natWord = rs.getString("nat_word");
                final String transcription = rs.getString("transcr");
                final String label = rs.getString("label") != null && rs.getString("label").equals("")
                        ? null : rs.getString("label");
                final User user = new User(rs.getString("login"), rs.getString("email"), rs.getString("name"),
                        rs.getString("password"));

                LocalDateTime lastAccessDate = DateTimeUtils.toLocalDateTime(rs.getTimestamp("last_accs_date"));
                double probabilityFactor = new BigDecimal(rs.getFloat("prob_factor"))
                        .setScale(2, RoundingMode.UP).doubleValue();
                double probabilityMultiplier = new BigDecimal(rs.getFloat("rate"))
                        .setScale(2, RoundingMode.UP).doubleValue();

                if (forWord.contains("/") && natWord.contains("/")) {

                    final Question question = Question.builder()
                        .id(rs.getInt("id"))
                        .created(DateTimeUtils.toLocalDateTime(rs.getTimestamp("create_date")))
                        .studyPoints(probabilityFactor)
                        .studyPointsMultiplier(probabilityMultiplier)
                        .lastAccessed(lastAccessDate)
                        .tag(label).build();

                    final String[] forWords = forWord.split("/");
                    final String[] natWords = natWord.split("/");
                    if (forWords.length != natWords.length) {
                        LOGGER.error(
                                "forWords.length != natWords.length, forWord = " + forWord + ", natWord = " + natWord);
                        continue;
                    }
                    for (int i = 0; i < forWords.length; i++) {
                        Word foreignWord = Word.builder()
                            .word(forWords[i])
                            .language("en")
                            .transcription(StringUtils.isEmpty(transcription) ? null : transcription)
                            .question(question)
                            .build();

                        Word nativeWord = Word.builder().question(question).word(natWords[i]).language("ru").build();
                        List<Word> words = Collections.unmodifiableList(Arrays.asList(foreignWord, nativeWord));

                        question.setWords(Collections.unmodifiableList(words));
                        questions.add(question);
                    }

                } else {

                    List<Word> words = new ArrayList<>();

                    final Question question = Question.builder()
                        .id(rs.getInt("id"))
                        .studyPoints(probabilityFactor)
                        .studyPointsMultiplier(probabilityMultiplier)
                        .created(DateTimeUtils.toLocalDateTime(rs.getTimestamp("create_date")))
                        .lastAccessed(lastAccessDate)
                        .tag(label)
                        .words(Collections.unmodifiableList(words))
                        .build();

                    if (forWord.contains("/")) {
                        List<Word> engWords = Arrays.stream(forWord.split("/"))
                                .map(wordLiteral -> Word.builder()
                                    .word(wordLiteral)
                                    .language("en")
                                    .question(question)
                                    .transcription(StringUtils.isEmpty(transcription) ? null : transcription)
                                    .build())
                                .collect(Collectors.toList());
                        words.addAll(engWords);
                    } else {
                        words.add(Word.builder().word(forWord).language("en").question(question)
                            .transcription(StringUtils.isEmpty(transcription) ? null : transcription).build());
                    }

                    if (natWord.contains("/")) {
                        List<Word> natWords = Arrays.stream(natWord.split("/"))
                                .map(wordLiteral -> new Word(null, wordLiteral, "ru", null, question))
                                .collect(Collectors.toList());
                        words.addAll(natWords);
                    } else {
                        words.add(new Word(null, natWord, "ru", null, question));
                    }

                    questions.add(question);
                }

            }

            LOGGER.debug(String.format("Extraction completed. RsCount: %d", rsCounter));

            return questions;
        }
    }

}
