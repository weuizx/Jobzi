package dev.weuizx.jobzi.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "questions")
data class Question(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "vacancy_id", nullable = false)
    val vacancyId: Long,

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    var questionText: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 50)
    var questionType: QuestionType = QuestionType.TEXT,

    @Column(name = "is_required", nullable = false)
    var isRequired: Boolean = true,

    @Column(name = "order_index", nullable = false)
    var orderIndex: Int,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", columnDefinition = "JSONB")
    var options: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)