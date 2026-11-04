import { api } from '../api.js';
import {
    emptyState,
    esc,
    fmtTime,
    icon,
    loadingState,
    mapApiError,
    plural,
    toast,
    WEEKDAYS_FULL
} from '../ui.js';

const DAY_ORDER = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

function timeMinutes(value) {
    const [hours, minutes] = String(value || '00:00').split(':').map(Number);
    return hours * 60 + minutes;
}

export async function renderSchedule(container, ctx) {
    container.innerHTML = loadingState('Загружаем ваше расписание…');

    let profile;
    let schedule;
    try {
        [profile, schedule] = await Promise.all([
            api('/doctor/profile'),
            api('/doctor/schedule')
        ]);
    } catch (error) {
        const message = error?.status === 404
            ? 'Администратор ещё не создал профиль врача для вашей учётной записи.'
            : mapApiError(error);
        container.innerHTML = emptyState('doctor', 'Расписание недоступно', message);
        return;
    }

    const sorted = schedule.slice().sort((a, b) => {
        const dayDiff = DAY_ORDER.indexOf(a.dayOfWeek) - DAY_ORDER.indexOf(b.dayOfWeek);
        return dayDiff !== 0 ? dayDiff : a.startTime.localeCompare(b.startTime);
    });
    const totalMinutes = sorted
        .filter(item => item.active)
        .reduce((sum, item) => sum + Math.max(0, timeMinutes(item.endTime) - timeMinutes(item.startTime)), 0);
    const today = new Date();
    const todayIndex = (today.getDay() + 6) % 7;

    container.innerHTML = `
        <div class="page-head">
            <div>
                <span class="eyebrow">Кабинет врача</span>
                <h1>Моё расписание</h1>
                <p>Недельные интервалы приёма и длительность слотов.</p>
            </div>
            <div class="page-actions">
                <a class="btn btn-secondary" href="#/appointments">${icon('clipboard', 17)} Мои приёмы</a>
            </div>
        </div>

        <section class="profile-banner">
            <div class="profile-banner-main">
                <span class="avatar avatar-lg">${esc((profile.lastName?.[0] || '') + (profile.firstName?.[0] || ''))}</span>
                <div>
                    <h2>${esc(profile.fullName)}</h2>
                    <p>${esc(profile.specializationName)}${profile.officeNumber ? ` · кабинет ${esc(profile.officeNumber)}` : ''}</p>
                </div>
            </div>
            <div class="profile-banner-stats">
                <span><strong>${sorted.filter(item => item.active).length}</strong> активных интервалов</span>
                <span><strong>${Math.round(totalMinutes / 60 * 10) / 10}</strong> часов в неделю</span>
            </div>
        </section>

        <div class="notice notice-info">
            ${icon('info', 18)}
            <span>Рабочее расписание изменяет администратор клиники. Если нужно скорректировать часы, обратитесь к администратору.</span>
        </div>

        <section class="week-schedule">
            ${DAY_ORDER.map((day, index) => {
                const items = sorted.filter(item => item.dayOfWeek === day);
                return `
                    <article class="day-card ${index === todayIndex ? 'is-today' : ''}">
                        <div class="day-card-head">
                            <h3>${esc(WEEKDAYS_FULL[day])}</h3>
                            ${index === todayIndex ? '<span class="today-tag">Сегодня</span>' : ''}
                        </div>
                        ${items.length ? `
                            <div class="day-intervals">
                                ${items.map(item => `
                                    <div class="day-interval ${item.active ? '' : 'is-inactive'}">
                                        <strong>${fmtTime(item.startTime)}–${fmtTime(item.endTime)}</strong>
                                        <span>${item.slotDurationMinutes} мин / слот</span>
                                        ${item.active ? '' : '<small>выключено</small>'}
                                    </div>`).join('')}
                            </div>` : `<div class="day-empty">${icon('clock', 18)} Нет приёма</div>`}
                    </article>`;
            }).join('')}
        </section>`;
}

