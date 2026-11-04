-- ============================================================
-- Demonstration seed data for MySQL (MariaDB compatible)
-- These reference tables are populated on application startup.
-- Users and doctors are created via StartupDataInitializer and the API.
-- ============================================================

-- Roles
INSERT IGNORE INTO roles (id, name, description, created_at, updated_at) VALUES
    (1, 'ADMIN', 'Администратор клиники', NOW(), NOW()),
    (2, 'DOCTOR', 'Врач', NOW(), NOW()),
    (3, 'RECEPTIONIST', 'Сотрудник регистратуры', NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- Specializations
INSERT IGNORE INTO specializations (id, name, description, created_at, updated_at) VALUES
    (1, 'Кардиология', 'Диагностика и лечение заболеваний сердца и сосудов', NOW(), NOW()),
    (2, 'Неврология', 'Заболевания нервной системы', NOW(), NOW()),
    (3, 'Терапия', 'Общая терапия и профилактика заболеваний', NOW(), NOW()),
    (4, 'Педиатрия', 'Детская медицина', NOW(), NOW()),
    (5, 'Стоматология', 'Лечение зубов и полости рта', NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- Services
INSERT IGNORE INTO services (id, name, description, price, duration_minutes, active, created_at, updated_at) VALUES
    (1, 'Консультация терапевта', 'Первичный приём врача-терапевта', 1500.00, 30, true, NOW(), NOW()),
    (2, 'Консультация кардиолога', 'Приём врача-кардиолога с ЭКГ', 2500.00, 30, true, NOW(), NOW()),
    (3, 'Консультация невролога', 'Приём врача-невролога', 2000.00, 30, true, NOW(), NOW()),
    (4, 'Консультация педиатра', 'Приём детского врача', 1800.00, 30, true, NOW(), NOW()),
    (5, 'ЭхоКГ', 'Эхокардиография сердца', 3500.00, 45, true, NOW(), NOW()),
    (6, 'ЭКГ', 'Электрокардиография', 1200.00, 15, true, NOW(), NOW()),
    (7, 'Консультация стоматолога', 'Первичный осмотр стоматолога', 2000.00, 30, true, NOW(), NOW()),
    (8, 'Консультация (бесплатная)', 'Бесплатная первичная консультация', 0.00, 30, true, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), price = VALUES(price);

-- Patients
INSERT IGNORE INTO patients (id, first_name, last_name, middle_name, birth_date, phone, email, address, notes, created_at, updated_at) VALUES
    (1, 'Пётр', 'Петров', 'Петрович', '1985-03-15', '+7 900 111-22-33', 'petrov@example.com', 'Москва, ул. Лесная, 5', 'Аллергия на пенициллин', NOW(), NOW()),
    (2, 'Анна', 'Иванова', 'Сергеевна', '1990-07-22', '+7 900 222-33-44', 'ivanova@example.com', 'Москва, ул. Мира, 10', NULL, NOW(), NOW()),
    (3, 'Михаил', 'Сидоров', 'Алексеевич', '1972-11-08', '+7 900 333-44-55', 'sidorov@example.com', 'Москва, ул. Пушкина, 1', 'Хроническая гипертония', NOW(), NOW()),
    (4, 'Елена', 'Кузнецова', 'Владимировна', '1988-01-30', '+7 900 444-55-66', 'kuznetsova@example.com', 'Москва, ул. Гагарина, 20', NULL, NOW(), NOW()),
    (5, 'Дмитрий', 'Васильев', 'Игоревич', '1995-09-12', '+7 900 555-66-77', 'vasilyev@example.com', 'Москва, ул. Ленинский пр., 15', NULL, NOW(), NOW())
ON DUPLICATE KEY UPDATE phone = VALUES(phone);

-- ============================================================
-- NOTE: Users and Doctors are NOT inserted here because
--   - User passwords must be BCrypt-encoded (cannot be plain SQL)
--   - StartupDataInitializer creates the admin user automatically
--   - Additional users/doctors can be created via /api/admin/users and /api/doctors
-- ============================================================