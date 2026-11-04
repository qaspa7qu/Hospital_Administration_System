import { api } from '../api.js';
import {
    addDays,
    emptyState,
    esc,
    fmtDate,
    fmtMoney,
    fmtShortDate,
    fmtTime,
    icon,
    loadingState,
    mapApiError,
    parseDate,
    plural,
    primaryRole,
    roleLabel,
    startOfWeek,
    statusBadge,
    statusLabel,
    toISO,
    toast,
    todayISO,
    WEEKDAY_ENUM,
    WEEKDAYS_FULL,
    WEEKDAYS_SHORT
} from '../ui.js';
import {
    appointmentRow,
    doctorLabel,
    openAppointmentDetails,
    openAppointmentForm,
    patientFullName
} from '../appointment-shared.js';

function greeting() {
    const hour = new Date().getHours();
    if (hour < 6) return 'Доброй ночи';
    if (hour < 12) return 'Доброе утро';
    if (hour < 18) return 'Добрый день';
    return 'Добрый вечер';
}

function statCard({ iconName, tone, label, value, hint, action = '' }) {
    return `
        <article class="stat-card stat-${tone}">
            <span class="stat-icon">${icon(iconName, 22)}</span>
            <div class="stat-body">
                <span class="stat-label">${esc(label)}</span>
                <strong class="stat-value">${value}</strong>
                ${hint ? `<span class="stat-hint">${hint}</span>` : ''}
            </div>
            ${action}
        </article>`;
}

function miniWeek(appointments, selectedDate = todayISO()) {
    const monday = startOfWeek(new Date());
    const days = Array.from({ length: 7 }, (_, index) => addDays(monday, index));
    return `
        <div class="week-strip">
            ${days.map(day => {
                const iso = toISO(day);
                const dayAppointments = appointments.filter(item => item.appointmentDate === iso);
                const active = dayAppointments.filter(item => item.status !== 'CANCELLED').length;
                return `
                    <button class="week-day ${iso === selectedDate ? 'is-today' : ''}" type="button"
                            data-day="${iso}" aria-label="${esc(fmtDate(iso))}">
                        <span>${WEEKDAYS_SHORT[(day.getDay() + 6) % 7]}</span>
                        <strong>${day.getDate()}</strong>
                        <small>${active ? `${active} ${plural(active, 'приём', 'приёма', 'приёмов')}` : 'свободно'}</small>
                    </button>`;
            }).join('')}
        </div>`;
}

export async function render(container, ctx) {
    const role = primaryRole(ctx.user);
    if (role === 'DOCTOR') {
        await renderDoctorDashboard(container, ctx);
        return;
    }
    await renderClinicDashboard(container, ctx);
}

