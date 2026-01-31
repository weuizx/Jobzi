package dev.weuizx.jobzi.repository

import dev.weuizx.jobzi.domain.Answer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AnswerRepository : JpaRepository<Answer, Long> {
    fun findByApplicationId(applicationId: Long): List<Answer>
    fun findByApplicationIdAndQuestionId(applicationId: Long, questionId: Long): Answer?
    fun deleteByApplicationId(applicationId: Long)
}
