package com.guessword.entity;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
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
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "answer_sq")
    @SequenceGenerator(schema = "main", sequenceName = "answer_sq", name = "answer_sq", allocationSize = 1)
    private Integer id;

    @Column(nullable = false)
    private ZonedDateTime answered;

    @ManyToOne(fetch = FetchType.LAZY)
    private Question question;

    @Column(nullable = false)
    private Boolean correct;

    @Column(name = "actual_answer")
    private String actualAnswer;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        Answer answer = (Answer) o;
        return getId() != null && Objects.equals(getId(), answer.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
