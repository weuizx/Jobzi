package dev.weuizx.jobzi.service.db

import com.fasterxml.jackson.databind.ObjectMapper
import dev.weuizx.jobzi.domain.Question
import dev.weuizx.jobzi.domain.QuestionType
import dev.weuizx.jobzi.repository.QuestionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class QuestionDbService(
    private val questionRepository: QuestionRepository,
    private val objectMapper: ObjectMapper
) {

    fun findByVacancyId(vacancyId: Long): List<Question> =
        questionRepository.findByVacancyIdOrderByOrderIndex(vacancyId)

    fun save(question: Question): Question = questionRepository.save(question)

    fun deleteByVacancyId(vacancyId: Long) = questionRepository.deleteByVacancyId(vacancyId)

    /**
     * Создает дефолтные вопросы для вакансии
     */
    fun createDefaultQuestions(vacancyId: Long): List<Question> {
        val defaultQuestions = listOf(
            Question(
                vacancyId = vacancyId,
                questionText = "Как вас зовут?",
                questionType = QuestionType.TEXT,
                isRequired = true,
                orderIndex = 1
            ),
            Question(
                vacancyId = vacancyId,
                questionText = "Ваш номер телефона",
                questionType = QuestionType.PHONE,
                isRequired = true,
                orderIndex = 2
            ),
            Question(
                vacancyId = vacancyId,
                questionText = "Сколько вам лет?",
                questionType = QuestionType.NUMBER,
                isRequired = false,
                orderIndex = 3
            )
        )

        return questionRepository.saveAll(defaultQuestions)
    }

    /**
     * Создает кастомный вопрос для вакансии
     */
    fun createCustomQuestion(
        vacancyId: Long,
        questionText: String,
        questionType: QuestionType,
        isRequired: Boolean,
        options: List<String>? = null
    ): Question {
        // Получаем максимальный order_index для вакансии
        val existingQuestions = questionRepository.findByVacancyId(vacancyId)
        val nextOrderIndex = (existingQuestions.maxOfOrNull { it.orderIndex } ?: 0) + 1

        // Сериализуем опции в JSON, если они есть
        val optionsJson = if (questionType == QuestionType.CHOICE && options != null) {
            objectMapper.writeValueAsString(options)
        } else null

        val question = Question(
            vacancyId = vacancyId,
            questionText = questionText,
            questionType = questionType,
            isRequired = isRequired,
            orderIndex = nextOrderIndex,
            options = optionsJson
        )

        return questionRepository.save(question)
    }

    /**
     * Парсит опции из JSON
     */
    fun parseOptions(optionsJson: String?): List<String>? {
        if (optionsJson.isNullOrBlank()) return null
        return try {
            objectMapper.readValue(optionsJson, Array<String>::class.java).toList()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Находит вопрос по ID
     */
    fun findById(id: Long): Question? = questionRepository.findById(id).orElse(null)

    /**
     * Удаляет вопрос по ID
     */
    fun deleteById(id: Long) {
        questionRepository.deleteById(id)
    }

    /**
     * Обновляет текст вопроса
     */
    fun updateQuestionText(questionId: Long, newText: String): Question? {
        val question = findById(questionId) ?: return null
        question.questionText = newText
        return questionRepository.save(question)
    }

    /**
     * Обновляет обязательность вопроса
     */
    fun updateQuestionRequired(questionId: Long, isRequired: Boolean): Question? {
        val question = findById(questionId) ?: return null
        question.isRequired = isRequired
        return questionRepository.save(question)
    }

    /**
     * Обновляет опции вопроса (для CHOICE типа)
     */
    fun updateQuestionOptions(questionId: Long, options: List<String>): Question? {
        val question = findById(questionId) ?: return null
        if (question.questionType != QuestionType.CHOICE) return null

        val optionsJson = objectMapper.writeValueAsString(options)
        question.options = optionsJson
        return questionRepository.save(question)
    }

    /**
     * Получает количество вопросов для вакансии
     */
    fun countByVacancyId(vacancyId: Long): Int {
        return questionRepository.findByVacancyIdOrderByOrderIndex(vacancyId).size
    }
}