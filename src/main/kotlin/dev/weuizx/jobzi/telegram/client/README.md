# Telegram User Client Module

Модуль для отправки сообщений от имени личного Telegram аккаунта.

## Структура

- **config/** - конфигурация Spring
- **controller/** - REST контроллеры для тестирования
- **dto/** - Data Transfer Objects
- **service/** - бизнес-логика работы с Telegram Client API

## Использование

См. подробную инструкцию в файле `telegram-client-instructions.md` в корне проекта.

## API Endpoints

- `GET /api/telegram-client/test/status` - проверка статуса аутентификации
- `GET /api/telegram-client/test/chats` - получить список чатов
- `POST /api/telegram-client/test/send` - отправить сообщение

## Безопасность

⚠️ **НЕ коммитьте в Git:**
- Папку `telegram-session/`
- Файл `.env`
- Значения `api_hash`

Эти файлы уже добавлены в `.gitignore`.
