package com.guessword.entity;


import jakarta.persistence.*;
import java.util.Objects;
import lombok.*;
import org.hibernate.Hibernate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        Word word = (Word) o;
        return getId() != null && Objects.equals(getId(), word.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}


