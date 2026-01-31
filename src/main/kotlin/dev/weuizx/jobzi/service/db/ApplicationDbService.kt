package dev.weuizx.jobzi.service.db

import dev.weuizx.jobzi.domain.Answer
import dev.weuizx.jobzi.domain.Application
import dev.weuizx.jobzi.domain.ApplicationStatus
import dev.weuizx.jobzi.repository.AnswerRepository
import dev.weuizx.jobzi.repository.ApplicationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
@Transactional
class ApplicationDbService(
    private val applicationRepository: ApplicationRepository,
    private val answerRepository: AnswerRepository
) {

    fun findById(id: Long): Application? = applicationRepository.findById(id).orElse(null)

    fun findByVacancyId(vacancyId: Long): List<Application> =
        applicationRepository.findByVacancyId(vacancyId)

    fun findByVacancyIdAndStatus(vacancyId: Long, status: ApplicationStatus): List<Application> =
        applicationRepository.findByVacancyIdAndStatus(vacancyId, status)

    fun findByUserId(userId: Long): List<Application> =
        applicationRepository.findByUserId(userId)

    fun findByVacancyIdAndUserId(vacancyId: Long, userId: Long): Application? =
        applicationRepository.findByVacancyIdAndUserId(vacancyId, userId)

    fun existsByVacancyIdAndUserId(vacancyId: Long, userId: Long): Boolean =
        applicationRepository.existsByVacancyIdAndUserId(vacancyId, userId)

    fun save(application: Application): Application = applicationRepository.save(application)

    /**
     * Создает новый отклик на вакансию
     */
    fun createApplication(
        vacancyId: Long,
        userId: Long
    ): Application {
        val application = Application(
            vacancyId = vacancyId,
            userId = userId,
            status = ApplicationStatus.NEW
        )
        return applicationRepository.save(application)
    }

    /**
     * Сохраняет ответ на вопрос анкеты
     * Сохраняет snapshot вопроса для сохранения контекста даже при изменении/удалении вопроса
     */
    fun saveAnswer(
        applicationId: Long,
        questionId: Long,
        answerText: String,
        questionText: String,
        questionType: String,
        questionOrder: Int
    ): Answer {
        val answer = Answer(
            applicationId = applicationId,
            questionId = questionId,
            answerText = answerText,
            questionText = questionText,
            questionType = questionType,
            questionOrder = questionOrder
        )
        return answerRepository.save(answer)
    }

    /**
     * Получает все ответы для отклика
     */
    fun getAnswersByApplicationId(applicationId: Long): List<Answer> =
        answerRepository.findByApplicationId(applicationId)

    /**
     * Обновляет статус отклика
     */
    fun updateStatus(applicationId: Long, newStatus: ApplicationStatus): Application? {
        val application = findById(applicationId) ?: return null
        application.status = newStatus
        application.updatedAt = OffsetDateTime.now()
        return applicationRepository.save(application)
    }

    /**
     * Добавляет заметку к отклику
     */
    fun addNotes(applicationId: Long, notes: String): Application? {
        val application = findById(applicationId) ?: return null
        application.notes = notes
        application.updatedAt = OffsetDateTime.now()
        return applicationRepository.save(application)
    }
}