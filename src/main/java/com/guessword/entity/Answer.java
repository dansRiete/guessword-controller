package com.guessword.entity;

import java.time.ZonedDateTime;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

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
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "answer_sq")
    @SequenceGenerator(schema = "main", sequenceName = "answer_sq", name = "answer_sq", allocationSize = 1)
    private Integer id;

    @Column(nullable = false)
    private ZonedDateTime answered;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "questionId")
    private Question question;

    @Column(nullable = false)
    private Boolean correct;

}
