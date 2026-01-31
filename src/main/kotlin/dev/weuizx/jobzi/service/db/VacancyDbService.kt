package dev.weuizx.jobzi.service.db

import dev.weuizx.jobzi.domain.Vacancy
import dev.weuizx.jobzi.domain.VacancyStatus
import dev.weuizx.jobzi.repository.VacancyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
@Transactional
class VacancyDbService(
    private val vacancyRepository: VacancyRepository
) {
    fun findById(id: Long): Vacancy? = vacancyRepository.findById(id).orElse(null)

    fun findByBusinessId(businessId: Long): List<Vacancy> =
        vacancyRepository.findByBusinessId(businessId)

    fun findByBusinessIdAndStatus(businessId: Long, status: VacancyStatus): List<Vacancy> =
        vacancyRepository.findByBusinessIdAndStatus(businessId, status)

    fun findByCode(code: String): Vacancy? = vacancyRepository.findByCode(code)

    fun save(vacancy: Vacancy): Vacancy = vacancyRepository.save(vacancy)

    /**
     * Создает новую вакансию с уникальным кодом
     */
    fun createVacancy(
        businessId: Long,
        title: String,
        description: String,
        location: String? = null,
        salary: String? = null,
        status: VacancyStatus = VacancyStatus.DRAFT
    ): Vacancy {
        val code = generateUniqueCode()

        val vacancy = Vacancy(
            businessId = businessId,
            code = code,
            title = title,
            description = description,
            location = location,
            salary = salary,
            status = status,
            publishedAt = if (status == VacancyStatus.ACTIVE) OffsetDateTime.now() else null
        )

        return vacancyRepository.save(vacancy)
    }

    /**
     * Публикует вакансию (переводит из DRAFT в ACTIVE)
     */
    fun publishVacancy(vacancyId: Long): Vacancy? {
        val vacancy = findById(vacancyId) ?: return null

        vacancy.status = VacancyStatus.ACTIVE
        vacancy.publishedAt = OffsetDateTime.now()
        vacancy.updatedAt = OffsetDateTime.now()

        return vacancyRepository.save(vacancy)
    }

    /**
     * Изменяет статус вакансии
     */
    fun changeStatus(vacancyId: Long, newStatus: VacancyStatus): Vacancy? {
        val vacancy = findById(vacancyId) ?: return null

        vacancy.status = newStatus
        vacancy.updatedAt = OffsetDateTime.now()

        // Если публикуем впервые, устанавливаем publishedAt
        if (newStatus == VacancyStatus.ACTIVE && vacancy.publishedAt == null) {
            vacancy.publishedAt = OffsetDateTime.now()
        }

        return vacancyRepository.save(vacancy)
    }

    /**
     * Обновляет название вакансии
     */
    fun updateTitle(vacancyId: Long, newTitle: String): Vacancy? {
        val vacancy = findById(vacancyId) ?: return null
        vacancy.title = newTitle
        vacancy.updatedAt = OffsetDateTime.now()
        return vacancyRepository.save(vacancy)
    }

    /**
     * Обновляет описание вакансии
     */
    fun updateDescription(vacancyId: Long, newDescription: String): Vacancy? {
        val vacancy = findById(vacancyId) ?: return null
        vacancy.description = newDescription
        vacancy.updatedAt = OffsetDateTime.now()
        return vacancyRepository.save(vacancy)
    }

    /**
     * Обновляет локацию вакансии
     */
    fun updateLocation(vacancyId: Long, newLocation: String?): Vacancy? {
        val vacancy = findById(vacancyId) ?: return null
        vacancy.location = newLocation
        vacancy.updatedAt = OffsetDateTime.now()
        return vacancyRepository.save(vacancy)
    }

    /**
     * Обновляет зарплату вакансии
     */
    fun updateSalary(vacancyId: Long, newSalary: String?): Vacancy? {
        val vacancy = findById(vacancyId) ?: return null
        vacancy.salary = newSalary
        vacancy.updatedAt = OffsetDateTime.now()
        return vacancyRepository.save(vacancy)
    }

    /**
     * Удаляет вакансию по ID
     * Внимание: также удаляются все связанные вопросы и отклики (cascade)
     */
    fun deleteVacancy(vacancyId: Long): Boolean {
        val vacancy = findById(vacancyId) ?: return false
        vacancyRepository.delete(vacancy)
        return true
    }

    /**
     * Генерирует уникальный 6-символьный код для вакансии
     * Формат: ABC123 (3 буквы + 3 цифры)
     */
    private fun generateUniqueCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val digitsRange = '0'..'9'

        var code: String
        var attempts = 0
        val maxAttempts = 100

        do {
            val letters = (1..3).map { chars.random() }.joinToString("")
            val numbers = (1..3).map { digitsRange.random() }.joinToString("")
            code = letters + numbers
            attempts++

            if (attempts >= maxAttempts) {
                throw IllegalStateException("Не удалось сгенерировать уникальный код после $maxAttempts попыток")
            }
        } while (vacancyRepository.existsByCode(code))

        return code
    }
}