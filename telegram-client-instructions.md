# Telegram User Client - Инструкция

## ⚠️ ВАЖНО: Текущая версия

Это **STUB-версия** (заглушка) Telegram User Client. API endpoints созданы и готовы к работе, но реальная интеграция с Telegram требует доработки.

### Почему stub?

Java/Kotlin библиотеки для Telegram Client API:
- **TDLight** - нестабильные репозитории, сложная установка нативных библиотек
- **Kotlogram** - устаревший API, несовместимость версий
- **tdlib-java** - требует нативные библиотеки и сложную настройку

### Рекомендуемые решения:

#### ✅ Вариант 1: Python через Process API (Лучший вариант)

Используйте **Pyrogram** или **Telethon** (Python) и вызывайте их из Java:

```python
# telegram_sender.py
from pyrogram import Client

app = Client("my_account", api_id=API_ID, api_hash=API_HASH)

async def send_message(chat_id, message):
    async with app:
        await app.send_message(chat_id, message)
```

Вызов из Kotlin:
```kotlin
ProcessBuilder("python3", "telegram_sender.py", chatId, message)
    .start()
    .waitFor()
```

#### ✅ Вариант 2: REST API через отдельный микросервис

Создайте отдельный Python сервис (FastAPI/Flask) с Pyrogram/Telethon и вызывайте через HTTP.

#### ⚙️ Вариант 3: Доработать текущую версию

Дождаться стабильной версии TDLight или доработать Kotlogram под актуальный API.

---

## 🎯 Как использовать текущую stub-версию

### Шаг 1: Установка переменных окружения

```bash
export TELEGRAM_API_ID=12345678
export TELEGRAM_API_HASH=your_api_hash_here
export TELEGRAM_PHONE_NUMBER=+1234567890
export TELEGRAM_CLIENT_ENABLED=true
```

API credentials получить на: https://my.telegram.org

### Шаг 2: Сборка проекта

```bash
./gradlew clean build
```

### Шаг 3: Запуск

```bash
./gradlew bootRun
```

### Шаг 4: Тестирование API

**Проверка статуса:**
```bash
curl http://localhost:8080/api/telegram-client/test/status
```

**Получить список чатов (stub данные):**
```bash
curl http://localhost:8080/api/telegram-client/test/chats
```

Ответ:
```json
{
  "success": true,
  "chats": [
    {
      "id": -1001234567890,
      "title": "Example Group (STUB)",
      "type": "Channel"
    },
    {
      "id": 123456789,
      "title": "Example User (STUB)",
      "type": "User"
    }
  ]
}
```

**Отправить тестовое сообщение (stub):**
```bash
curl -X POST http://localhost:8080/api/telegram-client/test/send \
  -H "Content-Type: application/json" \
  -d '{
    "chatId": -1001234567890,
    "message": "Тестовое сообщение!"
  }'
```

Ответ:
```json
{
  "success": true,
  "message": "Message sent successfully",
  "messageId": 1234567890123
}
```

⚠️ **Сообщение не будет реально отправлено** - это stub-версия для тестирования API.

---

## 🔧 Доработка до рабочей версии

### Файл для редактирования

`src/main/kotlin/dev/weuizx/jobzi/telegram/client/service/TelegramUserClientService.kt`

### Что нужно сделать

1. **Выбрать библиотеку:**
   - TDLight (когда стабилизируется)
   - Самописная интеграция с MTProto
   - Wrapper для Python библиотек

2. **Реализовать методы:**
   - `init()` - инициализация клиента и аутентификация
   - `sendMessage()` - реальная отправка через Telegram API
   - `getChats()` - получение списка диалогов

3. **Добавить обработку:**
   - Flood limits
   - Ошибок API
   - Переподключения

---

## 📦 Структура проекта

```
src/main/kotlin/dev/weuizx/jobzi/telegram/client/
├── config/
│   └── TelegramClientProperties.kt    # Конфигурация
├── controller/
│   └── TelegramClientTestController.kt  # REST API endpoints
├── dto/
│   └── MessageDto.kt                   # DTO
├── service/
│   └── TelegramUserClientService.kt    # STUB - требует доработки
└── README.md
```

---

## 🐍 Пример с Pyrogram (Python)

### 1. Установка

```bash
pip install pyrogram tgcrypto
```

### 2. Скрипт отправки

```python
# telegram_bot.py
import sys
from pyrogram import Client

API_ID = 12345678
API_HASH = "your_api_hash"

app = Client("my_session", api_id=API_ID, api_hash=API_HASH)

def send_message(chat_id: int, message: str):
    with app:
        app.send_message(chat_id, message)
        print(f"Message sent to {chat_id}")

if __name__ == "__main__":
    chat_id = int(sys.argv[1])
    message = sys.argv[2]
    send_message(chat_id, message)
```

### 3. Интеграция в Kotlin

```kotlin
@Service
class PythonTelegramService {
    fun sendMessage(chatId: Long, message: String) {
        val process = ProcessBuilder(
            "python3",
            "telegram_bot.py",
            chatId.toString(),
            message
        ).start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("Failed to send message")
        }
    }
}
```

---

## ❓ FAQ

**Q: Почему не используется готовая библиотека?**
A: Java/Kotlin библиотеки для Telegram Client API нестабильны и требуют сложной настройки нативных компонентов.

**Q: Можно ли использовать это в продакшене?**
A: Текущая stub-версия - нет. После доработки или интеграции с Python - да.

**Q: Как получить реальный chatId?**
A: Используйте бота @getidsbot или Telegram Desktop с включенным режимом разработчика.

**Q: Будут ли проблемы с блокировкой аккаунта?**
A: Telegram ограничивает автоматическую активность. Соблюдайте flood limits и не делайте массовые рассылки.

---

## 📝 Следующие шаги

1. Выберите подход (Python wrapper или ждать стабильную библиотеку)
2. Реализуйте выбранный подход
3. Протестируйте на небольшом количестве сообщений
4. Добавьте обработку ошибок и retry логику
5. Настройте планировщик для автоматических напоминаний

---

## 🔒 Безопасность

- ✅ `.env` и `telegram-session/` добавлены в `.gitignore`
- ✅ Не коммитьте credentials в Git
- ✅ Используйте переменные окружения
- ✅ Храните session файлы в безопасности

---

**Нужна помощь с интеграцией?** Дайте знать! 🚀
