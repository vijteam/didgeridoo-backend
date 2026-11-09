# Didgeridoo

Система управления задачами для персональной и командной работы.

Didgeridoo позволяет организовывать задачи в независимых пространствах, распределять их между участниками, отслеживать выполнение и анализировать результаты работы.

**Статус проекта:** в разработке.

## Технологии

- Java 25
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

### Требования

- JDK 25
- Docker с поддержкой Docker Compose

### 1. Запуск базы данных

Из корня проекта выполнить:

```bash
docker compose up -d
```

### 2. Запуск приложения

**Windows (PowerShell):**

```powershell
.\mvnw.cmd spring-boot:run
```

**Linux / macOS:**

```bash
./mvnw spring-boot:run
```

## Тестирование

Для запуска всех тестов приложения:

**Windows (PowerShell):**

```powershell
.\mvnw.cmd test
```

**Linux / macOS:**

```bash
./mvnw test
```

Интеграционные тесты используют **Testcontainers** для автоматического запуска PostgreSQL в Docker.

Для их выполнения достаточно запущенного Docker. Отдельно запускать базу данных через `docker compose up -d` не требуется.

Тестовый контейнер создаётся автоматически и удаляется после завершения тестов.

## Форматирование и проверка стиля кода

Spotless проверяет и автоматически форматирует Java-файлы.

### Проверка

**Windows (PowerShell):**

```powershell
.\mvnw.cmd spotless:check
```

**Linux / macOS:**

```bash
./mvnw spotless:check
```

### Применение форматирования

**Windows (PowerShell):**

```powershell
.\mvnw.cmd spotless:apply
```

**Linux / macOS:**

```bash
./mvnw spotless:apply
```