export async function renderProfile(container, ctx) {
    container.innerHTML = loadingState('Загружаем профиль…');

    let profile;
    try {
        profile = await api('/doctor/profile');
    } catch (error) {
        const message = error?.status === 404
            ? 'Администратор ещё не создал профиль врача для вашей учётной записи.'
            : mapApiError(error);
        container.innerHTML = emptyState('doctor', 'Профиль недоступен', message);
        return;
    }

    container.innerHTML = `
        <div class="page-head">
            <div>
                <span class="eyebrow">Кабинет врача</span>
                <h1>Мой профиль</h1>
                <p>Контактные данные, кабинет и информация о враче.</p>
            </div>
            <div class="page-actions">
                <a class="btn btn-secondary" href="#/schedule">${icon('clock', 17)} Моё расписание</a>
            </div>
        </div>

        <div class="profile-layout">
            <aside class="card profile-card">
                <div class="profile-card-avatar">
                    <span class="avatar avatar-xl">${esc((profile.lastName?.[0] || '') + (profile.firstName?.[0] || ''))}</span>
                    <h2>${esc(profile.fullName)}</h2>
                    <span class="specialization-chip">${esc(profile.specializationName)}</span>
                    <span class="active-pill ${profile.active ? 'is-active' : 'is-inactive'}">${profile.active ? 'Принимает пациентов' : 'Неактивен'}</span>
                </div>
                <div class="profile-card-list">
                    <div><span>Логин</span><strong><code class="inline-code">${esc(profile.username)}</code></strong></div>
                    <div><span>Специализация</span><strong>${esc(profile.specializationName)}</strong></div>
                    ${profile.officeNumber ? `<div><span>Кабинет</span><strong>${esc(profile.officeNumber)}</strong></div>` : ''}
                    <div><span>В системе с</span><strong>${profile.createdAt ? new Date(profile.createdAt).toLocaleDateString('ru-RU') : '—'}</strong></div>
                </div>
                <p class="profile-note">Специализацию и статус активности изменяет администратор клиники.</p>
            </aside>

            <section class="card profile-form-card">
                <div class="card-head">
                    <div>
                        <span class="eyebrow">Личные данные</span>
                        <h2>Редактировать профиль</h2>
                    </div>
                </div>

                <form class="form-stack" data-profile-form novalidate>
                    <div class="form-grid form-grid-3">
                        <label class="field">
                            <span class="field-label">Фамилия <span class="required">*</span></span>
                            <input class="input" name="lastName" maxlength="100" value="${esc(profile.lastName || '')}" required>
                        </label>
                        <label class="field">
                            <span class="field-label">Имя <span class="required">*</span></span>
                            <input class="input" name="firstName" maxlength="100" value="${esc(profile.firstName || '')}" required>
                        </label>
                        <label class="field">
                            <span class="field-label">Отчество</span>
                            <input class="input" name="middleName" maxlength="100" value="${esc(profile.middleName || '')}">
                        </label>
                        <label class="field">
                            <span class="field-label">Телефон</span>
                            <input class="input" name="phone" maxlength="30" value="${esc(profile.phone || '')}" placeholder="+7 900 000-00-00">
                        </label>
                        <label class="field">
                            <span class="field-label">Email</span>
                            <input class="input" type="email" name="email" maxlength="150" value="${esc(profile.email || '')}" placeholder="doctor@example.com">
                        </label>
                        <label class="field">
                            <span class="field-label">Кабинет</span>
                            <input class="input" name="officeNumber" maxlength="30" value="${esc(profile.officeNumber || '')}" placeholder="Например, 204">
                        </label>
                        <label class="field field-span-3">
                            <span class="field-label">О враче</span>
                            <textarea class="input textarea" name="bio" maxlength="1000" rows="5"
                                      placeholder="Опыт, направления работы, профессиональные интересы">${esc(profile.bio || '')}</textarea>
                        </label>
                    </div>
                    <div class="form-error hidden" data-form-error role="alert"></div>
                    <div class="form-actions">
                        <button class="btn btn-primary" type="submit" data-submit>${icon('check', 17)} Сохранить профиль</button>
                    </div>
                </form>
            </section>
        </div>`;

    const form = container.querySelector('[data-profile-form]');
    const errorBox = container.querySelector('[data-form-error]');
    const submitButton = container.querySelector('[data-submit]');

    form.addEventListener('submit', async event => {
        event.preventDefault();
        errorBox.classList.add('hidden');
        const payload = {
            firstName: form.elements.firstName.value.trim(),
            lastName: form.elements.lastName.value.trim(),
            middleName: form.elements.middleName.value.trim() || null,
            phone: form.elements.phone.value.trim() || null,
            email: form.elements.email.value.trim() || null,
            officeNumber: form.elements.officeNumber.value.trim() || null,
            bio: form.elements.bio.value.trim() || null
        };
        if (!payload.firstName || !payload.lastName) {
            errorBox.textContent = 'Имя и фамилия обязательны.';
            errorBox.classList.remove('hidden');
            return;
        }

        submitButton.disabled = true;
        submitButton.dataset.originalHtml = submitButton.innerHTML;
        submitButton.innerHTML = '<span class="spinner"></span>Сохраняем…';
        try {
            await api('/doctor/profile', { method: 'PUT', body: payload });
            toast('Профиль обновлён');
            await renderProfile(container, ctx);
        } catch (error) {
            errorBox.textContent = mapApiError(error);
            errorBox.classList.remove('hidden');
            submitButton.disabled = false;
            submitButton.innerHTML = submitButton.dataset.originalHtml;
        }
    });
}
