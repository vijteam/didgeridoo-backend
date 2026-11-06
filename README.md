# Didgeridoo

Система управления задачами для персональной и командной работы.

Didgeridoo позволяет организовывать задачи в независимых пространствах, распределять их между участниками, отслеживать выполнение и анализировать результаты работы.

**Статус проекта:** в разработке.

## Технологии

- Java 17
- Spring Boot
- Spring Web MVC
- Spring Data JPA / Hibernate
- Jakarta Bean Validation
- PostgreSQL
- Flyway
- OpenAPI 3.1
- Maven
- Docker Compose
- Testcontainers

## Локальный запуск

### Подготовка

Нужен Docker с поддержкой Docker Compose. Для запуска бэка вне контейнера также нужен JDK 17.

Из корня репозитория один раз создать `.env` из `.env.example` и задать локальный пароль БД. Если `.env` уже существует, сохранить его параметры:

```bash
cp .env.example .env
```

`.env` не коммитится и не попадает в Docker-образ. Параметры существующей базы не меняются автоматически при редактировании `.env`.

### Вариант A: бэк и PostgreSQL в Docker

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f backend
```

Compose собирает бэк на Java 17 и запускает его после готовности PostgreSQL. HTTP доступен на `http://localhost:8080`; порт можно изменить через `APP_PORT` в `.env`. Внутри Docker бэк подключается к `postgres:5432`, независимо от внешнего `DB_PORT`.

В текущем приложении нет страницы `/`, поэтому ответ `404` на неё ожидаем. Успешный старт подтверждается строкой `Started DidgeridooApplication` в логах.

После изменения Java-кода образ нужно пересобрать:

```bash
docker compose up --build -d backend
```

Dockerfile сначала собирает JAR через Maven Wrapper на JDK 17, затем копирует его в образ с JRE 17. При сборке образа проверяется форматирование, а тесты с Testcontainers выполняются отдельно на хосте. Docker-сборка не заменяет полный `bash ./mvnw clean verify` перед PR.

### Вариант B: PostgreSQL в Docker, бэк в IDEA/Maven

Если контейнер бэка уже запущен, сначала остановить его, чтобы освободить 8080:

```bash
docker compose stop backend
docker compose up -d postgres
```

В IDEA выбрать JDK 17 для Project SDK и запуска, а рабочей папкой — корень этого репозитория. Запускать `DidgeridooApplication` через Run/Debug. Для Maven в терминале:

**Windows (PowerShell):**

```powershell
.\mvnw.cmd spring-boot:run
```

**Linux / macOS:**

```bash
bash ./mvnw spring-boot:run
```

### Остановка

```bash
docker compose down
```

Данные PostgreSQL сохраняются в volume. Флаг `-v` удаляет данные и для обычной остановки не нужен.

## Тестирование

Для запуска всех тестов приложения:

**Windows (PowerShell):**

```powershell
.\mvnw.cmd test
```

**Linux / macOS:**

```bash
bash ./mvnw test
```

Интеграционные тесты используют **Testcontainers** для автоматического запуска PostgreSQL в Docker.

Для их выполнения достаточно запущенного Docker. Отдельно запускать базу данных через `docker compose up -d postgres` не требуется.

Тестовый контейнер создаётся автоматически и удаляется после завершения тестов.

### Хранение задач

Миграция V3 создаёт только таблицы модуля tasks: `task_statuses`, `tasks` и `task_changes`. JPA-сущности и репозитории задач/статусов находятся в `tasks.internal`. Flyway обновляет схему, Hibernate проверяет её через `ddl-auto: validate`.

Модуль spaces пока не предоставлен. Поэтому `space_id` хранится как UUID без внешнего ключа к пространствам; таблицы `spaces` и `space_memberships` эта реализация не создаёт. Составной ключ задачи и статуса уже запрещает использовать статус с другим `space_id`. Внешние ключи к существующим пользователям и согласованность исполнителя с источником назначения также проверяются БД.

При интеграции с Владом отдельная миграция должна добавить внешние ключи к пространствам/участникам после проверки существующих данных. До этого БД не подтверждает существование пространства, членство или роль исполнителя. Это ещё не готовый авторизованный HTTP API; соответствующие проверки будут выполняться сервисами через публичный контракт spaces.

## Форматирование и проверка стиля кода

Spotless проверяет и автоматически форматирует Java-файлы.

### Проверка

**Windows (PowerShell):**

```powershell
.\mvnw.cmd spotless:check
```

**Linux / macOS:**

```bash
bash ./mvnw spotless:check
```

### Применение форматирования

**Windows (PowerShell):**

```powershell
.\mvnw.cmd spotless:apply
```

**Linux / macOS:**

```bash
bash ./mvnw spotless:apply
```
