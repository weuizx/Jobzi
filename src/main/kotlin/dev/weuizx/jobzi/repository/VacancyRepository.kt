package dev.weuizx.jobzi.repository

import dev.weuizx.jobzi.domain.Vacancy
import dev.weuizx.jobzi.domain.VacancyStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface VacancyRepository : JpaRepository<Vacancy, Long> {
    fun findByBusinessId(businessId: Long): List<Vacancy>
    fun findByBusinessIdAndStatus(businessId: Long, status: VacancyStatus): List<Vacancy>
    fun findByCode(code: String): Vacancy?
    fun existsByCode(code: String): Boolean
}