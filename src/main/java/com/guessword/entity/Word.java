package com.guessword.entity;


import javax.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(schema = "main")
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "word_seq")
    private Integer id;

    @Column(nullable = false)
    private String word;

    @Column(nullable = false)
    private String language;

    private String transcription;

    @ManyToOne(fetch = FetchType.LAZY)
    private Question question;

    @Override
    public String toString() {
        return word + (transcription != null ? " [" + transcription + "]" : "");
    }
}


