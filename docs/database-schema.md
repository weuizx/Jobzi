# Схема базы данных Jobzi

## Диаграмма связей

```
┌──────────────────┐
│    businesses    │
│                  │
│ id (PK)          │
│ name             │
│ telegram_chat_id │◄─────┐
│ description      │      │
│ is_active        │      │
└────────┬─────────┘      │
         │                │
         │                │
         │           ┌────┴─────────────┐
         │           │  business_users  │
         │           │                  │
         │           │ id (PK)          │
         │           │ business_id (FK) │
         ├───────────┤ user_id (FK)     │
         │           │ role             │◄────┐
         │           └──────────────────┘     │
         │                                    │
         │           ┌────────────────┐       │
         │           │     users      │       │
         │           │                │       │
         │           │ id (PK)        │───────┘
         │           │ telegram_id    │
         │           │ first_name     │
         │           │ last_name      │
         │           │ username       │
         │           │ phone_number   │
         │           └────────┬───────┘
         │                    │
┌────────▼──────────┐         │
│    vacancies      │         │
│                   │         │
│ id (PK)           │         │
│ business_id (FK)  │         │
│ code (UNIQUE)     │         │
│ title             │         │
│ description       │         │
│ location          │         │
│ salary            │         │
│ status            │         │
└─────┬──────┬──────┘         │
      │      │                │
      │      │                │
      │   ┌──▼────────────┐   │
      │   │   questions   │   │
      │   │               │   │
      │   │ id (PK)       │   │
      │   │ vacancy_id    │   │
      │   │ question_text │   │
      │   │ question_type │   │
      │   │ is_required   │   │
      │   │ order_index   │   │
      │   │ options       │   │
      │   └───────┬───────┘   │
      │           │           │
  ┌───▼───────────┤           │
  │ applications  │           │
  │               │           │
  │ id (PK)       │           │
  │ vacancy_id    │           │
  │ user_id (FK)  ├───────────┘
  │ status        │
  │ notes         │
  └───────┬───────┘
          │
     ┌────▼─────────┐
     │   answers    │
     │              │
     │ id (PK)      │
     │ application  │
     │ question_id  │
     │ answer_text  │
     └──────────────┘
```

## Описание таблиц

### businesses
Хранит информацию о бизнесах (работодателях)

**Поля:**
- `id` - первичный ключ
- `name` - название компании/бизнеса
- `telegram_chat_id` - ID чата Telegram владельца (уникальный)
- `description` - описание бизнеса
- `is_active` - активен ли бизнес
- `created_at`, `updated_at` - временные метки

**Индексы:**
- `idx_businesses_telegram_chat_id`
- `idx_businesses_is_active`

---

### users
Все пользователи системы (админы и кандидаты)

**Поля:**
- `id` - первичный ключ
- `telegram_id` - уникальный ID пользователя в Telegram
- `first_name`, `last_name` - имя и фамилия
- `username` - username в Telegram
- `phone_number` - номер телефона
- `is_active` - активен ли пользователь
- `created_at`, `updated_at` - временные метки

**Индексы:**
- `idx_users_telegram_id`
- `idx_users_phone_number`
- `idx_users_username`

---

### business_users
Связь пользователей с бизнесами + роли

**Поля:**
- `id` - первичный ключ
- `business_id` - внешний ключ на businesses
- `user_id` - внешний ключ на users
- `role` - роль (ADMIN/MANAGER)
- `created_at` - временная метка

**Ограничения:**
- Уникальная пара (business_id, user_id)

**Индексы:**
- `idx_business_users_business_id`
- `idx_business_users_user_id`
- `idx_business_users_role`

**Enum: business_role**
- `ADMIN` - полный доступ к бизнесу
- `MANAGER` - ограниченный доступ

---

### vacancies
Вакансии работодателей

**Поля:**
- `id` - первичный ключ
- `business_id` - внешний ключ на businesses
- `code` - уникальный код вакансии (например, "ABC123")
- `title` - название вакансии
- `description` - описание работы
- `location` - место работы
- `salary` - зарплата (текст)
- `status` - статус вакансии
- `created_at`, `updated_at` - временные метки
- `published_at` - дата публикации

**Индексы:**
- `idx_vacancies_business_id`
- `idx_vacancies_code`
- `idx_vacancies_status`
- `idx_vacancies_published_at`

**Enum: vacancy_status**
- `DRAFT` - черновик (не опубликована)
- `ACTIVE` - активна (принимаются отклики)
- `PAUSED` - на паузе
- `CLOSED` - закрыта

---

### questions
Вопросы для анкет вакансий

