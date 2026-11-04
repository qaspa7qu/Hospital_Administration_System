# Hospital Appointment and Medical Administration System

Spring Boot приложение для записи пациентов к врачам и администрирования клиники:
REST API и встроенный веб-интерфейс для администраторов, регистраторов и врачей.

## Стек

- Java 21
- Spring Boot 4.1.2
- Spring Web MVC
- Spring Data JPA / Hibernate
- Spring Security + JWT (OAuth2 Resource Server)
- MySQL 8
- JUnit (JUnit Jupiter, входит в Spring Boot test starters)
- Maven (wrapper в комплекте)
- HTML5, CSS3 и JavaScript (ES-модули, без сборщика и фреймворков)

## Запуск

Приложение требует базу данных. Есть два варианта:

### Вариант 1: MySQL (основной, production)

1. Установите MySQL 8 и запустите сервер.

2. Создайте базу данных:

```sql
CREATE DATABASE clinic_schedule CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

3. Задайте параметры подключения переменными окружения:

```text
DB_URL=jdbc:mysql://localhost:3306/clinic_schedule?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
DB_USERNAME=root
DB_PASSWORD=ваш_пароль
```

4. Запустите приложение:

```powershell
cd "C:\Users\user\Documents\Hospital Appointment and Medical Administration System"
.\mvnw.cmd spring-boot:run
```

### Вариант 2: H2 in-memory (локальная разработка без MySQL)

Если MySQL не установлен, используйте профиль `h2` с встроенной базой H2:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=h2"
```

Приложение будет доступно на `http://localhost:8080`. Откройте этот адрес в
браузере — откроется экран входа в веб-интерфейс. REST API остаётся по тем же
путям `/api/**`.

По умолчанию Hibernate использует `ddl-auto=update` (MySQL) или `create-drop` (H2)
и сам создаёт таблицы. Демонстрационные данные загружаются из `src/main/resources/data.sql`.
Для production задайте `JPA_DDL_AUTO=validate` и используйте миграции (Flyway/Liquibase).

## ER-диаграмма и модель данных

В проекте 9 связанных таблиц MySQL с внешними ключами (требование — не менее 5):

```mermaid
erDiagram
    User ||--o{ User_roles : contains
    Role ||--o{ User_roles : assigned_to
    User ||--|| Doctor : has_profile
    Specialization ||--o{ Doctor : has_doctors
    Doctor ||--o{ WorkingSchedule : has_schedules
    Doctor ||--o{ Appointment : has_appointments
    Patient ||--o{ Appointment : has_appointments
    Appointment ||--o{ AppointmentService : includes
    Service ||--o{ AppointmentService : used_in

    User {
        long id PK
        string username UK
        string password_hash
        string full_name
        string email
        string phone
        boolean enabled
        datetime created_at
        datetime updated_at
    }

    Role {
        long id PK
        string name UK
        string description
        datetime created_at
        datetime updated_at
    }

    User_roles {
        long user_id FK
        long role_id FK
    }

    Patient {
        long id PK
        string first_name
        string last_name
        string middle_name
        date birth_date
        string phone UK
        string email
        string address
        string notes
        datetime created_at
        datetime updated_at
    }

    Specialization {
        long id PK
        string name UK
        string description
        datetime created_at
        datetime updated_at
    }

    Doctor {
        long id PK
        long user_id UK FK
        long specialization_id FK
        string first_name
        string last_name
        string middle_name
        string phone
        string email
        string office_number
        string bio
        boolean active
        datetime created_at
        datetime updated_at
    }

    WorkingSchedule {
        long id PK
        long doctor_id FK
        string day_of_week
        time start_time
        time end_time
        int slot_duration_minutes
        boolean active
        datetime created_at
        datetime updated_at
    }

    Appointment {
        long id PK
        long patient_id FK
        long doctor_id FK
        date appointment_date
        time start_time
        time end_time
        string status
        string reason
        string notes
        decimal total_price
        datetime created_at
        datetime updated_at
    }

    Service {
        long id PK
        string name UK
        string description
        decimal price
        int duration_minutes
        boolean active
        datetime created_at
        datetime updated_at
    }

    AppointmentService {
        long id PK
        long appointment_id FK
        long service_id FK
        int quantity
        decimal unit_price
        datetime created_at
        datetime updated_at
    }
```

### Демонстрационные данные