async function renderClinicDashboard(container, ctx) {
    container.innerHTML = loadingState('Готовим сводку по клинике…');

    let appointments;
    let doctors;
    let patients;
    try {
        [appointments, doctors, patients] = await Promise.all([
            api('/appointments'),
            api('/doctors'),
            api('/patients')
        ]);
    } catch (error) {
        container.innerHTML = emptyState('alert', 'Не удалось загрузить сводку', mapApiError(error));
        return;
    }

    function draw() {
        const today = todayISO();
        const todayAppointments = appointments
            .filter(item => item.appointmentDate === today)
            .sort((a, b) => a.startTime.localeCompare(b.startTime));
        const scheduledToday = todayAppointments.filter(item => item.status === 'SCHEDULED');
        const completedToday = todayAppointments.filter(item => item.status === 'COMPLETED');
        const revenueToday = completedToday.reduce((sum, item) => sum + Number(item.totalPrice || 0), 0);
        const activeDoctors = doctors.filter(doctor => doctor.active).length;
        const upcoming = appointments
            .filter(item => item.status === 'SCHEDULED' && item.appointmentDate >= today)
            .sort((a, b) => `${a.appointmentDate}T${a.startTime}`.localeCompare(`${b.appointmentDate}T${b.startTime}`))
            .slice(0, 6);

        container.innerHTML = `
            <section class="hero">
                <div class="hero-content">
                    <span class="hero-eyebrow">${icon('sparkles', 16)} ${esc(greeting())}, ${esc(ctx.user.fullName)}</span>
                    <h1>Клиника работает по расписанию</h1>
                    <p>${esc(fmtDate(today))} · ${todayAppointments.length} ${plural(todayAppointments.length, 'приём', 'приёма', 'приёмов')} на сегодня, ${scheduledToday.length} ${plural(scheduledToday.length, 'ожидает', 'ожидают', 'ожидают')} подтверждения.</p>
                    <div class="hero-actions">
                        <button class="btn btn-light" type="button" data-new-appointment>
                            ${icon('calendarPlus', 18)} Новая запись
                        </button>
                        <a class="btn btn-hero-ghost" href="#/calendar?date=${today}">
                            ${icon('calendar', 18)} Открыть календарь
                        </a>
                    </div>
                </div>
                <div class="hero-visual" aria-hidden="true">
                    <span class="hero-cross"></span>
                    <span class="hero-pulse"></span>
                </div>
            </section>

            <section class="stats-grid stats-grid-4">
                ${statCard({
                    iconName: 'calendar',
                    tone: 'teal',
                    label: 'Приёмы сегодня',
                    value: todayAppointments.length,
                    hint: `${scheduledToday.length} запланировано`
                })}
                ${statCard({
                    iconName: 'patients',
                    tone: 'blue',
                    label: 'Пациенты',
                    value: patients.length,
                    hint: 'в картотеке клиники'
                })}
                ${statCard({
                    iconName: 'doctor',
                    tone: 'mint',
                    label: 'Активные врачи',
                    value: activeDoctors,
                    hint: `всего ${doctors.length}`
                })}
                ${statCard({
                    iconName: 'wallet',
                    tone: 'amber',
                    label: 'Выручка сегодня',
                    value: fmtMoney(revenueToday),
                    hint: `${completedToday.length} завершено`
                })}
            </section>

            <div class="dashboard-grid">
                <section class="card">
                    <div class="card-head">
                        <div>
                            <span class="eyebrow">Расписание</span>
                            <h2>Приёмы сегодня</h2>
                        </div>
                        <a class="text-link" href="#/calendar?date=${today}">Календарь ${icon('arrowRight', 15)}</a>
                    </div>
                    ${miniWeek(appointments)}
                    <div class="appointment-list">
                        ${todayAppointments.length
                            ? todayAppointments.map(item => appointmentRow(item)).join('')
                            : emptyState('calendar', 'На сегодня записей нет', 'Свободное время можно посмотреть в календаре.')}
                    </div>
                </section>

                <aside class="dashboard-side">
                    <section class="card">
                        <div class="card-head">
                            <div>
                                <span class="eyebrow">Ближайшие</span>
                                <h2>Следующие приёмы</h2>
                            </div>
                        </div>
                        <div class="compact-list">
                            ${upcoming.length ? upcoming.map(item => `
                                <button class="compact-row" type="button" data-appointment="${item.id}">
                                    <span class="compact-date">
                                        <strong>${fmtTime(item.startTime)}</strong>
                                        <small>${fmtShortDate(item.appointmentDate)}</small>
                                    </span>
                                    <span class="compact-main">
                                        <strong>${esc(item.patientFullName)}</strong>
                                        <small>${esc(item.doctorFullName)}</small>
                                    </span>
                                    ${statusBadge(item.status)}
                                </button>`).join('')
                                : `<p class="muted-text">Запланированных приёмов пока нет.</p>`}
                        </div>
                    </section>

                    <section class="card quick-card">
                        <div class="card-head">
                            <div>
                                <span class="eyebrow">Быстрые действия</span>
                                <h2>Начать работу</h2>
                            </div>
                        </div>
                        <div class="quick-actions">
                            <button class="quick-action" type="button" data-new-appointment>
                                <span class="quick-icon">${icon('calendarPlus', 20)}</span>
                                <span><strong>Записать пациента</strong><small>Подобрать свободный слот</small></span>
                            </button>
                            <button class="quick-action" type="button" data-new-patient>
                                <span class="quick-icon">${icon('userPlus', 20)}</span>
                                <span><strong>Новая карточка</strong><small>Добавить пациента в базу</small></span>
                            </button>
                            <a class="quick-action" href="#/calendar">
                                <span class="quick-icon">${icon('calendar', 20)}</span>
                                <span><strong>Календарь клиники</strong><small>Загрузка по дням и врачам</small></span>
                            </a>
                            ${ctx.role === 'ADMIN' ? `
                                <a class="quick-action" href="#/reports">
                                    <span class="quick-icon">${icon('chart', 20)}</span>
                                    <span><strong>Отчёт за период</strong><small>Выручка и статистика</small></span>
                                </a>` : ''}
                        </div>
                    </section>
                </aside>
            </div>`;

        container.querySelectorAll('[data-appointment]').forEach(button => {
            button.addEventListener('click', () => {
                const appointment = appointments.find(item => item.id === Number(button.dataset.appointment));
                if (appointment) {
                    openAppointmentDetails(appointment, {
                        role: ctx.role,
                        onChanged: () => renderClinicDashboard(container, ctx)
                    });
                }
            });
        });

        container.querySelectorAll('[data-new-appointment]').forEach(button => {
            button.addEventListener('click', () => openAppointmentForm({
                doctors,
                patients,
                onSaved: () => renderClinicDashboard(container, ctx)
            }));
        });

        container.querySelector('[data-new-patient]')?.addEventListener('click', () => {
            window.location.hash = '#/patients?new=1';
        });

        container.querySelectorAll('[data-day]').forEach(button => {
            button.addEventListener('click', () => {
                window.location.hash = `#/calendar?date=${button.dataset.day}`;
            });
        });
    }

    draw();
}

