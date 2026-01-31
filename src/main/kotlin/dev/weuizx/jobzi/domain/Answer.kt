package dev.weuizx.jobzi.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(
    name = "answers",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_application_question_answer", columnNames = ["application_id", "question_id"])
    ]
)
data class Answer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "application_id", nullable = false)
    val applicationId: Long,

    @Column(name = "question_id", nullable = false)
    val questionId: Long,

    @Column(name = "answer_text", nullable = false, columnDefinition = "TEXT")
    var answerText: String,

    // Snapshot of question at the time of application submission
    // This preserves context even if the question is modified or deleted later
    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    val questionText: String,

    @Column(name = "question_type", nullable = false, length = 20)
    val questionType: String,

    @Column(name = "question_order", nullable = false)
    val questionOrder: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