Файл `src/main/resources/data.sql` содержит начальные справочные данные:
- Роли: `ADMIN`, `DOCTOR`, `RECEPTIONIST`
- Специализации: Кардиология, Неврология, Терапия, Педиатрия, Стоматология
- Услуги: консультации, ЭКГ, ЭхоКГ (включая бесплатную услугу с ценой 0.00)
- Пациенты: 5 demonstration-записей

Пользователи и врачи не вставляются через `data.sql`, потому что пароли должны быть BCrypt-хешами. Администратор создаётся автоматически при первом запуске (`admin` / `admin123`). Дополнительных пользователей и врачей можно создать через веб-интерфейс или API (`/api/admin/users`, `/api/doctors`).


## Аутентификация и роли (этап 2)

Используется stateless-аутентификация через JWT. Логин выдаёт токен, который
передаётся в заголовке `Authorization: Bearer <token>`.

При первом запуске создаётся стартовый администратор:

```text
Логин:  admin
Пароль: admin123
```

Его можно изменить переменными окружения:

```text
ADMIN_USERNAME=admin
ADMIN_PASSWORD=admin123
ADMIN_FULL_NAME=Администратор
```

**Пароль по умолчанию предназначен только для локальной разработки.**
Перед production обязательно задайте свой пароль и секрет JWT.

### Настройки JWT

```text
JWT_SECRET=Base64-строка не короче 32 байт
JWT_EXPIRATION_SECONDS=86400
JWT_ISSUER=hospital-appointment-system
```

Значение `JWT_SECRET` по умолчанию тоже подходит только для локальной разработки.

### Эндпоинты и доступ

| Метод | Эндпоинт | Доступ |
|---|---|---|
| `POST` | `/api/auth/login` | публичный |
| `GET` | `/api/auth/me` | любой аутентифицированный пользователь |
| `GET` | `/api/admin/ping` | только `ADMIN` |
| `GET` | `/api/doctor/ping` | только `DOCTOR` |
| `GET` | `/api/receptionist/ping` | только `RECEPTIONIST` |

`/api/auth/login` принимает:

```json
{
  "username": "admin",
  "password": "admin123"
}
```

и возвращает JWT:

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "username": "admin",
    "fullName": "Администратор",
    "roles": ["ADMIN"]
  }
}
```

Пример запроса к защищённому эндпоинту:

```powershell
$response = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/auth/login `
  -ContentType 'application/json' `
  -Body '{"username":"admin","password":"admin123"}'

$token = $response.accessToken

Invoke-RestMethod `
  -Uri http://localhost:8080/api/admin/ping `
  -Headers @{ Authorization = "Bearer $token" }
