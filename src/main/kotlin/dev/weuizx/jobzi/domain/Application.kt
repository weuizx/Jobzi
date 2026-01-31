package dev.weuizx.jobzi.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(
    name = "applications",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_vacancy_user_application", columnNames = ["vacancy_id", "user_id"])
    ]
)
data class Application(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "vacancy_id", nullable = false)
    val vacancyId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    var status: ApplicationStatus = ApplicationStatus.NEW,

    @Column(name = "notes", columnDefinition = "TEXT")
    var notes: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
