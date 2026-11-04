import { api } from '../api.js';
import {
    confirmDialog,
    debounce,
    emptyState,
    esc,
    fmtDate,
    fmtMoney,
    fmtShortDate,
    fmtTime,
    icon,
    loadingState,
    mapApiError,
    openModal,
    plural,
    statusBadge,
    toast,
    todayISO
} from '../ui.js';
import { openAppointmentDetails, patientFullName } from '../appointment-shared.js';

function ageOf(birthDate) {
    if (!birthDate) {
        return '';
    }
    const birth = new Date(birthDate);
    const now = new Date();
    let age = now.getFullYear() - birth.getFullYear();
    const monthDiff = now.getMonth() - birth.getMonth();
    if (monthDiff < 0 || (monthDiff === 0 && now.getDate() < birth.getDate())) {
        age -= 1;
    }
    if (age < 0) {
        return '';
    }
    return `${age} ${plural(age, 'год', 'года', 'лет')}`;
}

export async function render(container, ctx) {
    container.innerHTML = loadingState('Загружаем картотеку пациентов…');

    let patients;
    try {
        patients = await api('/patients');
    } catch (error) {
        container.innerHTML = emptyState('alert', 'Не удалось загрузить пациентов', mapApiError(error));
        return;
    }

    const state = {
        search: '',
        page: 1,
        pageSize: 12
    };

    container.innerHTML = `
        <div class="page-head">
            <div>
                <span class="eyebrow">Картотека клиники</span>
                <h1>Пациенты</h1>
                <p>Контактные данные, история приёмов и заметки в одной карточке.</p>
            </div>
            <div class="page-actions">
                <button class="btn btn-secondary" type="button" data-refresh>${icon('refresh', 17)} Обновить</button>
                <button class="btn btn-primary" type="button" data-new>${icon('userPlus', 18)} Новый пациент</button>
            </div>
        </div>

        <section class="mini-stats" data-mini-stats></section>

        <section class="card filter-card">
            <div class="filters filters-compact">
                <label class="field field-search field-grow">
                    <span class="field-label">Поиск по картотеке</span>
                    <span class="input-icon-wrap">
                        ${icon('search', 17)}
                        <input class="input" type="search" data-search placeholder="Фамилия, телефон или email">
                    </span>
                </label>
                <button class="btn btn-ghost filter-reset" type="button" data-reset>${icon('close', 16)} Сбросить</button>
            </div>
            <div class="filter-meta">
                <span data-result-count></span>
                <span class="muted-text">Поиск выполняется по всей картотеке</span>
            </div>
        </section>

        <section class="card table-card">
            <div class="table-wrap" data-table></div>
            <div class="pagination" data-pager></div>
        </section>`;

    const searchInput = container.querySelector('[data-search]');
    const tableWrap = container.querySelector('[data-table]');
    const pager = container.querySelector('[data-pager]');
    const resultCount = container.querySelector('[data-result-count]');
    const miniStats = container.querySelector('[data-mini-stats]');

    function renderStats() {
        const withPhone = patients.filter(patient => patient.phone).length;
        const withEmail = patients.filter(patient => patient.email).length;
        miniStats.innerHTML = `
            <span class="mini-stat"><strong>${patients.length}</strong> ${plural(patients.length, 'пациент', 'пациента', 'пациентов')} в картотеке</span>
            <span class="mini-stat"><strong>${withPhone}</strong> с телефоном</span>
            <span class="mini-stat"><strong>${withEmail}</strong> с email</span>`;
    }

    function filtered() {
        const search = state.search.trim().toLocaleLowerCase('ru');
        return patients
            .filter(patient => {
                if (!search) return true;
                return [
                    patientFullName(patient),
                    patient.phone,
                    patient.email,
                    patient.address
                ].filter(Boolean).join(' ').toLocaleLowerCase('ru').includes(search);
            })
            .sort((a, b) => patientFullName(a).localeCompare(patientFullName(b), 'ru'));
    }

    function draw() {
        const items = filtered();
        const totalPages = Math.max(1, Math.ceil(items.length / state.pageSize));
        state.page = Math.min(state.page, totalPages);
        const start = (state.page - 1) * state.pageSize;
        const pageItems = items.slice(start, start + state.pageSize);

        resultCount.textContent = items.length
            ? `Найдено ${items.length} ${plural(items.length, 'пациент', 'пациента', 'пациентов')}`
            : 'Пациенты не найдены';

        if (!pageItems.length) {
            tableWrap.innerHTML = emptyState(
                'patients',
                state.search ? 'Пациенты не найдены' : 'Картотека пока пуста',
                state.search ? 'Попробуйте изменить запрос.' : 'Добавьте первого пациента, чтобы начать запись на приём.',
                `<button class="btn btn-primary" type="button" data-empty-new>${icon('userPlus', 16)} Новый пациент</button>`
            );
            tableWrap.querySelector('[data-empty-new]')?.addEventListener('click', openForm);
            pager.innerHTML = '';
            return;
        }

        tableWrap.innerHTML = `
            <table class="data-table">
                <thead>
                    <tr>
                        <th>Пациент</th>
                        <th>Телефон</th>
                        <th>Дата рождения</th>
                        <th>Email</th>
                        <th>Заметки</th>
                        <th></th>
                    </tr>
                </thead>
                <tbody>
                    ${pageItems.map(patient => `
                        <tr class="table-row-clickable" data-patient="${patient.id}" tabindex="0">
                            <td>
                                <div class="table-person">
                                    <span class="avatar avatar-sm">${esc((patient.lastName?.[0] || '') + (patient.firstName?.[0] || ''))}</span>
                                    <div class="table-stack">
                                        <strong>${esc(patientFullName(patient))}</strong>
                                        <span>${patient.birthDate ? esc(ageOf(patient.birthDate)) : 'дата рождения не указана'}</span>
                                    </div>
                                </div>
                            </td>
                            <td>${esc(patient.phone || '—')}</td>
                            <td>${patient.birthDate ? fmtShortDate(patient.birthDate) : '—'}</td>
                            <td>${esc(patient.email || '—')}</td>
                            <td class="table-notes">${esc(patient.notes || '—')}</td>
                            <td class="table-actions">
                                <button class="icon-btn" type="button" data-open="${patient.id}" title="Открыть карточку" aria-label="Открыть карточку">${icon('eye', 17)}</button>
                                <button class="icon-btn" type="button" data-edit="${patient.id}" title="Изменить" aria-label="Изменить">${icon('edit', 17)}</button>
                                <button class="icon-btn icon-btn-danger" type="button" data-delete="${patient.id}" title="Удалить" aria-label="Удалить">${icon('trash', 17)}</button>
                            </td>
                        </tr>`).join('')}
                </tbody>
            </table>`;

        pager.innerHTML = `
            <span class="pager-info">${start + 1}–${Math.min(start + state.pageSize, items.length)} из ${items.length}</span>
            <div class="pager-controls">
                <button class="icon-btn" type="button" data-page="${state.page - 1}" ${state.page === 1 ? 'disabled' : ''} aria-label="Предыдущая страница">${icon('chevronLeft')}</button>
                <span class="pager-current">Страница ${state.page} из ${totalPages}</span>
                <button class="icon-btn" type="button" data-page="${state.page + 1}" ${state.page === totalPages ? 'disabled' : ''} aria-label="Следующая страница">${icon('chevronRight')}</button>
            </div>`;
    }

    function openForm(patient = null) {
        const isEdit = Boolean(patient);
        const formId = `patient-form-${Date.now()}`;
        const body = `
            <form id="${formId}" class="form-stack" novalidate>
                <div class="form-grid form-grid-3">
                    <label class="field">
                        <span class="field-label">Фамилия <span class="required">*</span></span>
                        <input class="input" name="lastName" maxlength="100" value="${esc(patient?.lastName || '')}" required>
                    </label>
                    <label class="field">
                        <span class="field-label">Имя <span class="required">*</span></span>
                        <input class="input" name="firstName" maxlength="100" value="${esc(patient?.firstName || '')}" required>
                    </label>
                    <label class="field">
                        <span class="field-label">Отчество</span>
                        <input class="input" name="middleName" maxlength="100" value="${esc(patient?.middleName || '')}">
                    </label>
                    <label class="field">
                        <span class="field-label">Дата рождения</span>
                        <input class="input" type="date" name="birthDate" max="${todayISO()}"
                               value="${esc(patient?.birthDate || '')}">
                    </label>
                    <label class="field">
                        <span class="field-label">Телефон <span class="required">*</span></span>
                        <input class="input" name="phone" maxlength="30" value="${esc(patient?.phone || '')}" placeholder="+7 900 000-00-00" required>
                    </label>
                    <label class="field">
                        <span class="field-label">Email</span>
                        <input class="input" type="email" name="email" maxlength="150" value="${esc(patient?.email || '')}" placeholder="patient@example.com">
                    </label>
                    <label class="field field-span-3">
                        <span class="field-label">Адрес</span>
                        <input class="input" name="address" maxlength="500" value="${esc(patient?.address || '')}" placeholder="Город, улица, дом">
                    </label>
                    <label class="field field-span-3">
                        <span class="field-label">Заметки</span>
                        <textarea class="input textarea" name="notes" maxlength="1000" rows="3"
                                  placeholder="Аллергии, особенности здоровья, пожелания">${esc(patient?.notes || '')}</textarea>
                    </label>
                </div>
                <div class="form-error hidden" data-form-error role="alert"></div>
            </form>`;

        openModal({
            title: isEdit ? 'Изменить карточку пациента' : 'Новый пациент',
            subtitle: isEdit ? 'Контактные данные и заметки' : 'Заполните основные данные для записи на приём',
            size: 'lg',
            body,
            footer: `
                <button class="btn btn-secondary" type="button" data-dismiss>Отмена</button>
                <button class="btn btn-primary" type="submit" form="${formId}" data-submit>
                    ${isEdit ? 'Сохранить изменения' : 'Создать карточку'}
                </button>`,
            onMount: ({ root, close }) => {
                const form = root.querySelector(`#${formId}`);
                const errorBox = root.querySelector('[data-form-error]');
                const submitButton = root.querySelector('[data-submit]');
                root.querySelector('[data-dismiss]').addEventListener('click', close);

                const showError = message => {
                    errorBox.textContent = message;
                    errorBox.classList.remove('hidden');
                };

                form.addEventListener('submit', async event => {
                    event.preventDefault();
                    errorBox.classList.add('hidden');

                    const payload = {
                        firstName: form.elements.firstName.value.trim(),
                        lastName: form.elements.lastName.value.trim(),
                        middleName: form.elements.middleName.value.trim() || null,
                        birthDate: form.elements.birthDate.value || null,
                        phone: form.elements.phone.value.trim(),
                        email: form.elements.email.value.trim() || null,
                        address: form.elements.address.value.trim() || null,
                        notes: form.elements.notes.value.trim() || null
                    };

                    if (!payload.firstName || !payload.lastName || !payload.phone) {
                        showError('Фамилия, имя и телефон обязательны.');
                        return;
                    }
                    if (payload.birthDate && payload.birthDate >= todayISO()) {
                        showError('Дата рождения должна быть в прошлом.');
                        return;
                    }

                    submitButton.disabled = true;
                    submitButton.dataset.originalHtml = submitButton.innerHTML;
                    submitButton.innerHTML = '<span class="spinner"></span>Сохраняем…';
                    try {
                        if (isEdit) {
                            await api(`/patients/${patient.id}`, { method: 'PUT', body: payload });
                        } else {
                            await api('/patients', { method: 'POST', body: payload });
                        }
                        toast(isEdit ? 'Карточка пациента обновлена' : 'Пациент добавлен в картотеку');
                        close();
                        await reload();
                    } catch (error) {
                        showError(mapApiError(error));
                        submitButton.disabled = false;
                        submitButton.innerHTML = submitButton.dataset.originalHtml;
                    }
                });
            }
        });
    }

    async function openDetails(patient) {
        let patientAppointments = [];
        try {
            patientAppointments = await api('/appointments', { params: { patientId: patient.id } });
        } catch {
            patientAppointments = [];
        }

        openModal({
            title: 'Карточка пациента',
            subtitle: 'Контактные данные и история приёмов',
            size: 'lg',
            body: `
                <div class="patient-hero">
                    <span class="avatar avatar-xl">${esc((patient.lastName?.[0] || '') + (patient.firstName?.[0] || ''))}</span>
                    <div>
                        <h3>${esc(patientFullName(patient))}</h3>
                        <p>${patient.birthDate ? `${fmtDate(patient.birthDate)} · ${esc(ageOf(patient.birthDate))}` : 'Дата рождения не указана'}</p>
                    </div>
                </div>

                <div class="detail-grid detail-grid-3">
                    <div class="detail-item">
                        <span class="detail-label">Телефон</span>
                        <strong>${esc(patient.phone || '—')}</strong>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">Email</span>
                        <strong>${esc(patient.email || '—')}</strong>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">Адрес</span>
                        <span class="detail-notes">${esc(patient.address || '—')}</span>
                    </div>
                </div>

                <div class="detail-block">
                    <span class="detail-label">Заметки</span>
                    <p class="detail-notes">${esc(patient.notes || 'Нет заметок')}</p>
                </div>

                <div class="detail-block">
                    <div class="section-mini-head">
                        <span>История приёмов</span>
                        <small>${patientAppointments.length} ${plural(patientAppointments.length, 'запись', 'записи', 'записей')}</small>
                    </div>
                    ${patientAppointments.length ? `
                        <div class="history-list">
                            ${patientAppointments.map(item => `
                                <button class="history-row" type="button" data-appointment="${item.id}">
                                    <span class="history-date">
                                        <strong>${fmtShortDate(item.appointmentDate)}</strong>
                                        <small>${fmtTime(item.startTime)}</small>
                                    </span>
                                    <span class="history-main">
                                        <strong>${esc(item.doctorFullName)}</strong>
                                        <small>${esc(item.specializationName || '')}</small>
                                    </span>
                                    ${statusBadge(item.status)}
                                    <strong class="history-total">${fmtMoney(item.totalPrice)}</strong>
                                </button>`).join('')}
                        </div>` : emptyState('calendar', 'Приёмов пока нет', 'История появится после первой записи к врачу.')}
                </div>`,
            footer: `
                <button class="btn btn-secondary" type="button" data-dismiss>Закрыть</button>
                <button class="btn btn-danger-soft" type="button" data-delete>${icon('trash', 16)} Удалить</button>
                <button class="btn btn-primary" type="button" data-edit>${icon('edit', 16)} Изменить карточку</button>`,
            onMount: ({ root, close }) => {
                root.querySelector('[data-dismiss]').addEventListener('click', close);
                root.querySelector('[data-edit]').addEventListener('click', () => {
                    close();
                    openForm(patient);
                });
                root.querySelector('[data-delete]').addEventListener('click', () => {
                    close();
                    deletePatient(patient);
                });
                root.querySelectorAll('[data-appointment]').forEach(button => {
                    button.addEventListener('click', () => {
                        const appointment = patientAppointments.find(item => item.id === Number(button.dataset.appointment));
                        if (appointment) {
                            close();
                            openAppointmentDetails(appointment, {
                                role: ctx.role,
                                onChanged: reload
                            });
                        }
                    });
                });
            }
        });
    }

    async function deletePatient(patient) {
        const confirmed = await confirmDialog({
            title: 'Удалить пациента?',
            message: `Карточка «${patientFullName(patient)}» будет удалена. Если у пациента есть приёмы, удаление будет отклонено.`,
            confirmLabel: 'Удалить пациента',
            danger: true
        });
        if (!confirmed) {
            return;
        }
        try {
            await api(`/patients/${patient.id}`, { method: 'DELETE' });
            toast('Карточка пациента удалена');
            await reload();
        } catch (error) {
            toast(mapApiError(error), 'error');
        }
    }

    async function reload() {
        try {
            patients = await api('/patients');
            renderStats();
            draw();
        } catch (error) {
            toast(mapApiError(error), 'error');
        }
    }

    searchInput.addEventListener('input', debounce(() => {
        state.search = searchInput.value;
        state.page = 1;
        draw();
    }, 220));

    container.querySelector('[data-reset]').addEventListener('click', () => {
        state.search = '';
        state.page = 1;
        searchInput.value = '';
        draw();
    });

    tableWrap.addEventListener('click', event => {
        const openButton = event.target.closest('[data-open]');
        const editButton = event.target.closest('[data-edit]');
        const deleteButton = event.target.closest('[data-delete]');

        if (openButton) {
            const patient = patients.find(item => item.id === Number(openButton.dataset.open));
            if (patient) openDetails(patient);
            return;
        }
        if (editButton) {
            const patient = patients.find(item => item.id === Number(editButton.dataset.edit));
            if (patient) openForm(patient);
            return;
        }
        if (deleteButton) {
            const patient = patients.find(item => item.id === Number(deleteButton.dataset.delete));
            if (patient) deletePatient(patient);
            return;
        }

        const row = event.target.closest('[data-patient]');
        if (row) {
            const patient = patients.find(item => item.id === Number(row.dataset.patient));
            if (patient) openDetails(patient);
        }
    });

    tableWrap.addEventListener('keydown', event => {
        if (event.key !== 'Enter') {
            return;
        }
        const row = event.target.closest('[data-patient]');
        if (row) {
            const patient = patients.find(item => item.id === Number(row.dataset.patient));
            if (patient) openDetails(patient);
        }
    });

    pager.addEventListener('click', event => {
        const button = event.target.closest('[data-page]');
        if (!button || button.disabled) {
            return;
        }
        state.page = Number(button.dataset.page);
        draw();
    });

    container.querySelector('[data-new]').addEventListener('click', () => openForm());
    container.querySelector('[data-refresh]').addEventListener('click', reload);

    renderStats();
    draw();

    if (ctx.params.get('new') === '1') {
        openForm();
    }
}
