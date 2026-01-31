package dev.weuizx.jobzi.repository

import dev.weuizx.jobzi.domain.Question
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface QuestionRepository : JpaRepository<Question, Long> {
    fun findByVacancyIdOrderByOrderIndex(vacancyId: Long): List<Question>
    fun findByVacancyId(vacancyId: Long): List<Question>
    fun deleteByVacancyId(vacancyId: Long)
}