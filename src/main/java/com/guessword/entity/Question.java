package com.guessword.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

import javax.persistence.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
@Entity
@Table(schema = "main")
public class Question implements BaseQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "question_seq")
    private Integer id;

    @Column(nullable = false)
    private Double probabilityFactor;

    @Column(nullable = false)
    private Double probabilityMultiplier;

    @Column(nullable = false)
    private LocalDateTime created;

    private LocalDateTime lastAccessed;

    private String tag;

    @OneToMany(cascade = CascadeType.ALL,fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "question_id")
    private List<Word> words;

    @JsonIgnore
    @Transient
    private int indexStart;

    @JsonIgnore
    @Transient
    private int indexEnd;

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
}
