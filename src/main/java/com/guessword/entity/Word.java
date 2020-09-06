package com.guessword.entity;


import javax.persistence.*;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
@Entity
@Table(schema = "main")
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "word_sq")
    @SequenceGenerator(schema = "main", sequenceName = "word_sq", name = "word_sq", allocationSize = 1)
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


