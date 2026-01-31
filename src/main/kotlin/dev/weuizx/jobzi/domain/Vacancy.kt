package dev.weuizx.jobzi.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "vacancies")
data class Vacancy(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "business_id", nullable = false)
    val businessId: Long,

    @Column(name = "code", nullable = false, unique = true, length = 20)
    var code: String,

    @Column(name = "title", nullable = false)
    var title: String,

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    var description: String,

    @Column(name = "location")
    var location: String? = null,

    @Column(name = "salary", length = 100)
    var salary: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    var status: VacancyStatus = VacancyStatus.DRAFT,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "published_at")
    var publishedAt: OffsetDateTime? = null
)