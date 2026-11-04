import { api } from '../api.js';
import {
    confirmDialog,
    debounce,
    emptyState,
    esc,
    fmtTime,
    icon,
    loadingState,
    mapApiError,
    openModal,
    plural,
    toast,
    WEEKDAYS_FULL
} from '../ui.js';

const DAY_ORDER = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

export async function render(container, ctx) {
    container.innerHTML = loadingState('Загружаем врачей клиники…');

    let doctors;
    let specializations;
    try {
        [doctors, specializations] = await Promise.all([
            api('/doctors'),
            api('/specializations')
        ]);
    } catch (error) {
        container.innerHTML = emptyState('alert', 'Не удалось загрузить врачей', mapApiError(error));
        return;
    }

    const state = {
        search: '',
        specializationId: ''
    };
    const canEdit = ctx.role === 'ADMIN';

    container.innerHTML = `
        <div class="page-head">
            <div>
                <span class="eyebrow">Медицинский персонал</span>
                <h1>Врачи</h1>
                <p>Профили специалистов, контакты и недельное рабочее расписание.</p>
            </div>
            <div class="page-actions">
                <button class="btn btn-secondary" type="button" data-refresh>${icon('refresh', 17)} Обновить</button>
                ${canEdit ? `<button class="btn btn-primary" type="button" data-new>${icon('userPlus', 18)} Добавить врача</button>` : ''}
            </div>
        </div>

        <section class="mini-stats" data-mini-stats></section>

        <section class="card filter-card">
            <div class="filters filters-compact">
                <label class="field field-search field-grow">
                    <span class="field-label">Поиск врача</span>
                    <span class="input-icon-wrap">
                        ${icon('search', 17)}
                        <input class="input" type="search" data-search placeholder="ФИО, специализация или кабинет">
                    </span>
                </label>
                <label class="field">
                    <span class="field-label">Специализация</span>
                    <select class="input" data-specialization>
                        <option value="">Все специализации</option>
                        ${specializations.map(item => `<option value="${item.id}">${esc(item.name)}</option>`).join('')}
                    </select>
                </label>
                <button class="btn btn-ghost filter-reset" type="button" data-reset>${icon('close', 16)} Сбросить</button>
            </div>
            <div class="filter-meta"><span data-result-count></span></div>
        </section>

        <section class="doctor-grid" data-grid></section>`;

    const grid = container.querySelector('[data-grid]');
    const searchInput = container.querySelector('[data-search]');
    const specializationSelect = container.querySelector('[data-specialization]');
    const resultCount = container.querySelector('[data-result-count]');
    const miniStats = container.querySelector('[data-mini-stats]');

    function renderStats() {
        const active = doctors.filter(doctor => doctor.active).length;
        miniStats.innerHTML = `
            <span class="mini-stat"><strong>${doctors.length}</strong> ${plural(doctors.length, 'врач', 'врача', 'врачей')}</span>
            <span class="mini-stat mini-stat-completed"><strong>${active}</strong> активных</span>
            <span class="mini-stat"><strong>${specializations.length}</strong> ${plural(specializations.length, 'специализация', 'специализации', 'специализаций')}</span>`;
    }

    function filtered() {
        const search = state.search.trim().toLocaleLowerCase('ru');
        return doctors
            .filter(doctor => {
                if (state.specializationId && doctor.specializationId !== Number(state.specializationId)) {
                    return false;
                }
                if (!search) return true;
                return [
                    doctor.fullName,
                    doctor.specializationName,
                    doctor.officeNumber,
                    doctor.phone,
                    doctor.email
                ].filter(Boolean).join(' ').toLocaleLowerCase('ru').includes(search);
            })
            .sort((a, b) => a.fullName.localeCompare(b.fullName, 'ru'));
    }

    function draw() {
        const items = filtered();
        resultCount.textContent = `Найдено ${items.length} ${plural(items.length, 'врач', 'врача', 'врачей')}`;

        if (!items.length) {
            grid.innerHTML = emptyState(
                'doctor',
                'Врачи не найдены',
                'Измените фильтры или добавьте профиль врача.',
                canEdit
                    ? `<button class="btn btn-primary" type="button" data-empty-new>${icon('userPlus', 16)} Добавить врача</button>`
                    : ''
            );
            grid.querySelector('[data-empty-new]')?.addEventListener('click', () => openDoctorForm());
            return;
        }

        grid.innerHTML = items.map(doctor => `
            <article class="doctor-card ${doctor.active ? '' : 'is-inactive'}">
                <div class="doctor-card-head">
                    <span class="avatar avatar-lg">${esc((doctor.lastName?.[0] || '') + (doctor.firstName?.[0] || ''))}</span>
                    <div class="doctor-card-title">
                        <h3>${esc(doctor.fullName)}</h3>
                        <span class="specialization-chip">${esc(doctor.specializationName)}</span>
                    </div>
                    <span class="active-pill ${doctor.active ? 'is-active' : 'is-inactive'}">${doctor.active ? 'Активен' : 'Неактивен'}</span>
                </div>

                <div class="doctor-contacts">
                    ${doctor.phone ? `<span>${icon('phone', 15)} ${esc(doctor.phone)}</span>` : ''}
                    ${doctor.email ? `<span>${icon('mail', 15)} ${esc(doctor.email)}</span>` : ''}
                    ${doctor.officeNumber ? `<span>${icon('pin', 15)} Кабинет ${esc(doctor.officeNumber)}</span>` : ''}
                    ${!doctor.phone && !doctor.email && !doctor.officeNumber ? '<span class="muted-text">Контактные данные не заполнены</span>' : ''}
                </div>

                ${doctor.bio ? `<p class="doctor-bio">${esc(doctor.bio)}</p>` : ''}

                <div class="doctor-card-actions">
                    <button class="btn btn-secondary btn-sm" type="button" data-schedule="${doctor.id}">
                        ${icon('calendar', 16)} Расписание
                    </button>
                    ${canEdit ? `
                        <button class="icon-btn" type="button" data-edit="${doctor.id}" title="Изменить врача" aria-label="Изменить врача">${icon('edit', 17)}</button>
                        <button class="icon-btn icon-btn-danger" type="button" data-delete="${doctor.id}" title="Удалить врача" aria-label="Удалить врача">${icon('trash', 17)}</button>` : ''}
                </div>
            </article>`).join('');

        grid.querySelectorAll('[data-schedule]').forEach(button => {
            button.addEventListener('click', () => {
                const doctor = doctors.find(item => item.id === Number(button.dataset.schedule));
                if (doctor) openScheduleModal(doctor, { canEdit });
            });
        });
        grid.querySelectorAll('[data-edit]').forEach(button => {
            button.addEventListener('click', () => {
                const doctor = doctors.find(item => item.id === Number(button.dataset.edit));
                if (doctor) openDoctorForm(doctor);
            });
        });
        grid.querySelectorAll('[data-delete]').forEach(button => {
            button.addEventListener('click', () => {
                const doctor = doctors.find(item => item.id === Number(button.dataset.delete));
                if (doctor) deleteDoctor(doctor);
            });
        });
    }

    async function openDoctorForm(doctor = null) {
        if (!canEdit) {
            return;
        }
        const isEdit = Boolean(doctor);
        let availableUsers = [];

        if (!isEdit) {
            try {
                const users = await api('/admin/users');
                const profileUserIds = new Set(doctors.map(item => item.userId));
                availableUsers = users.filter(user =>
                    user.roles.includes('DOCTOR') && !profileUserIds.has(user.id)
                );
            } catch (error) {
                toast(mapApiError(error), 'error');
                return;
            }

            if (!availableUsers.length) {
                openModal({
                    title: 'Новый врач',
                    size: 'sm',
                    body: emptyState(
                        'users',
                        'Нет сотрудников без профиля врача',
                        'Сначала создайте сотрудника с ролью «Врач» или освободите существующего пользователя.',
                        `<a class="btn btn-primary" href="#/users" data-close-modal>Перейти к сотрудникам</a>`
                    ),
                    onMount: ({ root, close }) => {
                        root.querySelector('[data-close-modal]')?.addEventListener('click', close);
                    }
                });
                return;
            }
        }

        if (!specializations.length) {
            openModal({
                title: 'Новый врач',
                size: 'sm',
                body: emptyState(
                    'layers',
                    'Сначала добавьте специализацию',
                    'Для профиля врача нужна хотя бы одна специализация.',
                    `<a class="btn btn-primary" href="#/specializations">Перейти к специализациям</a>`
                )
            });
            return;
        }

        const formId = `doctor-form-${Date.now()}`;
        const selectedSpecialization = doctor?.specializationId || specializations[0].id;
        const body = `
            <form id="${formId}" class="form-stack" novalidate>
                <div class="form-grid">
                    ${!isEdit ? `
                        <label class="field field-span-2">
                            <span class="field-label">Учётная запись врача <span class="required">*</span></span>
                            <select class="input" name="userId" required>
                                ${availableUsers.map(user => `
                                    <option value="${user.id}">${esc(user.fullName)} · ${esc(user.username)}</option>`).join('')}
                            </select>
                            <span class="field-hint">Список содержит сотрудников с ролью «Врач» без профиля.</span>
                        </label>` : `
                        <div class="notice notice-info field-span-2">
                            ${icon('userCheck', 18)}
                            <span>Профиль привязан к учётной записи <strong>${esc(doctor.username)}</strong>. Сменить пользователя нельзя.</span>
                        </div>`}

                    <label class="field">
                        <span class="field-label">Фамилия <span class="required">*</span></span>
                        <input class="input" name="lastName" maxlength="100" value="${esc(doctor?.lastName || '')}" required>
                    </label>
                    <label class="field">
                        <span class="field-label">Имя <span class="required">*</span></span>
                        <input class="input" name="firstName" maxlength="100" value="${esc(doctor?.firstName || '')}" required>
                    </label>
                    <label class="field">
                        <span class="field-label">Отчество</span>
                        <input class="input" name="middleName" maxlength="100" value="${esc(doctor?.middleName || '')}">
                    </label>
                    <label class="field">
                        <span class="field-label">Специализация <span class="required">*</span></span>
                        <select class="input" name="specializationId" required>
                            ${specializations.map(item => `
                                <option value="${item.id}" ${item.id === selectedSpecialization ? 'selected' : ''}>${esc(item.name)}</option>`).join('')}
                        </select>
                    </label>
                    <label class="field">
                        <span class="field-label">Телефон</span>
                        <input class="input" name="phone" maxlength="30" value="${esc(doctor?.phone || '')}" placeholder="+7 900 000-00-00">
                    </label>
                    <label class="field">
                        <span class="field-label">Email</span>
                        <input class="input" type="email" name="email" maxlength="150" value="${esc(doctor?.email || '')}" placeholder="doctor@example.com">
                    </label>
                    <label class="field">
                        <span class="field-label">Кабинет</span>
                        <input class="input" name="officeNumber" maxlength="30" value="${esc(doctor?.officeNumber || '')}" placeholder="Например, 204">
                    </label>
                    <label class="field field-span-2">
                        <span class="field-label">О враче</span>
                        <textarea class="input textarea" name="bio" maxlength="1000" rows="3"
                                  placeholder="Специализация, опыт, направления работы">${esc(doctor?.bio || '')}</textarea>
                    </label>
                    <label class="checkbox-field field-span-2">
                        <input type="checkbox" name="active" ${doctor?.active === false ? '' : 'checked'}>
                        <span><strong>Врач принимает пациентов</strong><small>Неактивный врач не может быть выбран для новой записи.</small></span>
                    </label>
                </div>
                <div class="form-error hidden" data-form-error role="alert"></div>
            </form>`;

        openModal({
            title: isEdit ? 'Изменить профиль врача' : 'Новый врач',
            subtitle: isEdit ? doctor.fullName : 'Профиль создаётся для существующего сотрудника',
            size: 'lg',
            body,
            footer: `
                <button class="btn btn-secondary" type="button" data-dismiss>Отмена</button>
                <button class="btn btn-primary" type="submit" form="${formId}" data-submit>
                    ${isEdit ? 'Сохранить изменения' : 'Создать профиль'}
                </button>`,
            onMount: ({ root, close }) => {
                const form = root.querySelector(`#${formId}`);
                const errorBox = root.querySelector('[data-form-error]');
                const submitButton = root.querySelector('[data-submit]');
                root.querySelector('[data-dismiss]').addEventListener('click', close);

                form.addEventListener('submit', async event => {
                    event.preventDefault();
                    errorBox.classList.add('hidden');

                    const payload = {
                        specializationId: Number(form.elements.specializationId.value),
                        firstName: form.elements.firstName.value.trim(),
                        lastName: form.elements.lastName.value.trim(),
                        middleName: form.elements.middleName.value.trim() || null,
                        phone: form.elements.phone.value.trim() || null,
                        email: form.elements.email.value.trim() || null,
                        officeNumber: form.elements.officeNumber.value.trim() || null,
                        bio: form.elements.bio.value.trim() || null,
                        active: form.elements.active.checked
                    };
                    if (!isEdit) {
                        payload.userId = Number(form.elements.userId.value);
                    }
                    if (!payload.firstName || !payload.lastName || !payload.specializationId || (!isEdit && !payload.userId)) {
                        errorBox.textContent = 'Заполните обязательные поля.';
                        errorBox.classList.remove('hidden');
                        return;
                    }

                    submitButton.disabled = true;
                    submitButton.dataset.originalHtml = submitButton.innerHTML;
                    submitButton.innerHTML = '<span class="spinner"></span>Сохраняем…';
                    try {
                        if (isEdit) {
                            await api(`/doctors/${doctor.id}`, { method: 'PUT', body: payload });
                        } else {
                            await api('/doctors', { method: 'POST', body: payload });
                        }
                        toast(isEdit ? 'Профиль врача обновлён' : 'Профиль врача создан');
                        close();
                        await reload();
                    } catch (error) {
                        errorBox.textContent = mapApiError(error);
                        errorBox.classList.remove('hidden');
                        submitButton.disabled = false;
                        submitButton.innerHTML = submitButton.dataset.originalHtml;
                    }
                });
            }
        });
    }

    async function deleteDoctor(doctor) {
        const confirmed = await confirmDialog({
            title: 'Удалить врача?',
            message: `Профиль «${doctor.fullName}» и его рабочее расписание будут удалены. Если у врача есть приёмы, удаление будет отклонено.`,
            confirmLabel: 'Удалить врача',
            danger: true
        });
        if (!confirmed) {
            return;
        }
        try {
            await api(`/doctors/${doctor.id}`, { method: 'DELETE' });
            toast('Профиль врача удалён');
            await reload();
        } catch (error) {
            toast(mapApiError(error), 'error');
        }
    }

    async function openScheduleModal(doctor, { canEdit: scheduleEditable }) {
        let schedules = [];
        let editingId = null;
        let draft = null;
        let showForm = false;

        const modal = openModal({
            title: 'Рабочее расписание',
            subtitle: `${doctor.fullName} · ${doctor.specializationName}`,
            size: 'lg',
            body: `<div class="loading-state"><span class="spinner spinner-large"></span><span>Загружаем расписание…</span></div>`,
            footer: `<button class="btn btn-secondary" type="button" data-close>Закрыть</button>`,
            onMount: ({ root, body, close }) => {
                root.querySelector('[data-close]').addEventListener('click', close);
                body.addEventListener('click', handleClick);
                body.addEventListener('submit', handleSubmit);
                load();

                async function load() {
                    try {
                        schedules = await api(`/doctors/${doctor.id}/schedule`);
                        render();
                    } catch (error) {
                        body.innerHTML = emptyState('alert', 'Не удалось загрузить расписание', mapApiError(error));
                    }
                }

                function render() {
                    const sorted = schedules.slice().sort((a, b) => {
                        const dayDiff = DAY_ORDER.indexOf(a.dayOfWeek) - DAY_ORDER.indexOf(b.dayOfWeek);
                        return dayDiff !== 0 ? dayDiff : a.startTime.localeCompare(b.startTime);
                    });
                    const totalMinutes = sorted
                        .filter(item => item.active)
                        .reduce((sum, item) => sum + Math.max(0, timeMinutes(item.endTime) - timeMinutes(item.startTime)), 0);
                    const totalHours = Math.round(totalMinutes / 60 * 10) / 10;

                    body.innerHTML = `
                        <div class="schedule-summary">
                            <span><strong>${sorted.filter(item => item.active).length}</strong> активных интервалов</span>
                            <span><strong>${totalHours}</strong> часов в неделю</span>
                            <span>${sorted.length ? `${sorted.length} ${plural(sorted.length, 'запись', 'записи', 'записей')} в расписании` : 'расписание не настроено'}</span>
                        </div>

                        <div class="schedule-table">
                            ${sorted.length ? sorted.map(item => `
                                <div class="schedule-row ${item.active ? '' : 'is-inactive'}">
                                    <span class="schedule-day">${esc(WEEKDAYS_FULL[item.dayOfWeek])}</span>
                                    <strong class="schedule-time">${fmtTime(item.startTime)}–${fmtTime(item.endTime)}</strong>
                                    <span class="schedule-duration">${item.slotDurationMinutes} мин / слот</span>
                                    <span class="active-pill ${item.active ? 'is-active' : 'is-inactive'}">${item.active ? 'Активно' : 'Выключено'}</span>
                                    ${scheduleEditable ? `
                                        <span class="schedule-actions">
                                            <button class="icon-btn" type="button" data-edit="${item.id}" title="Изменить" aria-label="Изменить">${icon('edit', 16)}</button>
                                            <button class="icon-btn icon-btn-danger" type="button" data-delete="${item.id}" title="Удалить" aria-label="Удалить">${icon('trash', 16)}</button>
                                        </span>` : ''}
                                </div>`).join('')
                                : emptyState('clock', 'Расписание не задано', 'Без расписания врач не сможет принимать пациентов.')}
                        </div>

                        ${scheduleEditable ? `
                            <div class="schedule-editor">
                                <div class="section-mini-head">
                                    <span>${editingId ? 'Изменить интервал' : 'Добавить интервал'}</span>
                                    ${editingId ? `<button class="text-link" type="button" data-cancel-edit>Отменить редактирование</button>` : ''}
                                </div>
                                <form class="form-grid schedule-form" data-schedule-form>
                                    <label class="field">
                                        <span class="field-label">День недели</span>
                                        <select class="input" name="dayOfWeek">
                                            ${DAY_ORDER.map(day => `
                                                <option value="${day}" ${(draft?.dayOfWeek || 'MONDAY') === day ? 'selected' : ''}>${esc(WEEKDAYS_FULL[day])}</option>`).join('')}
                                        </select>
                                    </label>
                                    <label class="field">
                                        <span class="field-label">Начало</span>
                                        <input class="input" type="time" name="startTime" value="${esc(draft?.startTime?.slice(0, 5) || '09:00')}" required>
                                    </label>
                                    <label class="field">
                                        <span class="field-label">Окончание</span>
                                        <input class="input" type="time" name="endTime" value="${esc(draft?.endTime?.slice(0, 5) || '17:00')}" required>
                                    </label>
                                    <label class="field">
                                        <span class="field-label">Слот, минут</span>
                                        <input class="input" type="number" name="slotDurationMinutes" min="5" max="480" step="5"
                                               value="${draft?.slotDurationMinutes || 30}" required>
                                    </label>
                                    <label class="checkbox-field field-span-2">
                                        <input type="checkbox" name="active" ${draft?.active === false ? '' : 'checked'}>
                                        <span><strong>Интервал активен</strong><small>Выключенный интервал не участвует в записи.</small></span>
                                    </label>
                                    <div class="form-error hidden field-span-2" data-form-error role="alert"></div>
                                    <div class="form-actions field-span-2">
                                        <button class="btn btn-primary" type="submit" data-submit>
                                            ${editingId ? 'Сохранить интервал' : 'Добавить в расписание'}
                                        </button>
                                    </div>
                                </form>
                            </div>` : ''}`;
                }

                function timeMinutes(value) {
                    const [hours, minutes] = String(value || '00:00').split(':').map(Number);
                    return hours * 60 + minutes;
                }

                function handleClick(event) {
                    const edit = event.target.closest('[data-edit]');
                    const remove = event.target.closest('[data-delete]');
                    const cancel = event.target.closest('[data-cancel-edit]');

                    if (edit) {
                        draft = schedules.find(item => item.id === Number(edit.dataset.edit)) || null;
                        editingId = draft?.id || null;
                        render();
                        body.querySelector('[name="startTime"]')?.focus();
                        return;
                    }
                    if (remove) {
                        confirmDialog({
                            title: 'Удалить интервал?',
                            message: 'Интервал будет удалён из недельного расписания врача.',
                            confirmLabel: 'Удалить',
                            danger: true
                        }).then(async confirmed => {
                            if (!confirmed) {
                                return;
                            }
                            try {
                                await api(`/doctors/${doctor.id}/schedule/${remove.dataset.delete}`, { method: 'DELETE' });
                                toast('Интервал удалён');
                                if (editingId === Number(remove.dataset.delete)) {
                                    editingId = null;
                                    draft = null;
                                }
                                await load();
                            } catch (error) {
                                toast(mapApiError(error), 'error');
                            }
                        });
                        return;
                    }
                    if (cancel) {
                        editingId = null;
                        draft = null;
                        render();
                    }
                }

                async function handleSubmit(event) {
                    const form = event.target.closest('[data-schedule-form]');
                    if (!form) {
                        return;
                    }
                    event.preventDefault();
                    const errorBox = form.querySelector('[data-form-error]');
                    const submitButton = form.querySelector('[data-submit]');
                    errorBox.classList.add('hidden');

                    const payload = {
                        dayOfWeek: form.elements.dayOfWeek.value,
                        startTime: form.elements.startTime.value,
                        endTime: form.elements.endTime.value,
                        slotDurationMinutes: Number(form.elements.slotDurationMinutes.value),
                        active: form.elements.active.checked
                    };

                    if (!payload.startTime || !payload.endTime || payload.startTime >= payload.endTime) {
                        errorBox.textContent = 'Время начала должно быть раньше времени окончания.';
                        errorBox.classList.remove('hidden');
                        return;
                    }
                    if (payload.slotDurationMinutes < 5 || payload.slotDurationMinutes > 480) {
                        errorBox.textContent = 'Длительность слота должна быть от 5 до 480 минут.';
                        errorBox.classList.remove('hidden');
                        return;
                    }

                    submitButton.disabled = true;
                    submitButton.dataset.originalHtml = submitButton.innerHTML;
                    submitButton.innerHTML = '<span class="spinner"></span>Сохраняем…';
                    try {
                        if (editingId) {
                            await api(`/doctors/${doctor.id}/schedule/${editingId}`, { method: 'PUT', body: payload });
                        } else {
                            await api(`/doctors/${doctor.id}/schedule`, { method: 'POST', body: payload });
                        }
                        toast(editingId ? 'Интервал обновлён' : 'Интервал добавлен');
                        editingId = null;
                        draft = null;
                        await load();
                    } catch (error) {
                        errorBox.textContent = mapApiError(error);
                        errorBox.classList.remove('hidden');
                        submitButton.disabled = false;
                        submitButton.innerHTML = submitButton.dataset.originalHtml;
                    }
                }
            }
        });

        return modal;
    }

    async function reload() {
        try {
            [doctors, specializations] = await Promise.all([
                api('/doctors'),
                api('/specializations')
            ]);
            specializationSelect.innerHTML = `
                <option value="">Все специализации</option>
                ${specializations.map(item => `<option value="${item.id}" ${String(item.id) === state.specializationId ? 'selected' : ''}>${esc(item.name)}</option>`).join('')}`;
            renderStats();
            draw();
        } catch (error) {
            toast(mapApiError(error), 'error');
        }
    }

    searchInput.addEventListener('input', debounce(() => {
        state.search = searchInput.value;
        draw();
    }, 220));
    specializationSelect.addEventListener('change', () => {
        state.specializationId = specializationSelect.value;
        draw();
    });
    container.querySelector('[data-reset]').addEventListener('click', () => {
        state.search = '';
        state.specializationId = '';
        searchInput.value = '';
        specializationSelect.value = '';
        draw();
    });
    container.querySelector('[data-refresh]').addEventListener('click', reload);
    container.querySelector('[data-new]')?.addEventListener('click', () => openDoctorForm());

    renderStats();
    draw();
}
