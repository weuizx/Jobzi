# Jobzi - Запуск с Docker Compose

## Быстрый старт

### 1. Подготовка

Убедитесь, что у вас установлены:
- Docker Desktop или Docker Engine
- Docker Compose

### 2. Настройка переменных окружения

Создайте файл `.env` в корне проекта:

```bash
# Telegram Bot Configuration
TELEGRAM_BOT_TOKEN=ваш_токен_от_BotFather
TELEGRAM_BOT_USERNAME=имя_вашего_бота
```

Как получить токен бота:
1. Найдите @BotFather в Telegram
2. Отправьте команду `/newbot`
3. Следуйте инструкциям
4. Скопируйте токен и username

### 3. Запуск приложения

```bash
# Запуск всех сервисов (PostgreSQL + приложение)
docker-compose -f docker-compose.full.yml up -d

# Просмотр логов
docker-compose -f docker-compose.full.yml logs -f

# Просмотр логов только приложения
docker-compose -f docker-compose.full.yml logs -f jobzi-app

# Остановка
docker-compose -f docker-compose.full.yml down

# Остановка с удалением данных
docker-compose -f docker-compose.full.yml down -v
```

### 4. Проверка работоспособности

После запуска:
- Приложение доступно на: `http://localhost:8080`
- PostgreSQL доступна на: `localhost:5432`
- Бот должен ответить в Telegram

Проверка health check:
```bash
curl http://localhost:8080/actuator/health
```

## Структура сервисов

### postgres
- **Образ**: postgres:16
- **Порт**: 5432
- **База данных**: jobzi
- **Пользователь**: jobzi
- **Данные**: сохраняются в volume `postgres_data`

### jobzi-app
- **Образ**: собирается из Dockerfile
- **Порт**: 8080
- **Зависимости**: postgres (ждет healthcheck)
- **Логи**: сохраняются в volume `app_logs`

## Конфигурация

### Переменные окружения

#### Обязательные:
- `TELEGRAM_BOT_TOKEN` - токен бота от @BotFather
- `TELEGRAM_BOT_USERNAME` - username бота (без @)

#### Опциональные (имеют значения по умолчанию):
- `SPRING_DATASOURCE_URL` - URL подключения к БД
- `SPRING_DATASOURCE_USERNAME` - пользователь БД
- `SPRING_DATASOURCE_PASSWORD` - пароль БД

### Изменение конфигурации БД

Отредактируйте `docker-compose.full.yml`:

```yaml
postgres:
  environment:
    POSTGRES_DB: ваша_база
    POSTGRES_USER: ваш_пользователь
    POSTGRES_PASSWORD: ваш_пароль

jobzi-app:
  environment:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/ваша_база
    SPRING_DATASOURCE_USERNAME: ваш_пользователь
    SPRING_DATASOURCE_PASSWORD: ваш_пароль
```

## Разработка

### Только база данных

Для локальной разработки можно запустить только PostgreSQL:

```bash
docker-compose up -d
```

Затем запускайте приложение из IDE с параметрами:
```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jobzi
SPRING_DATASOURCE_USERNAME=jobzi
SPRING_DATASOURCE_PASSWORD=jobzi
```

### Пересборка приложения

После изменения кода:

```bash
# Пересобрать и перезапустить
docker-compose -f docker-compose.full.yml up -d --build

# Или по отдельности
docker-compose -f docker-compose.full.yml build jobzi-app
docker-compose -f docker-compose.full.yml up -d jobzi-app
```

## Troubleshooting

### Приложение не запускается

Проверьте логи:
```bash
docker-compose -f docker-compose.full.yml logs jobzi-app
```

Частые проблемы:
1. **База данных недоступна** - проверьте, что postgres запустился и прошел healthcheck
2. **Неверный токен бота** - проверьте переменную `TELEGRAM_BOT_TOKEN`
3. **Порты заняты** - измените порты в docker-compose.full.yml

### База данных не инициализируется

```bash
# Удалить все данные и пересоздать
docker-compose -f docker-compose.full.yml down -v
docker-compose -f docker-compose.full.yml up -d
```

### Проверка подключения к БД

```bash
# Войти в контейнер postgres
docker exec -it jobzi-postgres psql -U jobzi -d jobzi

# Проверить таблицы
\dt

# Выйти
\q
```

## Производственное окружение

Для production рекомендуется:

1. **Изменить пароли**:
   - Используйте сложные пароли
   - Храните их в secrets или vault

2. **Настроить логирование**:
   - Добавьте volume для логов
   - Настройте ротацию логов
   - Используйте централизованное логирование

3. **Мониторинг**:
   - Настройте Spring Boot Actuator endpoints
   - Добавьте Prometheus/Grafana
   - Настройте алерты

4. **Безопасность**:
   - Используйте HTTPS
   - Ограничьте доступ к портам
   - Регулярно обновляйте образы

5. **Бэкапы**:
   - Настройте регулярные бэкапы PostgreSQL
   - Храните бэкапы отдельно от основного сервера

## Полезные команды

```bash
# Посмотреть статус сервисов
docker-compose -f docker-compose.full.yml ps

# Перезапустить сервис
docker-compose -f docker-compose.full.yml restart jobzi-app

# Выполнить команду в контейнере
docker-compose -f docker-compose.full.yml exec jobzi-app sh

# Посмотреть использование ресурсов
docker stats jobzi-app jobzi-postgres

# Очистка неиспользуемых образов и контейнеров
docker system prune -a
```

## Поддержка

Если у вас возникли проблемы:
1. Проверьте логи: `docker-compose -f docker-compose.full.yml logs`
2. Убедитесь, что все переменные окружения заданы
3. Проверьте, что порты не заняты другими приложениями
4. Обновите Docker до последней версии
