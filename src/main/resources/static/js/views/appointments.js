import { api } from '../api.js';
import {
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
    plural,
    statusBadge,
    statusLabel,
    toast,
    todayISO
} from '../ui.js';
import {
    openAppointmentDetails,
    openAppointmentForm
} from '../appointment-shared.js';

export async function render(container, ctx) {
    if (ctx.role === 'DOCTOR') {
        await renderDoctorAppointments(container, ctx);
        return;
    }
    await renderClinicAppointments(container, ctx);
}

function statusSelect(selected = '') {
    return `
        <option value="">Все статусы</option>
        <option value="SCHEDULED" ${selected === 'SCHEDULED' ? 'selected' : ''}>Запланирован</option>
        <option value="COMPLETED" ${selected === 'COMPLETED' ? 'selected' : ''}>Завершён</option>
        <option value="CANCELLED" ${selected === 'CANCELLED' ? 'selected' : ''}>Отменён</option>`;
}

function appointmentTableRows(items, { role, showDoctor = true }) {
    return items.map(item => `
        <tr class="table-row-clickable" data-appointment="${item.id}" tabindex="0">
            <td>
                <div class="table-date">
                    <strong>${fmtShortDate(item.appointmentDate)}</strong>
                    <span>${fmtTime(item.startTime)}–${fmtTime(item.endTime)}</span>
                </div>
            </td>
            <td>
                <div class="table-person">
                    <span class="avatar avatar-xs">${esc((item.patientFullName || '')[0] || '')}</span>
                    <strong>${esc(item.patientFullName)}</strong>
                </div>
            </td>
            ${showDoctor ? `
                <td>
                    <div class="table-stack">
                        <strong>${esc(item.doctorFullName)}</strong>
                        <span>${esc(item.specializationName || '')}</span>
                    </div>
                </td>` : `
                <td>
                    <div class="table-stack">
                        <span>${esc(item.reason || 'Приём')}</span>
                        <small class="muted-text">${esc(item.notes || '')}</small>
                    </div>
                </td>`}
            <td>${statusBadge(item.status)}</td>
            <td class="table-money">${fmtMoney(item.totalPrice)}</td>
            <td class="table-actions">
                <button class="btn btn-secondary btn-sm" type="button" data-open="${item.id}">
                    Открыть ${icon('chevronRight', 14)}
                </button>
            </td>
        </tr>`).join('');
}

