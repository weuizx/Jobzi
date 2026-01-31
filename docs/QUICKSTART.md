# Руководство по запуску Jobzi

## Быстрый старт

### 1. Запуск базы данных

```bash
# Запустить PostgreSQL в Docker
docker-compose up -d

# Проверить, что база данных запустилась
docker ps | grep jobzi-postgres

# Проверить готовность базы
docker exec jobzi-postgres pg_isready -U jobzi -d jobzi
```

База данных будет доступна на `localhost:5432`:
- **Database**: `jobzi`
- **User**: `jobzi`
- **Password**: `jobzi`

### 2. Запуск приложения

```bash
# Собрать проект
./gradlew build

# Запустить приложение
./gradlew bootRun
```

При первом запуске Liquibase автоматически:
- Применит все миграции
- Создаст таблицы в базе данных
- Настроит индексы и ограничения

### 3. Проверка миграций

```bash
# Подключиться к базе данных
docker exec -it jobzi-postgres psql -U jobzi -d jobzi

# Внутри psql:
\dt                    # Показать все таблицы
\d+ businesses         # Показать структуру таблицы businesses
\d+ vacancies          # Показать структуру таблицы vacancies

# Проверить историю миграций
SELECT * FROM databasechangelog ORDER BY dateexecuted DESC;

# Выйти
\q
```

### 4. Остановка

```bash
# Остановить приложение
# Ctrl+C в терминале, где запущен bootRun

# Остановить базу данных
docker-compose down

# Остановить базу данных и удалить данные
docker-compose down -v
```

## Полезные команды

### Docker

```bash
# Просмотр логов PostgreSQL
docker logs jobzi-postgres

# Просмотр логов в реальном времени
docker logs -f jobzi-postgres

# Перезапуск базы данных
docker-compose restart

# Статус контейнеров
docker-compose ps
```

### Gradle

```bash
# Очистка build-директории
./gradlew clean

# Сборка без тестов
./gradlew build -x test

# Запуск тестов
./gradlew test

# Просмотр зависимостей
./gradlew dependencies
```

### База данных

```bash
# Создать резервную копию
docker exec jobzi-postgres pg_dump -U jobzi jobzi > backup.sql

# Восстановить из резервной копии
docker exec -i jobzi-postgres psql -U jobzi -d jobzi < backup.sql

# Выполнить SQL-скрипт
docker exec -i jobzi-postgres psql -U jobzi -d jobzi < script.sql
```

### Liquibase

```bash
# Откатить последнюю миграцию
./gradlew liquibaseRollbackCount -PliquibaseCommandValue=1

# Откатить на определенную дату
./gradlew liquibaseRollbackDate -PliquibaseCommandValue=2024-01-01

# Статус миграций
./gradlew liquibaseStatus

# Валидация changelogs
./gradlew liquibaseValidate
```

## Структура проекта

```
jobzi/
├── docker-compose.yml              # Конфигурация PostgreSQL
├── build.gradle.kts                # Зависимости и настройки Gradle
├── settings.gradle.kts             # Настройки проекта
├── README.md                       # Основная документация
├── docs/
│   ├── database-schema.md          # Подробная схема БД
│   └── QUICKSTART.md               # Это руководство
└── src/
    ├── main/
    │   ├── kotlin/
    │   │   └── dev/weuizx/jobzi/
    │   │       └── JobziApplication.kt
    │   └── resources/
    │       ├── application.yaml    # Конфигурация приложения
    │       └── db/
    │           └── changelog/
    │               ├── db.changelog-master.xml
    │               └── changes/
    │                   ├── 001-create-businesses-table.sql
    │                   ├── 002-create-users-table.sql
    │                   ├── 003-create-business-users-table.sql
    │                   ├── 004-create-vacancies-table.sql
    │                   ├── 005-create-questions-table.sql
    │                   ├── 006-create-applications-table.sql
    │                   └── 007-create-answers-table.sql
    └── test/
```

## Следующие шаги

После успешного запуска базы данных и применения миграций:

1. **Интеграция с Telegram Bot API**
   - Добавить зависимость для Telegram Bot
   - Настроить bot token
   - Создать базовые команды

2. **Создание entity-классов**
   - Создать Kotlin data classes для таблиц
   - Настроить JPA/Hibernate аннотации
   - Создать репозитории

3. **Реализация бизнес-логики**
   - Service-слой для управления бизнесами
   - Service-слой для работы с вакансиями
   - Генератор кодов вакансий

4. **Telegram Bot handlers**
   - Регистрация бизнесов
   - Создание вакансий
   - Обработка откликов

## Troubleshooting

### База данных не запускается

```bash
# Проверить, что порт 5432 не занят
lsof -i :5432

# Остановить другие инстансы PostgreSQL
brew services stop postgresql  # macOS

# Удалить старый volume и создать заново
docker-compose down -v
docker-compose up -d
```

### Ошибки миграций

```bash
# Посмотреть логи приложения
./gradlew bootRun --info

# Проверить, что база данных доступна
docker exec jobzi-postgres pg_isready -U jobzi -d jobzi

# Вручную откатить все миграции
docker exec -it jobzi-postgres psql -U jobzi -d jobzi -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
```

### Проблемы с Gradle

```bash
# Очистить кэш Gradle
./gradlew clean --no-daemon

# Удалить .gradle директорию
rm -rf .gradle/

# Обновить Gradle wrapper
./gradlew wrapper --gradle-version=8.5
```

## Конфигурация

### Изменение настроек базы данных

Отредактируйте `docker-compose.yml`:

```yaml
environment:
  POSTGRES_DB: jobzi
  POSTGRES_USER: jobzi
  POSTGRES_PASSWORD: your_password  # Измените пароль
```

Затем обновите `src/main/resources/application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/jobzi
    username: jobzi
    password: your_password  # Тот же пароль
```

### Изменение порта базы данных

В `docker-compose.yml`:

```yaml
ports:
  - "5433:5432"  # Использовать порт 5433 вместо 5432
```

В `application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/jobzi
```

## Полезные ссылки

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Liquibase Documentation](https://docs.liquibase.com/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)