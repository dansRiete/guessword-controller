package com.guessword.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.apache.commons.collections4.CollectionUtils;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import javax.persistence.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode
@Entity
@Table(schema = "main")
public class Question implements BaseQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "question_sq")
    @SequenceGenerator(schema = "main", sequenceName = "question_sq", name = "question_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    @Column(nullable = false)
    @ToString.Include
    private Double studyPoints;

    @Column(nullable = false)
    private Double studyPointsMultiplier;

    @Column(nullable = false)
    private ZonedDateTime created;

    private ZonedDateTime lastAccessed;

    private String tag;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @ToString.Include
    private List<Word> words = new ArrayList<>();

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @Fetch(value = FetchMode.SUBSELECT)
    @JsonIgnore
    private List<Answer> answers = new ArrayList<>();

    @JsonIgnore
    @Transient
    private int indexStart;

    @JsonIgnore
    @Transient
    private int indexEnd;

    /**
     * This method is used when limiting collections in order to select random elements
     * @return hash of fields which are not going to be modified and unique for every question
     */
    public int random() {
        return Objects.hash(id, created);
    }

    public boolean isInList(HashSet<String> tags) {

        if (tags != null) {
            if (tags.isEmpty()) {
                return true;
            }
            for (String str : tags) {
                if (tag != null && tag.equalsIgnoreCase(str)) {
                    return true;
                }
            }
            return false;
        } else {
            return true;
        }
    }

    public void setWords(final List<Word> words) {
        if (CollectionUtils.isEmpty(words)) {
            return;
        }
        this.words.clear();
        this.words.addAll(words);
        this.words.forEach(word -> word.setQuestion(this));
    }

    public void setAnswers(final List<Answer> answers) {
        if (CollectionUtils.isEmpty(answers)) {
            return;
        }
        this.answers.clear();
        this.answers.addAll(answers);
        this.answers.forEach(word -> word.setQuestion(this));
    }
}