async function renderClinicAppointments(container, ctx) {
    container.innerHTML = loadingState('Загружаем записи на приём…');

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
        container.innerHTML = emptyState('alert', 'Не удалось загрузить записи', mapApiError(error));
        return;
    }

    const state = {
        search: '',
        status: '',
        doctorId: '',
        date: '',
        page: 1,
        pageSize: 12
    };

    container.innerHTML = `
        <div class="page-head">
            <div>
                <span class="eyebrow">Приёмы и визиты</span>
                <h1>Записи на приём</h1>
                <p>Управляйте расписанием, статусами и стоимостью приёмов.</p>
            </div>
            <div class="page-actions">
                <button class="btn btn-secondary" type="button" data-refresh>${icon('refresh', 17)} Обновить</button>
                <button class="btn btn-primary" type="button" data-new>${icon('plus', 18)} Новая запись</button>
            </div>
        </div>

        <section class="card filter-card">
            <div class="filters">
                <label class="field field-search">
                    <span class="field-label">Поиск</span>
                    <span class="input-icon-wrap">
                        ${icon('search', 17)}
                        <input class="input" type="search" data-search placeholder="Пациент, врач или причина">
                    </span>
                </label>
                <label class="field">
                    <span class="field-label">Дата</span>
                    <input class="input" type="date" data-date>
                </label>
                <label class="field">
                    <span class="field-label">Врач</span>
                    <select class="input" data-doctor>
                        <option value="">Все врачи</option>
                        ${doctors.map(doctor => `<option value="${doctor.id}">${esc(doctor.fullName)}</option>`).join('')}
                    </select>
                </label>
                <label class="field">
                    <span class="field-label">Статус</span>
                    <select class="input" data-status>${statusSelect()}</select>
                </label>
                <button class="btn btn-ghost filter-reset" type="button" data-reset>${icon('close', 16)} Сбросить</button>
            </div>
            <div class="filter-meta">
                <span data-result-count></span>
                <button class="text-link" type="button" data-today>Только сегодня ${icon('arrowRight', 14)}</button>
            </div>
        </section>

        <section class="card table-card">
            <div class="table-wrap" data-table></div>
            <div class="pagination" data-pager></div>
        </section>`;

    const searchInput = container.querySelector('[data-search]');
    const dateInput = container.querySelector('[data-date]');
    const doctorSelectElement = container.querySelector('[data-doctor]');
    const statusSelectElement = container.querySelector('[data-status]');
    const tableWrap = container.querySelector('[data-table]');
    const pager = container.querySelector('[data-pager]');
    const resultCount = container.querySelector('[data-result-count]');

    function filtered() {
        const search = state.search.trim().toLocaleLowerCase('ru');
        return appointments
            .filter(item => {
                if (state.status && item.status !== state.status) return false;
                if (state.date && item.appointmentDate !== state.date) return false;
                if (state.doctorId && item.doctorId !== Number(state.doctorId)) return false;
                if (!search) return true;
                const haystack = [
                    item.patientFullName,
                    item.doctorFullName,
                    item.specializationName,
                    item.reason,
                    item.notes
                ].filter(Boolean).join(' ').toLocaleLowerCase('ru');
                return haystack.includes(search);
            })
            .sort((a, b) =>
                `${b.appointmentDate}T${b.startTime}`.localeCompare(`${a.appointmentDate}T${a.startTime}`)
            );
    }

    function draw() {
        const items = filtered();
        const totalPages = Math.max(1, Math.ceil(items.length / state.pageSize));
        state.page = Math.min(state.page, totalPages);
        const start = (state.page - 1) * state.pageSize;
        const pageItems = items.slice(start, start + state.pageSize);

        resultCount.textContent = items.length
            ? `Найдено ${items.length} ${plural(items.length, 'запись', 'записи', 'записей')}`
            : 'Записи не найдены';

        if (!pageItems.length) {
            tableWrap.innerHTML = emptyState(
                'clipboard',
                'Записей не найдено',
                'Измените фильтры или создайте новую запись на приём.',
                `<button class="btn btn-primary" type="button" data-empty-new>${icon('plus', 16)} Новая запись</button>`
            );
            pager.innerHTML = '';
            tableWrap.querySelector('[data-empty-new]')?.addEventListener('click', openNew);
            return;
        }

        tableWrap.innerHTML = `
            <table class="data-table">
                <thead>
                    <tr>
                        <th>Дата и время</th>
                        <th>Пациент</th>
                        <th>Врач</th>
                        <th>Статус</th>
                        <th class="align-right">Стоимость</th>
                        <th></th>
                    </tr>
                </thead>
                <tbody>${appointmentTableRows(pageItems, { role: ctx.role })}</tbody>
            </table>`;

        pager.innerHTML = `
            <span class="pager-info">${start + 1}–${Math.min(start + state.pageSize, items.length)} из ${items.length}</span>
            <div class="pager-controls">
                <button class="icon-btn" type="button" data-page="${state.page - 1}" ${state.page === 1 ? 'disabled' : ''} aria-label="Предыдущая страница">${icon('chevronLeft')}</button>
                <span class="pager-current">Страница ${state.page} из ${totalPages}</span>
                <button class="icon-btn" type="button" data-page="${state.page + 1}" ${state.page === totalPages ? 'disabled' : ''} aria-label="Следующая страница">${icon('chevronRight')}</button>
            </div>`;
    }

    function openNew() {
        openAppointmentForm({
            doctors,
            patients,
            onSaved: async () => {
                await reload();
            }
        });
    }

    async function reload() {
        try {
            [appointments, doctors, patients] = await Promise.all([
                api('/appointments'),
                api('/doctors'),
                api('/patients')
            ]);
            draw();
        } catch (error) {
            toast(mapApiError(error), 'error');
        }
    }

    function openDetails(id) {
        const appointment = appointments.find(item => item.id === Number(id));
        if (!appointment) {
            return;
        }
        openAppointmentDetails(appointment, {
            role: ctx.role,
            onChanged: reload
        });
    }

    searchInput.addEventListener('input', debounce(() => {
        state.search = searchInput.value;
        state.page = 1;
        draw();
    }, 220));

    dateInput.addEventListener('change', () => {
        state.date = dateInput.value;
        state.page = 1;
        draw();
    });
    doctorSelectElement.addEventListener('change', () => {
        state.doctorId = doctorSelectElement.value;
        state.page = 1;
        draw();
    });
    statusSelectElement.addEventListener('change', () => {
        state.status = statusSelectElement.value;
        state.page = 1;
        draw();
    });

    container.querySelector('[data-reset]').addEventListener('click', () => {
        state.search = '';
        state.status = '';
        state.doctorId = '';
        state.date = '';
        state.page = 1;
        searchInput.value = '';
        dateInput.value = '';
        doctorSelectElement.value = '';
        statusSelectElement.value = '';
        draw();
    });

    container.querySelector('[data-today]').addEventListener('click', () => {
        state.date = todayISO();
        state.page = 1;
        dateInput.value = state.date;
        draw();
    });

    tableWrap.addEventListener('click', event => {
        const openButton = event.target.closest('[data-open]');
        if (openButton) {
            openDetails(openButton.dataset.open);
            return;
        }
        const row = event.target.closest('[data-appointment]');
        if (row && !event.target.closest('button')) {
            openDetails(row.dataset.appointment);
        }
    });

    tableWrap.addEventListener('keydown', event => {
        if (event.key !== 'Enter') {
            return;
        }
        const row = event.target.closest('[data-appointment]');
        if (row) {
            openDetails(row.dataset.appointment);
        }
    });

    pager.addEventListener('click', event => {
        const button = event.target.closest('[data-page]');
        if (!button || button.disabled) {
            return;
        }
        state.page = Number(button.dataset.page);
        draw();
        container.querySelector('.table-card').scrollIntoView({ behavior: 'smooth', block: 'start' });
    });

    container.querySelector('[data-new]').addEventListener('click', openNew);
    container.querySelector('[data-refresh]').addEventListener('click', reload);

    draw();
}

