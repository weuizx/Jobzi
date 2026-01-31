# Диаграммы для курса "Специальная подготовка"

## Структура файлов

### BPMN диаграммы (для Camunda Modeler):
1. `01_AS-IS_Макроуровень.bpmn` - Текущий процесс найма (ручной)
2. `02_TO-BE_Макроуровень.bpmn` - Автоматизированный процесс через Jobzi

### UML диаграммы (PlantUML):
3. `03_AS-IS_Activity_1_Публикация.puml` - Ручное создание и публикация объявления
4. `04_AS-IS_Activity_2_Обработка_откликов.puml` - Ручная обработка откликов
5. `05_TO-BE_Activity_1_Создание_вакансии.puml` - Создание вакансии через бота
6. `06_TO-BE_Activity_2_Автоматический_отклик.puml` - Автоматическая обработка откликов
7. `07_UseCase_Варианты_использования.puml` - Варианты использования системы
8. `08_Class_Модель_данных.puml` - Модель данных (14 таблиц)
9. `09_Sequence_Создание_вакансии_и_отклик.puml` - Последовательность взаимодействия
10. `10_State_Жизненный_цикл_отклика.puml` - Состояния отклика
11. `11_Traceability_Трассировка.puml` - Связь требований с компонентами

---

## Как использовать

### BPMN диаграммы (Camunda Modeler)

**1. Скачать Camunda Modeler:**
- Официальный сайт: https://camunda.com/download/modeler/
- Или любая версия с GitHub: https://github.com/camunda/camunda-modeler/releases

**2. Открыть файлы:**
```bash
# Открыть Camunda Modeler
# File → Open File → выбрать .bpmn файл
```

**3. Экспорт в PNG/SVG:**
- В Camunda Modeler: File → Export → Export as Image
- Выбрать формат: PNG или SVG
- Сохранить

**Альтернатива:**
- Импортировать BPMN XML в https://demo.bpmn.io/
- Отредактировать визуально (расположение элементов)
- Экспортировать как PNG/SVG

---

### UML диаграммы (PlantUML)

**Вариант 1: Онлайн конвертация (рекомендуется)**

1. Открыть https://www.plantuml.com/plantuml/
2. Скопировать содержимое .puml файла
3. Вставить в текстовое поле
4. Скачать PNG/SVG

**Вариант 2: VS Code плагин**

1. Установить VS Code
2. Установить расширение "PlantUML" (jebbs.plantuml)
3. Открыть .puml файл
4. Нажать Alt+D для предпросмотра
5. ПКМ на превью → Export Current Diagram → PNG/SVG

**Вариант 3: Командная строка (Java)**

```bash
# Установить PlantUML
brew install plantuml  # macOS
# или скачать plantuml.jar с https://plantuml.com/download

# Конвертировать все файлы
plantuml UML/*.puml

# Конвертировать один файл
plantuml UML/03_AS-IS_Activity_1_Публикация.puml

# Результат: создается PNG файл рядом с .puml
```

**Вариант 4: Онлайн редактор PlantText**

- https://www.planttext.com/
- Вставить код
- Скачать изображение

---

## Быстрый экспорт всех диаграмм

### BPMN → PNG (через bpmn.io):

```bash
# Открыть каждый файл в https://demo.bpmn.io/
# Нажать "Export" → "Download as PNG"
```

### PlantUML → PNG (через командную строку):

```bash
cd 9_сем_НИР_Jobzi/Диаграммы/UML

# Конвертировать все сразу (если установлен plantuml)
plantuml *.puml

# Или по одному через онлайн:
# 1. Открыть http://www.plantuml.com/plantuml/
# 2. Вставить содержимое файла
# 3. Скачать PNG
```

---

## Что делать дальше

1. **Экспортировать все диаграммы в PNG/SVG**
2. **Вставить изображения в пояснительную записку** (в разделы с плейсхолдерами)
3. **Создать единый PDF документ** со всеми диаграммами
4. **Отправить преподавателю**

---

## Структура итогового документа

```
Итоговый_документ_Специальная_подготовка.pdf
├── 1. Титульный лист
├── 2. Содержание
├── 3. Введение
├── 4. BPMN диаграммы
│   ├── 4.1. AS-IS макроуровень
│   └── 4.2. TO-BE макроуровень
├── 5. Activity диаграммы (декомпозиция)
│   ├── 5.1. AS-IS Activity 1
│   ├── 5.2. AS-IS Activity 2
│   ├── 5.3. TO-BE Activity 1
│   └── 5.4. TO-BE Activity 2
├── 6. Use Case диаграмма
├── 7. Class диаграмма
├── 8. Sequence диаграмма
├── 9. State диаграмма
├── 10. Traceability диаграмма
└── 11. Заключение
```

---

## Контакты

Преподаватель: Бушина К.С.
Email: [указать email]
Очные консультации: вторник, 18:45-20:00, К-907 или К-911

---

## Полезные ссылки

**BPMN:**
- Camunda Modeler: https://camunda.com/download/modeler/
- Online редактор: https://demo.bpmn.io/
- Спецификация BPMN 2.0: https://www.omg.org/spec/BPMN/2.0/

**PlantUML:**
- Официальный сайт: https://plantuml.com/
- Онлайн редактор: https://www.plantuml.com/plantuml/
- Документация: https://plantuml.com/guide
- PlantText: https://www.planttext.com/

**UML:**
- Спецификация UML 2.5: https://www.omg.org/spec/UML/2.5/
- Drawio (альтернатива): https://app.diagrams.net/
