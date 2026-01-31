# Миграция: Переход от Custom ENUM типов к VARCHAR

## Описание изменений

Убраны все кастомные PostgreSQL ENUM типы и заменены на обычные VARCHAR(50) столбцы.

### Причина изменения:
- Упрощение схемы базы данных
- Большая гибкость при изменении значений enum
- Избежание проблем с миграциями при добавлении новых значений

---

## Созданные миграции:

### 009-change-user-role-to-varchar
**Изменяет:** `users.role`
- **Было:** Custom type `user_role` ENUM ('USER', 'SUPERADMIN')
- **Стало:** VARCHAR(50)

**Файлы:**
- `sql/009-change-user-role-to-varchar.sql`
- `changes/009-change-user-role-to-varchar.xml`

### 010-change-all-enums-to-varchar
**Изменяет несколько столбцов:**

1. `business_users.role`
   - **Было:** Custom type `business_role` ENUM ('ADMIN', 'MANAGER')
   - **Стало:** VARCHAR(50)

2. `vacancies.status`
   - **Было:** Custom type `vacancy_status` ENUM ('DRAFT', 'ACTIVE', 'PAUSED', 'CLOSED')
   - **Стало:** VARCHAR(50)

3. `questions.question_type`
   - **Было:** Custom type `question_type` ENUM ('TEXT', 'YES_NO', 'CHOICE', 'PHONE', 'DATE', 'NUMBER')
   - **Стало:** VARCHAR(50)

4. `applications.status`
   - **Было:** Custom type `application_status` ENUM ('NEW', 'VIEWED', 'CONTACTED', 'ACCEPTED', 'REJECTED')
   - **Стало:** VARCHAR(50)

**Файлы:**
- `sql/010-change-all-enums-to-varchar.sql`
- `changes/010-change-all-enums-to-varchar.xml`

---

## Изменения в коде:

### Entity классы
Обновлены аннотации `@Column` для всех enum полей с добавлением `length = 50`:

1. **User.kt** - поле `role`
   ```kotlin
   @Enumerated(EnumType.STRING)
   @Column(name = "role", nullable = false, length = 50)
   var role: UserRole = UserRole.USER
   ```

2. **BusinessUser.kt** - поле `role`
   ```kotlin
   @Enumerated(EnumType.STRING)
   @Column(name = "role", nullable = false, length = 50)
   var role: BusinessRole = BusinessRole.MANAGER
   ```

### Enum классы
Enum классы в коде остались без изменений:
- `UserRole` - USER, SUPERADMIN
- `BusinessRole` - ADMIN, MANAGER
- `VacancyStatus` - DRAFT, ACTIVE, PAUSED, CLOSED (будет создан в Фазе 3)
- `QuestionType` - TEXT, YES_NO, CHOICE, PHONE, DATE, NUMBER (будет создан в Фазе 4)
- `ApplicationStatus` - NEW, VIEWED, CONTACTED, ACCEPTED, REJECTED (будет создан в Фазе 5)

---

## Как применить миграции:

### Для существующих баз данных:
Миграции будут применены автоматически при запуске приложения:
1. Liquibase выполнит миграцию 009
2. Затем выполнит миграцию 010
3. Все данные будут сохранены (USING role::text конвертирует значения)

### Для новых развертываний:
Можно упростить, убрав создание ENUM типов из миграций 003-008, но это не обязательно - миграции 009 и 010 все равно приведут схему к нужному виду.

---

## Rollback:
В каждой миграции есть rollback, который восстановит кастомные типы, если потребуется:
```sql
-- Миграция 009 rollback:
CREATE TYPE user_role AS ENUM ('USER', 'SUPERADMIN');
ALTER TABLE users ALTER COLUMN role TYPE user_role USING role::user_role;

-- Миграция 010 rollback:
CREATE TYPE business_role AS ENUM ('ADMIN', 'MANAGER');
ALTER TABLE business_users ALTER COLUMN role TYPE business_role USING role::business_role;
-- и так далее для остальных типов
```

---

## Проверка:
✅ Проект скомпилирован успешно
✅ Все enum поля имеют маппинг `@Enumerated(EnumType.STRING)`
✅ Все enum поля имеют `length = 50` в аннотации `@Column`
✅ Миграции добавлены в master changelog