async function renderDoctorAppointments(container, ctx) {
    container.innerHTML = loadingState('Загружаем ваши приёмы…');

    let appointments;
    try {
        appointments = await api('/doctor/appointments');
    } catch (error) {
        const message = error?.status === 404
            ? 'Для вашей учётной записи ещё не создан профиль врача. Обратитесь к администратору.'
            : mapApiError(error);
        container.innerHTML = emptyState('doctor', 'Не удалось открыть приёмы', message);
        return;
    }

    const state = {
        search: '',
        status: '',
        date: '',
        page: 1,
        pageSize: 14
    };

    container.innerHTML = `
        <div class="page-head">
            <div>
                <span class="eyebrow">Кабинет врача</span>
                <h1>Мои приёмы</h1>
                <p>Здесь отображаются только ваши записи. Данные доступны для просмотра.</p>
            </div>
            <div class="page-actions">
                <button class="btn btn-secondary" type="button" data-refresh>${icon('refresh', 17)} Обновить</button>
            </div>
        </div>

        <section class="mini-stats" data-mini-stats></section>

        <section class="card filter-card">
            <div class="filters">
                <label class="field field-search">
                    <span class="field-label">Поиск</span>
                    <span class="input-icon-wrap">
                        ${icon('search', 17)}
                        <input class="input" type="search" data-search placeholder="Пациент или причина">
                    </span>
                </label>
                <label class="field">
                    <span class="field-label">Дата</span>
                    <input class="input" type="date" data-date>
                </label>
                <label class="field">
                    <span class="field-label">Статус</span>
                    <select class="input" data-status>${statusSelect()}</select>
                </label>
                <button class="btn btn-ghost filter-reset" type="button" data-reset>${icon('close', 16)} Сбросить</button>
            </div>
            <div class="filter-meta">
                <span data-result-count></span>
                <button class="text-link" type="button" data-today>Только сегодня ${icon('arrowRight', 14)}</button>
            </div>
        </section>

        <section class="card table-card">
            <div class="table-wrap" data-table></div>
            <div class="pagination" data-pager></div>
        </section>`;

    const searchInput = container.querySelector('[data-search]');
    const dateInput = container.querySelector('[data-date]');
    const statusSelectElement = container.querySelector('[data-status]');
    const tableWrap = container.querySelector('[data-table]');
    const pager = container.querySelector('[data-pager]');
    const resultCount = container.querySelector('[data-result-count]');
    const miniStats = container.querySelector('[data-mini-stats]');

    function renderStats() {
        const counts = {
            total: appointments.length,
            scheduled: appointments.filter(item => item.status === 'SCHEDULED').length,
            completed: appointments.filter(item => item.status === 'COMPLETED').length,
            cancelled: appointments.filter(item => item.status === 'CANCELLED').length
        };
        miniStats.innerHTML = `
            <span class="mini-stat"><strong>${counts.total}</strong> всего</span>
            <span class="mini-stat mini-stat-scheduled"><strong>${counts.scheduled}</strong> запланировано</span>
            <span class="mini-stat mini-stat-completed"><strong>${counts.completed}</strong> завершено</span>
            <span class="mini-stat mini-stat-cancelled"><strong>${counts.cancelled}</strong> отменено</span>`;
    }

    function filtered() {
        const search = state.search.trim().toLocaleLowerCase('ru');
        return appointments
            .filter(item => {
                if (state.status && item.status !== state.status) return false;
                if (state.date && item.appointmentDate !== state.date) return false;
                if (!search) return true;
                return [item.patientFullName, item.reason, item.notes]
                    .filter(Boolean)
                    .join(' ')
                    .toLocaleLowerCase('ru')
                    .includes(search);
            })
            .sort((a, b) =>
                `${b.appointmentDate}T${b.startTime}`.localeCompare(`${a.appointmentDate}T${a.startTime}`)
            );
    }

    function draw() {
        const items = filtered();
        const totalPages = Math.max(1, Math.ceil(items.length / state.pageSize));
        state.page = Math.min(state.page, totalPages);
        const start = (state.page - 1) * state.pageSize;
        const pageItems = items.slice(start, start + state.pageSize);

        resultCount.textContent = items.length
            ? `Найдено ${items.length} ${plural(items.length, 'приём', 'приёма', 'приёмов')}`
            : 'Приёмы не найдены';

        if (!pageItems.length) {
            tableWrap.innerHTML = emptyState('calendar', 'Приёмов не найдено', 'Измените фильтры или выберите другую дату.');
            pager.innerHTML = '';
            return;
        }

        tableWrap.innerHTML = `
            <table class="data-table">
                <thead>
                    <tr>
                        <th>Дата и время</th>
                        <th>Пациент</th>
                        <th>Причина</th>
                        <th>Статус</th>
                        <th class="align-right">Стоимость</th>
                        <th></th>
                    </tr>
                </thead>
                <tbody>${appointmentTableRows(pageItems, { role: 'DOCTOR', showDoctor: false })}</tbody>
            </table>`;

        pager.innerHTML = `
            <span class="pager-info">${start + 1}–${Math.min(start + state.pageSize, items.length)} из ${items.length}</span>
            <div class="pager-controls">
                <button class="icon-btn" type="button" data-page="${state.page - 1}" ${state.page === 1 ? 'disabled' : ''} aria-label="Предыдущая страница">${icon('chevronLeft')}</button>
                <span class="pager-current">Страница ${state.page} из ${totalPages}</span>
                <button class="icon-btn" type="button" data-page="${state.page + 1}" ${state.page === totalPages ? 'disabled' : ''} aria-label="Следующая страница">${icon('chevronRight')}</button>
            </div>`;
    }

    async function reload() {
        try {
            appointments = await api('/doctor/appointments');
            renderStats();
            draw();
        } catch (error) {
            toast(mapApiError(error), 'error');
        }
    }

    function openDetails(id) {
        const appointment = appointments.find(item => item.id === Number(id));
        if (appointment) {
            openAppointmentDetails(appointment, {
                role: 'DOCTOR',
                onChanged: reload
            });
        }
    }

    searchInput.addEventListener('input', debounce(() => {
        state.search = searchInput.value;
        state.page = 1;
        draw();
    }, 220));
    dateInput.addEventListener('change', () => {
        state.date = dateInput.value;
        state.page = 1;
        draw();
    });
    statusSelectElement.addEventListener('change', () => {
        state.status = statusSelectElement.value;
        state.page = 1;
        draw();
    });

    container.querySelector('[data-reset]').addEventListener('click', () => {
        state.search = '';
        state.status = '';
        state.date = '';
        state.page = 1;
        searchInput.value = '';
        dateInput.value = '';
        statusSelectElement.value = '';
        draw();
    });
    container.querySelector('[data-today]').addEventListener('click', () => {
        state.date = todayISO();
        state.page = 1;
        dateInput.value = state.date;
        draw();
    });

    tableWrap.addEventListener('click', event => {
        const openButton = event.target.closest('[data-open]');
        if (openButton) {
            openDetails(openButton.dataset.open);
            return;
        }
        const row = event.target.closest('[data-appointment]');
        if (row && !event.target.closest('button')) {
            openDetails(row.dataset.appointment);
        }
    });
    tableWrap.addEventListener('keydown', event => {
        if (event.key === 'Enter') {
            const row = event.target.closest('[data-appointment]');
            if (row) {
                openDetails(row.dataset.appointment);
            }
        }
    });
    pager.addEventListener('click', event => {
        const button = event.target.closest('[data-page]');
        if (!button || button.disabled) {
            return;
        }
        state.page = Number(button.dataset.page);
        draw();
        container.querySelector('.table-card').scrollIntoView({ behavior: 'smooth', block: 'start' });
    });

    container.querySelector('[data-refresh]').addEventListener('click', reload);

    renderStats();
    draw();
}
