package dev.weuizx.jobzi.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(
    name = "business_users",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_business_user", columnNames = ["business_id", "user_id"])
    ]
)
data class BusinessUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "business_id", nullable = false)
    val businessId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    var role: BusinessRole = BusinessRole.MANAGER,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
