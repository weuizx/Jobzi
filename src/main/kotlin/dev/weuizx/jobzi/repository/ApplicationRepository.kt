package dev.weuizx.jobzi.repository

import dev.weuizx.jobzi.domain.Application
import dev.weuizx.jobzi.domain.ApplicationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ApplicationRepository : JpaRepository<Application, Long> {
    fun findByVacancyId(vacancyId: Long): List<Application>
    fun findByVacancyIdAndStatus(vacancyId: Long, status: ApplicationStatus): List<Application>
    fun findByUserId(userId: Long): List<Application>
    fun findByVacancyIdAndUserId(vacancyId: Long, userId: Long): Application?
    fun existsByVacancyIdAndUserId(vacancyId: Long, userId: Long): Boolean
}