**Поля:**
- `id` - первичный ключ
- `vacancy_id` - внешний ключ на vacancies
- `question_text` - текст вопроса
- `question_type` - тип вопроса
- `is_required` - обязательный ли вопрос
- `order_index` - порядок отображения
- `options` - JSON с вариантами ответа (для CHOICE)
- `created_at` - временная метка

**Индексы:**
- `idx_questions_vacancy_id`
- `idx_questions_order_index`

**Enum: question_type**
- `TEXT` - текстовое поле
- `YES_NO` - да/нет
- `CHOICE` - выбор из вариантов
- `PHONE` - номер телефона
- `DATE` - дата
- `NUMBER` - число

---

### applications
Отклики кандидатов на вакансии

**Поля:**
- `id` - первичный ключ
- `vacancy_id` - внешний ключ на vacancies
- `user_id` - внешний ключ на users (кандидат)
- `status` - статус отклика
- `notes` - заметки работодателя о кандидате
- `created_at`, `updated_at` - временные метки

**Ограничения:**
- Уникальная пара (vacancy_id, user_id) - один отклик от кандидата на вакансию

**Индексы:**
- `idx_applications_vacancy_id`
- `idx_applications_user_id`
- `idx_applications_status`
- `idx_applications_created_at`

**Enum: application_status**
- `NEW` - новый отклик
- `VIEWED` - просмотрен
- `CONTACTED` - связались с кандидатом
- `ACCEPTED` - принят
- `REJECTED` - отклонен

---

### answers
Ответы кандидатов на вопросы анкет

**Поля:**
- `id` - первичный ключ
- `application_id` - внешний ключ на applications
- `question_id` - внешний ключ на questions
- `answer_text` - текст ответа
- `created_at` - временная метка

**Ограничения:**
- Уникальная пара (application_id, question_id) - один ответ на вопрос

**Индексы:**
- `idx_answers_application_id`
- `idx_answers_question_id`

---

## Система кодов вакансий

Каждая вакансия получает уникальный короткий код при создании:
- Формат: буквенно-цифровой (например, "ABC123", "DEF456")
- Длина: 6-10 символов
- Уникальность: database constraint на поле `vacancies.code`
- Использование: кандидаты указывают код при отклике

### Генерация кода

Код генерируется автоматически при создании вакансии:
1. Генерируется случайная строка
2. Проверяется уникальность в базе
3. При коллизии генерируется новый код
4. Сохраняется в таблице vacancies

### Примеры кодов
- `AB12CD` - короткий вариант
- `XYZ789` - числовой суффикс
- `WORK42` - осмысленный префикс

---

## Мультитенантность

Система поддерживает множество независимых бизнесов:

### Изоляция данных
- Каждая вакансия привязана к business_id
- Отклики видны только владельцам бизнеса
- Вопросы анкеты принадлежат конкретной вакансии

### Роли пользователей
- **ADMIN**: полный доступ к бизнесу
  - Создание/удаление вакансий
  - Управление пользователями
  - Экспорт данных

- **MANAGER**: ограниченный доступ
  - Просмотр вакансий и откликов
  - Изменение статусов откликов
  - Нет доступа к настройкам бизнеса

### Связь пользователей с бизнесами
- Один пользователь может быть админом в нескольких бизнесах
- Один пользователь может быть кандидатом (откликаться на вакансии)
- Роли определяются через таблицу business_users

---

## Примеры запросов

### Получить все вакансии бизнеса

```sql
SELECT v.*
FROM vacancies v
WHERE v.business_id = :business_id
  AND v.status = 'ACTIVE'
ORDER BY v.published_at DESC;
```

### Получить отклики на вакансию с ответами

```sql
SELECT
  a.id,
  u.first_name,
  u.last_name,
  u.phone_number,
  a.status,
  a.created_at
FROM applications a
JOIN users u ON u.id = a.user_id
WHERE a.vacancy_id = :vacancy_id
ORDER BY a.created_at DESC;
```

### Получить ответы кандидата

```sql
SELECT
  q.question_text,
  q.question_type,
  ans.answer_text
FROM answers ans
JOIN questions q ON q.id = ans.question_id
WHERE ans.application_id = :application_id
ORDER BY q.order_index;
```

### Найти вакансию по коду

```sql
SELECT v.*
FROM vacancies v
WHERE v.code = UPPER(:code)
  AND v.status = 'ACTIVE';
```

### Проверить права админа

```sql
SELECT bu.role
FROM business_users bu
WHERE bu.business_id = :business_id
  AND bu.user_id = :user_id;
```
