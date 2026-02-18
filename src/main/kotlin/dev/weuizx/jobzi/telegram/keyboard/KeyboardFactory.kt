package dev.weuizx.jobzi.telegram.keyboard

import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow

/**
 * Утилиты для создания Telegram клавиатур
 */
object KeyboardFactory {

    /**
     * Создает главное меню для суперадмина
     */
    fun createSuperAdminMainMenu(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("📋 Список бизнесов"))
                add(KeyboardButton("➕ Активировать бизнес"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("🔒 Управление доступом"))
                add(KeyboardButton("📊 Статистика"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("📱 Telegram аккаунты"))
                add(KeyboardButton("❓ Помощь"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Создает главное меню для представителя бизнеса
     */
    fun createBusinessMainMenu(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("📋 Мои вакансии"))
                add(KeyboardButton("➕ Новая вакансия"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("👥 Все отклики"))
                add(KeyboardButton("📢 Реклама"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("📊 Статистика"))
                add(KeyboardButton("❓ Помощь"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Создает клавиатуру для управления доступом
     */
    fun createAccessManagementMenu(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("🚫 Заблокировать бизнес"))
                add(KeyboardButton("✅ Разблокировать бизнес"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("◀️ Назад в меню"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Создает простую клавиатуру с кнопкой "Отмена"
     */
    fun createCancelKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("❌ Отмена"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Создает простую клавиатуру с кнопкой "Назад в меню"
     */
    fun createBackToMenuKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("◀️ Назад в меню"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Клавиатуры для создания вакансии (Business)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Клавиатура для пропуска необязательных полей
     */
    fun createSkipKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("-"))
                add(KeyboardButton("❌ Отмена"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для предпросмотра вакансии
     */
    fun createVacancyPreviewKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("Опубликовать"))
                add(KeyboardButton("Черновик"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("Отмена"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для выбора анкеты
     */
    fun createQuestionnaireChoiceKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("Только базовые"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("Добавить свои"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("Пропустить"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для добавления еще одного вопроса
     */
    fun createAddAnotherQuestionKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("Добавить еще"))
                add(KeyboardButton("Готово"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для выбора типа вопроса
     */
    fun createQuestionTypeKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("1"))
                add(KeyboardButton("2"))
                add(KeyboardButton("3"))
                add(KeyboardButton("4"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("❌ Отмена"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для выбора Да/Нет
     */
    fun createYesNoKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("Да"))
                add(KeyboardButton("Нет"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("❌ Отмена"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Клавиатуры для управления вакансиями (Business - Enhanced UX)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Клавиатура для действий с вакансией
     */
    fun createVacancyActionsKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("✏️ Редактировать"))
                add(KeyboardButton("📝 Анкета"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("🔄 Статус"))
                add(KeyboardButton("👥 Отклики"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("🗑 Удалить"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("◀️ Назад"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для подтверждения удаления вакансии
     */
    fun createDeleteConfirmKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("Да, удалить"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("❌ Отмена"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для редактирования полей вакансии
     */
    fun createVacancyEditFieldsKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("1"))
                add(KeyboardButton("2"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("3"))
                add(KeyboardButton("4"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("❌ Отмена"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для изменения статуса вакансии
     */
    fun createVacancyStatusKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("1"))
                add(KeyboardButton("2"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("3"))
                add(KeyboardButton("4"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("❌ Отмена"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для управления анкетой
     */
    fun createQuestionnaireManagementKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("➕ Добавить"))
                add(KeyboardButton("✏️ Редактировать"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("🗑 Удалить"))
                add(KeyboardButton("🔄 Заполнить заново"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("◀️ Назад"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для редактирования вопроса (выбор поля)
     */
    fun createQuestionEditFieldsKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("1"))
                add(KeyboardButton("2"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("❌ Отмена"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Клавиатуры для управления откликами (Business)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Клавиатура для действий с откликом
     */
    fun createApplicationActionsKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("Статус"))
                add(KeyboardButton("Заметка"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("◀️ Назад"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для выбора статуса отклика
     */
    fun createApplicationStatusKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("1"))
                add(KeyboardButton("2"))
                add(KeyboardButton("3"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("4"))
                add(KeyboardButton("5"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("❌ Отмена"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура "Назад"
     */
    fun createBackKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("◀️ Назад"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Клавиатуры для соискателей
    // ═══════════════════════════════════════════════════════════════

    /**
     * Клавиатура для соискателей (главное меню)
     */
    fun createApplicantMainMenu(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("/my"))
                add(KeyboardButton("/help"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для пропуска необязательных вопросов в анкете
     */
    fun createSkipQuestionKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("-"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("❌ Отмена"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Клавиатуры для рекламных рассылок (Business)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Клавиатура для меню рекламных рассылок
     */
    fun createBroadcastMenuKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("➕ Создать рекламу"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("📡 Мои чаты"))
                add(KeyboardButton("📋 Мои рассылки"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("◀️ Назад в меню"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для предпросмотра рекламной кампании
     */
    fun createBroadcastPreviewKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("✅ Отправить"))
                add(KeyboardButton("💾 Сохранить"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("✏️ Редактировать"))
                add(KeyboardButton("❌ Отмена"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для управления чатами
     */
    fun createChannelManagementKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("➕ Добавить чат"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("🗑 Удалить чат"))
                add(KeyboardButton("🔄 Проверить чаты"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("◀️ Назад"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для подтверждения удаления канала
     */
    fun createChannelDeleteConfirmKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("Да, удалить"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("❌ Отмена"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для действий с деталями кампании
     */
    fun createCampaignDetailsKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("⏰ Настроить расписание"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("🚀 Отправить сейчас"))
                add(KeyboardButton("🗑 Удалить кампанию"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("◀️ Назад"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для выбора типа расписания
     */
    fun createScheduleTypeKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("1"))
                add(KeyboardButton("2"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("3"))
                add(KeyboardButton("4"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("5"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("❌ Отмена"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для просмотра откликов вакансии
     */
    fun createVacancyApplicationsKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("📥 Экспорт в Excel"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("◀️ Назад"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Клавиатуры для управления Telegram пулом (SuperAdmin)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Клавиатура для меню управления Telegram аккаунтами
     */
    fun createTelegramPoolMenuKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("📋 Список аккаунтов"))
                add(KeyboardButton("➕ Добавить аккаунт"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("📊 Статус пула"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("◀️ Назад в меню"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для действий с конкретным аккаунтом
     */
    fun createAccountActionsKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("🔑 Аутентификация"))
                add(KeyboardButton("🗑 Удалить"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("◀️ Назад"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }

    /**
     * Клавиатура для подтверждения удаления аккаунта
     */
    fun createDeleteAccountConfirmKeyboard(): ReplyKeyboardMarkup {
        val keyboard = listOf(
            KeyboardRow().apply {
                add(KeyboardButton("Да, удалить"))
            },
            KeyboardRow().apply {
                add(KeyboardButton("❌ Отмена"))
            }
        )

        return ReplyKeyboardMarkup().apply {
            this.keyboard = keyboard
            this.resizeKeyboard = true
            this.oneTimeKeyboard = false
        }
    }
}