```

При отсутствии/невалидном токене возвращается `401 Unauthorized`,
при недостаточной роли — `403 Forbidden` в формате JSON.

Пароли хранятся в виде BCrypt-хешей. JWT действует до истечения `expiresIn`;
изменение ролей пользователя применяется после повторного входа.

## REST API (этап 3)

### Матрица доступа

| Группа эндпоинтов | `ADMIN` | `RECEPTIONIST` | `DOCTOR` |
|---|:---:|:---:|:---:|
| `/api/admin/users/**` | CRUD | — | — |
| `/api/patients/**` | CRUD | CRUD | — |
| `/api/specializations/**` — чтение | да | да | да |
| `/api/specializations/**` — изменение | да | — | — |
| `/api/doctors/**` — чтение | да | да | да |
| `/api/doctors/**` — изменение | да | — | — |
| `/api/doctors/{id}/schedule/**` — чтение | да | да | да |
| `/api/doctors/{id}/schedule/**` — изменение | да | — | — |
| `/api/appointments/**` | чтение, создание, изменение | то же | — |
| `/api/services/**` — чтение | да | да | да |
| `/api/services/**` — изменение | да | — | — |
| `/api/appointments/{id}/services/**` | чтение и изменение | то же | — |
| `/api/reports/**` | да | — | — |
| `/api/doctor/appointments` | — | — | только свои приёмы |
| `/api/doctor/profile`, `/api/doctor/schedule` | — | — | только свои данные |
| `/`, `/index.html`, `/css/**`, `/js/**`, `/assets/**`, `/favicon.svg` | публично | публично | публично |

Ошибки возвращаются в едином JSON-формате:

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Specialization already exists: Кардиология"
}
```

Коды: `400` — валидация или некорректные данные, `401` — нет/невалидный токен,
`403` — недостаточно прав, `404` — объект не найден, `409` — конфликт данных.

### Пользователи (`ADMIN`)

| Метод | Эндпоинт | Назначение |
|---|---|---|
| `GET` | `/api/admin/users` | список пользователей |
| `GET` | `/api/admin/users/{id}` | пользователь по ID |
| `POST` | `/api/admin/users` | создание |
| `PUT` | `/api/admin/users/{id}` | изменение |
| `DELETE` | `/api/admin/users/{id}` | удаление |

```json
{
  "username": "doctor.ivanov",
  "password": "Password123!",
  "fullName": "Иванов Иван Иванович",
  "email": "ivanov@example.com",
  "phone": "+7 900 000-00-00",
  "enabled": true,
  "roles": ["DOCTOR"]
}
```

При изменении поле `password` можно не передавать — старый пароль сохранится.
Нельзя удалить пользователя с профилем врача или убрать у него роль `DOCTOR`.

### Пациенты (`ADMIN`, `RECEPTIONIST`)

| Метод | Эндпоинт | Назначение |
|---|---|---|
| `GET` | `/api/patients?search=Петров` | список или поиск по фамилии |
| `GET` | `/api/patients/{id}` | карточка пациента |
| `POST` | `/api/patients` | создание |
| `PUT` | `/api/patients/{id}` | изменение |
| `DELETE` | `/api/patients/{id}` | удаление |

```json
{
  "firstName": "Пётр",
  "lastName": "Петров",
  "middleName": "Петрович",
  "birthDate": "1990-05-20",
  "phone": "+7 900 111-11-11",
  "email": "petrov@example.com",
  "address": "Москва",
  "notes": "Аллергия на пенициллин"
}
```

Удаление пациента с существующими записями к врачу возвращает `409 Conflict`.

### Специализации

Чтение — `ADMIN`, `RECEPTIONIST`, `DOCTOR`; изменение — только `ADMIN`.

| Метод | Эндпоинт |
|---|---|
| `GET` | `/api/specializations` |
| `GET` | `/api/specializations/{id}` |
| `POST` | `/api/specializations` |
| `PUT` | `/api/specializations/{id}` |
| `DELETE` | `/api/specializations/{id}` |

```json
{
  "name": "Кардиология",
  "description": "Диагностика и лечение заболеваний сердца"
}
```

Название уникально без учёта регистра. Специализацию, используемую врачами,
удалить нельзя (`409 Conflict`).

### Врачи

Чтение — `ADMIN`, `RECEPTIONIST`, `DOCTOR`; изменение — только `ADMIN`.

| Метод | Эндпоинт | Назначение |
|---|---|---|
| `GET` | `/api/doctors?specializationId=1` | список, фильтр по специализации |
| `GET` | `/api/doctors/{id}` | врач по ID |
| `POST` | `/api/doctors` | создание профиля врача |
| `PUT` | `/api/doctors/{id}` | изменение |
| `DELETE` | `/api/doctors/{id}` | удаление |

```json
{
  "userId": 2,
  "specializationId": 1,
  "firstName": "Иван",
  "lastName": "Иванов",
  "middleName": "Иванович",
  "phone": "+7 900 000-00-00",
  "email": "ivanov@example.com",
  "officeNumber": "101",
  "bio": "Врач-кардиолог",
  "active": true
}
```

Профиль создаётся только для пользователя с ролью `DOCTOR`, повторный профиль
для того же пользователя запрещён. Врача с записями к пациентам удалить нельзя;
при удалении врача его рабочие расписания удаляются автоматически.

### Рабочее расписание

| Метод | Эндпоинт | Доступ |
|---|---|---|
| `GET` | `/api/doctors/{doctorId}/schedule` | `ADMIN`, `RECEPTIONIST`, `DOCTOR` |
| `POST` | `/api/doctors/{doctorId}/schedule` | `ADMIN` |
| `PUT` | `/api/doctors/{doctorId}/schedule/{scheduleId}` | `ADMIN` |
| `DELETE` | `/api/doctors/{doctorId}/schedule/{scheduleId}` | `ADMIN` |

```json
{
  "dayOfWeek": "MONDAY",
  "startTime": "09:00",
  "endTime": "17:00",
  "slotDurationMinutes": 30,
  "active": true
}
```

Проверяется, что `startTime` раньше `endTime`, длительность слота — от 5 до 480 минут,
а комбинация «врач + день + начало» уникальна.

### Кабинет врача (`DOCTOR`)

| Метод | Эндпоинт | Назначение |
|---|---|---|
| `GET` | `/api/doctor/profile` | свой профиль врача |
| `PUT` | `/api/doctor/profile` | изменение своих контактных данных |
| `GET` | `/api/doctor/schedule` | своё рабочее расписание |

Через `/api/doctor/**` врач не может изменить специализацию, активность,
рабочее расписание или данные другого врача.

## Записи на приём (этап 4)

Управление записями доступно `ADMIN` и `RECEPTIONIST`.

| Метод | Эндпоинт | Назначение |
|---|---|---|
| `GET` | `/api/appointments` | список с фильтрами |
| `GET` | `/api/appointments/{id}` | запись по ID |
| `POST` | `/api/appointments` | создать запись |
| `PUT` | `/api/appointments/{id}` | перенести/изменить запись |
| `PATCH` | `/api/appointments/{id}/status` | изменить статус |

Фильтры списка: `patientId`, `doctorId`, `date` (`YYYY-MM-DD`), `status`.

Создание записи:

```json
{
  "patientId": 1,
  "doctorId": 2,
  "appointmentDate": "2026-11-09",
  "startTime": "10:00",
  "reason": "Консультация",
  "notes": "Первичный приём"
}
```

Длительность приёма и время окончания сервер вычисляет сам по
`slotDurationMinutes` подходящего рабочего расписания. В ответе возвращаются
`endTime`, статус `SCHEDULED`, ФИО пациента и врача, специализация:

```json
{
  "id": 10,
  "patientId": 1,
  "patientFullName": "Петров Пётр",
  "doctorId": 2,
  "doctorFullName": "Иванов Иван Иванович",
  "specializationId": 1,
  "specializationName": "Кардиология",
  "appointmentDate": "2026-11-09",
  "startTime": "10:00:00",
  "endTime": "10:30:00",
  "status": "SCHEDULED",
  "reason": "Консультация",
  "notes": "Первичный приём"
}
```

### Серверные проверки при бронировании

Запись создаётся или переносится только если:

1. пациент и врач существуют;
2. врач активен;
3. дата не в прошлом;
4. у врача есть активное рабочее расписание на этот день недели;
5. выбранное время входит в рабочие часы, а весь приём помещается до конца смены;
6. нет другой неотменённой записи этого врача, пересекающейся по времени.

Нарушение правил 1–2 даёт `404`/`409`, дата в прошлом — `400`,
отсутствие расписания, выход за рабочие часы и конфликт слота — `409`.

### Защита от двойного бронирования

Перед проверкой пересечений сервис блокирует строку врача в БД
(`SELECT ... FOR UPDATE`). Все операции бронирования одного врача выполняются
последовательно: второй параллельный запрос ждёт завершения первого, затем
видит сохранённую запись и получает `409 Conflict`. Транзакции бронирования
работают на уровне изоляции `READ_COMMITTED`, чтобы после ожидания блокировки
проверка видела последние зафиксированные данные и на MySQL.

Пересечение проверяется по интервалам, а не только по совпадению времени начала:

```text
существующая: 10:00–10:30
новая:        10:15–10:45  -> 409
новая:        10:30–11:00  -> разрешено
```

Отменённые записи (`CANCELLED`) в проверке не участвуют, поэтому слот можно
забронировать снова. Проверка и вставка выполняются в одной транзакции.

### Статусы

```text
SCHEDULED -> COMPLETED
SCHEDULED -> CANCELLED
```

`COMPLETED` и `CANCELLED` — терминальные статусы: из них нельзя перевести запись
в другой статус. Отменённую или завершённую запись нельзя перенести.
Повторная установка текущего статуса идемпотентна.

```json
{
  "status": "CANCELLED"
}
```

## Услуги, стоимость и отчёты (этап 5)

### Справочник услуг

Чтение доступно `ADMIN`, `RECEPTIONIST` и `DOCTOR`, изменение — только `ADMIN`.

| Метод | Эндпоинт | Назначение |
|---|---|---|
| `GET` | `/api/services?activeOnly=false` | список услуг; `activeOnly=true` — только активные |
| `GET` | `/api/services/{id}` | услуга по ID |
| `POST` | `/api/services` | создать услугу |
| `PUT` | `/api/services/{id}` | изменить услугу |
| `DELETE` | `/api/services/{id}` | удалить неиспользуемую услугу |

```json
{
  "name": "Консультация кардиолога",
  "description": "Первичный приём",
  "price": 1500.00,
  "durationMinutes": 30,
  "active": true
}
```

Бесплатная услуга — это услуга с `price: 0.00`, отдельного флага нет.
Название уникально без учёта регистра. Услугу, которая уже привязана к записи,
удалить нельзя (`409`); её можно деактивировать.

### Услуги в записи и итоговая стоимость

Доступно `ADMIN` и `RECEPTIONIST`.

| Метод | Эндпоинт | Назначение |
|---|---|---|
| `GET` | `/api/appointments/{appointmentId}/services` | состав услуг и итог |
| `POST` | `/api/appointments/{appointmentId}/services` | добавить услугу |
| `PUT` | `/api/appointments/{appointmentId}/services/{itemId}` | изменить количество |
| `DELETE` | `/api/appointments/{appointmentId}/services/{itemId}` | убрать услугу |

```json
{
  "serviceId": 1,
  "quantity": 2
}
```

Ответ:

```json
{
  "appointmentId": 10,
  "services": [
    {
      "id": 5,
      "serviceId": 1,
      "serviceName": "Консультация кардиолога",
      "quantity": 2,
      "unitPrice": 1500.00,
      "lineTotal": 3000.00
    }
  ],
  "totalPrice": 3000.00
}
```

Финансовые правила:

- `unitPrice` фиксируется в момент привязки услуги; последующее изменение
  цены в справочнике не меняет стоимость уже созданной записи;
- `lineTotal = unitPrice × quantity`, `totalPrice` — сумма всех строк;
- итог нормализуется до двух знаков с округлением `HALF_UP`;
- количество — от 1 до 100;
- повторная привязка той же услуги к записи возвращает `409`;
- неактивную услугу привязать нельзя (`409`);
- состав услуг можно менять только у записи в статусе `SCHEDULED`;
  для `COMPLETED` и `CANCELLED` чтение доступно, изменение — `409`;
- `totalPrice` также возвращается в `GET /api/appointments` и
  `GET /api/appointments/{id}`.

### Отчёты

Доступны только `ADMIN`. Период задаётся включительно параметрами
`from` и `to` (`YYYY-MM-DD`).

| Метод | Эндпоинт | Назначение |
|---|---|---|
| `GET` | `/api/reports/appointments?from=...&to=...` | статистика приёмов за период |
| `GET` | `/api/reports/doctors?from=...&to=...&doctorId=...` | статистика по врачам |

Статистика за период:

```json
{
  "from": "2026-11-01",
  "to": "2026-11-30",
  "totalAppointments": 24,
  "scheduledAppointments": 10,
  "completedAppointments": 12,
  "cancelledAppointments": 2,
  "completedRevenue": 45000.00
}
```

Статистика по врачам:

```json
{
  "from": "2026-11-01",
  "to": "2026-11-30",
  "doctors": [
    {
      "doctorId": 2,
      "doctorFullName": "Иванов Иван Иванович",
      "specializationName": "Кардиология",
      "totalAppointments": 12,
      "scheduledAppointments": 4,
      "completedAppointments": 7,
      "cancelledAppointments": 1,
      "completedRevenue": 28000.00
    }
  ]
}
```

Семантика:

- учитываются все статусы записей, попавших в период;
- `completedRevenue` включает только завершённые (`COMPLETED`) приёмы;
- отчёт по врачам содержит всех врачей, включая тех, у кого нет приёмов
  за период (нулевые строки);
- необязательный `doctorId` ограничивает отчёт одним врачом;
- если `from > to` или дата не передана, возвращается `400`.

## Веб-интерфейс (этап 6)

Интерфейс — одностраничное приложение на чистом HTML, CSS и JavaScript
(ES-модули), без сборщика и фреймворков. Spring Boot отдаёт его из
`src/main/resources/static`; маршрутизация хешевая (`#/dashboard`,
`#/calendar`, `#/appointments` и т. д.), поэтому сервер не требует
дополнительной настройки fallback-маршрутов.

После входа JWT и данные пользователя хранятся в `sessionStorage`, а клиент
добавляет заголовок `Authorization: Bearer <token>` ко всем запросам к API.
При истечении сессии или ответе `401` приложение возвращает экран входа.
Доступ к разделам проверяется и в интерфейсе, и на сервере.

### Вход и панели по ролям

Откройте `http://localhost:8080/` и войдите под нужной учётной записью.
Стартовый администратор — `admin` / `admin123` (только для локальной
разработки).

| Раздел | `ADMIN` | `RECEPTIONIST` | `DOCTOR` |
|---|:---:|:---:|:---:|
| Обзор | да | да | да |
| Календарь приёмов | да | да | — |
| Записи | да | да | «Мои приёмы» |
| Пациенты | да | да | — |
| Врачи и рабочее расписание | да | да | — |
| Специализации | да | — | — |
| Медицинские услуги | да | да | да (просмотр) |
| Отчёты | да | — | — |
| Сотрудники и роли | да | — | — |
| Моё расписание, мой профиль | — | — | да |

- **Администратор** управляет справочниками, сотрудниками, расписаниями,
  записями, расчётом стоимости и отчётами.
- **Регистратор** ведёт пациентов, записи и расчёт стоимости, пользуется
  календарём, но не видит отчёты, сотрудников и не изменяет справочники.
- **Врач** видит только свои приёмы, своё расписание и контактный профиль;
  редактирование специализации, активности и расписания ему недоступно.

Интерфейс использует спокойную медицинскую палитру (бирюзовый, синий, белые
поверхности, мягкие мятные и голубые фоны), карточки со скруглениями и
адаптивную вёрстку с мобильным меню.

### Календарь и свободные слоты

Календарь доступен администратору и регистратору:

1. В месячной сетке отображаются дни с количеством приёмов и цветовыми
   индикаторами статусов (`SCHEDULED`, `COMPLETED`, `CANCELLED`).
2. При выборе дня и врача панель справа строит свободные слоты.
3. Слоты рассчитываются на клиенте по активному рабочему расписанию врача на
   выбранный день недели: смена делится на интервалы по
   `slotDurationMinutes`.
4. Из свободных исключаются интервалы, пересекающиеся с любыми
   неотменёнными записями этого врача; для сегодняшнего дня прошедшее время
   также недоступно.
5. Нажатие на свободный слот открывает форму записи с заполненными врачом,
   датой и временем. После сохранения календарь и список приёмов обновляются.
6. При выборе «Все врачи» слоты не показываются, пока не выбран конкретный
   врач.

Клиентский расчёт лишь помогает выбрать время. Сервер повторно проверяет
расписание, рабочие часы и пересечения при создании или переносе записи,
поэтому конфликтный слот всё равно будет отклонён с `409`.

### Кабинет врача

Для панели врача добавлен эндпоинт собственных приёмов:

| Метод | Эндпоинт | Доступ |
|---|---|---|
| `GET` | `/api/doctor/appointments` | только `DOCTOR` |

Без параметров возвращаются все приёмы текущего врача в порядке убывания даты
и времени. Если переданы оба параметра `from` и `to` (`YYYY-MM-DD`), возвращается
включающий диапазон в порядке возрастания. Один параметр без второго или
`from > to` даёт `400`; отсутствие профиля врача — `404`. Отменённые записи
в выборку входят.

### Файлы интерфейса

```text
src/main/resources/static/
├── index.html              экран входа и оболочка приложения
├── favicon.svg
├── css/styles.css          тема, календарь, таблицы, модальные окна, адаптивность
└── js/
    ├── api.js              JWT-сессия и обёртка над REST API
    ├── ui.js               иконки, форматирование, модальные окна, уведомления
    ├── appointment-shared.js  создание, просмотр, статусы и стоимость записи
    ├── app.js              роутер, вход/выход, меню по ролям
    └── views/              обзор, календарь, записи, пациенты, врачи,
                            специализации, услуги, отчёты, сотрудники,
                            расписание и профиль врача
```

## Тесты

Тесты используют in-memory H2 в режиме совместимости с MySQL, поэтому локальный MySQL для них не нужен:

```powershell
.\mvnw.cmd test
```

Проверяются:

- сохранение связей JPA и расчёт стоимости услуг;
- вход и выдача JWT;
- ошибка при неверном пароле;
- отказ без токена и с невалидным токеном;
- доступ `ADMIN`, `DOCTOR` и `RECEPTIONIST` только к своим эндпоинтам;
- получение профиля текущего пользователя;
- CRUD пользователей, пациентов, специализаций, врачей и расписаний;
- проверки уникальности, валидация времени и конфликты удаления;
- сквозной сценарий: администратор создаёт специализацию, пользователя-врача,
  профиль врача, пациента и расписание; врач читает свой профиль и расписание,
  но не получает доступ к пациентам;
- бронирование с проверкой рабочего расписания, активной смены и пересечений;
- смежные и частично пересекающиеся интервалы;
- переходы статусов и повторное бронирование после отмены;
- перенос записи без ложного конфликта с самой собой;
- параллельные запросы на один слот: ровно один успешен, второй получает `409`;
- параллельные запросы на разные слоты оба успешны;
- CRUD справочника услуг, бесплатные услуги и запрет удаления используемой услуги;
- привязка услуг к записи, изменение количества, пересчёт итога и снапшот цены;
- запрет изменения услуг завершённой и отменённой записи;
- агрегация отчётов за период и по врачам, включая врачей без приёмов;
- разграничение доступа к справочнику, биллингу и отчётам;
- публичная отдача SPA: `index.html`, CSS, JS и favicon без токена;
- врачебный эндпоинт `/api/doctor/appointments`: только свои приёмы,
  сортировка, включающий диапазон дат и сохранение всех статусов;
- валидация дат врачебного эндпоинта, отказ другим ролям и `404` без профиля.

Всего 104 теста: 14 unit-тестов `AppointmentService`, 10 тестов справочника
услуг, 13 unit-тестов биллинга, 7 unit-тестов отчётов, 9 API-тестов записей,
11 тестов безопасности, 3 сквозных API-теста этапа 5, 3 многопоточных
интеграционных теста и 4 интеграционных теста этапа 6 (публичная статика и
приёмы врача).

## Модель данных (этап 1)

| Сущность | Назначение |
|---|---|
| `User` | Учётная запись сотрудника (логин, хеш пароля, статус) |
| `Role` | Роль: `ADMIN`, `DOCTOR`, `RECEPTIONIST` |
| `Patient` | Карточка пациента: личные данные и контакты |
| `Specialization` | Справочник специализаций врачей |
| `Doctor` | Врач; связан с `User` и `Specialization` |
| `WorkingSchedule` | Рабочие дни и часы приёма врача |
| `Appointment` | Запись пациента к врачу |
| `Service` | Справочник услуг с ценой и длительностью |
| `AppointmentService` | Оказанные в рамках записи услуги и стоимость |

Связи:

- `User` ↔ `Role` — many-to-many (`user_roles`);
- `Doctor` → `User` — one-to-one;
- `Doctor` → `Specialization` — many-to-one;
- `Doctor` → `WorkingSchedule` — one-to-many;
- `Patient` → `Appointment` ← `Doctor` — запись связывает пациента и врача;
- `Appointment` → `AppointmentService` ← `Service` — состав услуг записи.

Статусы записи: `SCHEDULED`, `COMPLETED`, `CANCELLED`.
Цена услуги в записи фиксируется в `unit_price`, чтобы изменение прайса
не меняло стоимость уже созданных записей.

## Структура пакетов

```text
com.smart.schedule
├── config      JwtConfig, SecurityConfig, StartupDataInitializer
├── controller  AuthController, AccessController, AdminUserController,
│               PatientController, DoctorController, DoctorSelfController,
│               SpecializationController, WorkingScheduleController,
│               AppointmentController, MedicalServiceController,
│               AppointmentServicesController, ReportController
├── dto         auth, error, user, patient, doctor, specialization, schedule,
│               appointment, service, report
├── entity      JPA-сущности
│   └── enums   RoleName, AppointmentStatus
├── exception   GlobalExceptionHandler, ResourceNotFoundException,
│               ConflictException, BadRequestException
├── repository  Spring Data JPA репозитории
├── security    AppUserDetailsService, JwtService
└── service     AuthService, UserService, PatientService, DoctorService,
                SpecializationService, WorkingScheduleService,
                AppointmentService, MedicalServiceService,
                AppointmentBillingService, ReportService
```

Веб-интерфейс находится в `src/main/resources/static` и описан в разделе
«Веб-интерфейс (этап 6)».

## Что будет дальше

Возможные следующие шаги: уведомления пациентам, экспорт отчётов, журнал
аудита действий, миграции Flyway/Liquibase и production-конфигурация
развёртывания.
