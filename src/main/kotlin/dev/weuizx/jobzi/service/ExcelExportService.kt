package dev.weuizx.jobzi.service

import dev.weuizx.jobzi.domain.Application
import dev.weuizx.jobzi.domain.Vacancy
import dev.weuizx.jobzi.repository.AnswerRepository
import dev.weuizx.jobzi.service.db.UserDbService
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter

/**
 * Сервис для экспорта данных в Excel
 */
@Service
class ExcelExportService(
    private val userDbService: UserDbService,
    private val answerRepository: AnswerRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    /**
     * Экспортирует отклики на вакансию в Excel файл
     */
    fun exportApplicationsToExcel(vacancy: Vacancy, applications: List<Application>): ByteArray {
        val workbook = XSSFWorkbook()

        try {
            // Создаем лист
            val sheet = workbook.createSheet("Отклики")

            // Создаем стили
            val headerStyle = createHeaderStyle(workbook)
            val dateStyle = createDateStyle(workbook)
            val wrapStyle = createWrapTextStyle(workbook)

            var rowNum = 0

            // Заголовок с информацией о вакансии
            val titleRow = sheet.createRow(rowNum++)
            val titleCell = titleRow.createCell(0)
            titleCell.setCellValue("Отклики на вакансию: ${vacancy.title}")
            titleCell.setCellStyle(createTitleStyle(workbook))
            sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 7))

            rowNum++ // Пустая строка

            // Информация о вакансии
            val infoRow1 = sheet.createRow(rowNum++)
            infoRow1.createCell(0).setCellValue("Описание:")
            val descCell = infoRow1.createCell(1)
            descCell.setCellValue(vacancy.description)
            descCell.setCellStyle(wrapStyle)

            val infoRow2 = sheet.createRow(rowNum++)
            infoRow2.createCell(0).setCellValue("Локация:")
            infoRow2.createCell(1).setCellValue(vacancy.location ?: "-")

            val infoRow3 = sheet.createRow(rowNum++)
            infoRow3.createCell(0).setCellValue("Зарплата:")
            infoRow3.createCell(1).setCellValue(vacancy.salary ?: "-")

            val infoRow4 = sheet.createRow(rowNum++)
            infoRow4.createCell(0).setCellValue("Статус:")
            infoRow4.createCell(1).setCellValue(vacancy.status.toString())

            rowNum++ // Пустая строка

            val statsRow = sheet.createRow(rowNum++)
            statsRow.createCell(0).setCellValue("Всего откликов: ${applications.size}")
            val statCell = statsRow.getCell(0)
            statCell.setCellStyle(createBoldStyle(workbook))

            rowNum++ // Пустая строка

            // Заголовки колонок
            val headerRow = sheet.createRow(rowNum++)
            val headers = listOf(
                "№", "ФИО", "Username", "Телефон", "Дата отклика",
                "Статус", "Ответы на вопросы", "Заметки"
            )

            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                cell.setCellStyle(headerStyle)
            }

            // Данные откликов
            applications.sortedByDescending { it.createdAt }.forEachIndexed { index, application ->
                val row = sheet.createRow(rowNum++)

                // Получаем пользователя
                val user = userDbService.findById(application.userId)

                // Получаем ответы на вопросы
                val answers = answerRepository.findByApplicationId(application.id!!)
                val answersText = if (answers.isNotEmpty()) {
                    answers.joinToString("\n\n") { answer ->
                        "${answer.questionText}: ${answer.answerText}"
                    }
                } else {
                    "-"
                }

                // Заполняем данные
                row.createCell(0).setCellValue((index + 1).toDouble())
                row.createCell(1).setCellValue(buildString {
                    append(user?.firstName ?: "")
                    if (user?.lastName != null) {
                        append(" ${user.lastName}")
                    }
                }.ifEmpty { "Не указано" })
                row.createCell(2).setCellValue(user?.username?.let { "@$it" } ?: "-")
                row.createCell(3).setCellValue(user?.phoneNumber ?: "-")

                val dateCell = row.createCell(4)
                dateCell.setCellValue(application.createdAt.format(dateFormatter))
                dateCell.setCellStyle(dateStyle)

                row.createCell(5).setCellValue(getStatusRussian(application.status.toString()))

                val answersCell = row.createCell(6)
                answersCell.setCellValue(answersText)
                answersCell.setCellStyle(wrapStyle)

                val notesCell = row.createCell(7)
                notesCell.setCellValue(application.notes ?: "-")
                notesCell.setCellStyle(wrapStyle)
            }

            // Автоматическая ширина колонок
            for (i in 0..7) {
                sheet.autoSizeColumn(i)
                // Ограничиваем максимальную ширину
                val currentWidth = sheet.getColumnWidth(i)
                if (currentWidth > 15000) {
                    sheet.setColumnWidth(i, 15000)
                }
            }

            // Устанавливаем минимальную ширину для колонки с ответами
            if (sheet.getColumnWidth(6) < 10000) {
                sheet.setColumnWidth(6, 10000)
            }

            // Возвращаем байты файла
            val outputStream = ByteArrayOutputStream()
            workbook.write(outputStream)
            return outputStream.toByteArray()

        } finally {
            workbook.close()
        }
    }

    private fun getStatusRussian(status: String): String {
        return when (status) {
            "NEW" -> "Новый"
            "VIEWED" -> "Просмотрен"
            "INTERVIEW" -> "Собеседование"
            "ACCEPTED" -> "Принят"
            "REJECTED" -> "Отклонен"
            else -> status
        }
    }

    private fun createHeaderStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        font.fontHeightInPoints = 12
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.borderBottom = BorderStyle.THIN
        style.borderTop = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
        style.alignment = HorizontalAlignment.CENTER
        style.verticalAlignment = VerticalAlignment.CENTER
        style.wrapText = true
        return style
    }

    private fun createTitleStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        font.fontHeightInPoints = 16
        style.setFont(font)
        style.alignment = HorizontalAlignment.CENTER
        style.verticalAlignment = VerticalAlignment.CENTER
        return style
    }

    private fun createBoldStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        style.setFont(font)
        return style
    }

    private fun createDateStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        style.alignment = HorizontalAlignment.CENTER
        return style
    }

    private fun createWrapTextStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        style.wrapText = true
        style.verticalAlignment = VerticalAlignment.TOP
        return style
    }
}