async function renderDoctorDashboard(container, ctx) {
    container.innerHTML = loadingState('Готовим ваше расписание…');

    let profile;
    let schedule;
    let appointments;
    try {
        [profile, schedule, appointments] = await Promise.all([
            api('/doctor/profile'),
            api('/doctor/schedule'),
            api('/doctor/appointments')
        ]);
    } catch (error) {
        const message = error?.status === 404
            ? 'Администратор ещё не создал профиль врача для вашей учётной записи.'
            : mapApiError(error);
        container.innerHTML = emptyState('doctor', 'Кабинет врача пока недоступен', message);
        return;
    }

    const today = todayISO();
    const todayAppointments = appointments
        .filter(item => item.appointmentDate === today)
        .sort((a, b) => a.startTime.localeCompare(b.startTime));
    const scheduled = todayAppointments.filter(item => item.status === 'SCHEDULED');
    const completed = todayAppointments.filter(item => item.status === 'COMPLETED');
    const upcoming = appointments
        .filter(item => item.status === 'SCHEDULED' && `${item.appointmentDate}T${item.startTime}` >= `${today}T00:00`)
        .sort((a, b) => `${a.appointmentDate}T${a.startTime}`.localeCompare(`${b.appointmentDate}T${b.startTime}`));
    const next = upcoming[0];
    const todaySchedules = schedule.filter(item => item.active && item.dayOfWeek === WEEKDAY_ENUM[new Date().getDay()]);

    container.innerHTML = `
        <section class="hero hero-doctor">
            <div class="hero-content">
                <span class="hero-eyebrow">${icon('stethoscope', 16)} Кабинет врача · ${esc(profile.specializationName)}</span>
                <h1>${esc(greeting())}, ${esc([profile.firstName, profile.middleName].filter(Boolean).join(' '))}</h1>
                <p>${esc(fmtDate(today))} · ${todaySchedules.length ? todaySchedules.map(item => `${fmtTime(item.startTime)}–${fmtTime(item.endTime)}`).join(', ') : 'сегодня нет приёма по расписанию'}${profile.officeNumber ? ` · кабинет ${esc(profile.officeNumber)}` : ''}</p>
                <div class="hero-actions">
                    <a class="btn btn-light" href="#/appointments">${icon('clipboard', 18)} Мои приёмы</a>
                    <a class="btn btn-hero-ghost" href="#/schedule">${icon('clock', 18)} Расписание</a>
                </div>
            </div>
            <div class="hero-visual hero-visual-doctor" aria-hidden="true">
                <span class="hero-cross"></span>
                <span class="hero-pulse"></span>
            </div>
        </section>

        <section class="stats-grid stats-grid-4">
            ${statCard({
                iconName: 'calendar',
                tone: 'teal',
                label: 'Приёмы сегодня',
                value: todayAppointments.length,
                hint: `${scheduled.length} запланировано`
            })}
            ${statCard({
                iconName: 'clock',
                tone: 'blue',
                label: 'Ближайший приём',
                value: next ? fmtTime(next.startTime) : '—',
                hint: next ? `${fmtShortDate(next.appointmentDate)} · ${esc(next.patientFullName)}` : 'свободное время'
            })}
            ${statCard({
                iconName: 'checkCircle',
                tone: 'mint',
                label: 'Завершено сегодня',
                value: completed.length,
                hint: `из ${todayAppointments.length} приёмов`
            })}
            ${statCard({
                iconName: 'wallet',
                tone: 'amber',
                label: 'Выручка сегодня',
                value: fmtMoney(completed.reduce((sum, item) => sum + Number(item.totalPrice || 0), 0)),
                hint: 'по завершённым приёмам'
            })}
        </section>

        <div class="dashboard-grid">
            <section class="card">
                <div class="card-head">
                    <div>
                        <span class="eyebrow">Расписание</span>
                        <h2>Мои приёмы сегодня</h2>
                    </div>
                    <a class="text-link" href="#/appointments">Все приёмы ${icon('arrowRight', 15)}</a>
                </div>
                <div class="appointment-list">
                    ${todayAppointments.length
                        ? todayAppointments.map(item => appointmentRow(item, { showDoctor: false })).join('')
                        : emptyState('calendar', 'На сегодня приёмов нет', 'Загляните в раздел «Мои приёмы», чтобы увидеть будущие записи.')}
                </div>
            </section>

            <aside class="dashboard-side">
                <section class="card">
                    <div class="card-head">
                        <div>
                            <span class="eyebrow">На неделю</span>
                            <h2>Рабочие смены</h2>
                        </div>
                        <a class="text-link" href="#/schedule">Подробнее ${icon('arrowRight', 15)}</a>
                    </div>
                    <div class="schedule-mini-list">
                        ${schedule.length ? schedule
                            .slice()
                            .sort((a, b) => ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].indexOf(a.dayOfWeek) - ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].indexOf(b.dayOfWeek))
                            .map(item => `
                                <div class="schedule-mini-row ${item.active ? '' : 'is-inactive'}">
                                    <span>${esc(WEEKDAYS_FULL[item.dayOfWeek])}</span>
                                    <strong>${fmtTime(item.startTime)}–${fmtTime(item.endTime)}</strong>
                                    <small>${item.slotDurationMinutes} мин</small>
                                </div>`).join('')
                            : '<p class="muted-text">Расписание пока не настроено.</p>'}
                    </div>
                </section>

                <section class="card quick-card">
                    <div class="card-head">
                        <div>
                            <span class="eyebrow">Профиль</span>
                            <h2>Мои данные</h2>
                        </div>
                    </div>
                    <div class="profile-mini">
                        <div class="avatar avatar-lg">${esc((profile.firstName?.[0] || '') + (profile.lastName?.[0] || ''))}</div>
                        <div>
                            <strong>${esc(profile.lastName)} ${esc(profile.firstName)} ${esc(profile.middleName || '')}</strong>
                            <span>${esc(profile.specializationName)}</span>
                        </div>
                    </div>
                    <div class="profile-mini-details">
                        ${profile.phone ? `<span>${icon('phone', 15)} ${esc(profile.phone)}</span>` : ''}
                        ${profile.email ? `<span>${icon('mail', 15)} ${esc(profile.email)}</span>` : ''}
                        ${profile.officeNumber ? `<span>${icon('pin', 15)} кабинет ${esc(profile.officeNumber)}</span>` : ''}
                    </div>
                    <a class="btn btn-secondary btn-block" href="#/profile">Редактировать профиль</a>
                </section>
            </aside>
        </div>`;

    container.querySelectorAll('[data-appointment]').forEach(button => {
        button.addEventListener('click', () => {
            const appointment = appointments.find(item => item.id === Number(button.dataset.appointment));
            if (appointment) {
                openAppointmentDetails(appointment, {
                    role: 'DOCTOR',
                    onChanged: () => renderDoctorDashboard(container, ctx)
                });
            }
        });
    });
}
