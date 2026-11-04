import { api } from '../api.js';
import {
    emptyState,
    esc,
    fmtDate,
    fmtMoney,
    icon,
    loadingState,
    mapApiError,
    parseDate,
    plural,
    startOfMonth,
    toISO,
    toast,
    todayISO
} from '../ui.js';

function reportDonut(stats) {
    const total = stats.totalAppointments || 0;
    if (!total) {
        return `<div class="report-donut report-donut-empty"><span>Нет данных</span></div>`;
    }
    const scheduled = (stats.scheduledAppointments / total) * 100;
    const completed = (stats.completedAppointments / total) * 100;
    const cancelled = Math.max(0, 100 - scheduled - completed);
    return `
        <div class="report-donut" style="--scheduled:${scheduled}%;--completed:${completed}%;--cancelled:${cancelled}%">
            <div class="report-donut-center">
                <strong>${total}</strong>
                <span>${plural(total, 'приём', 'приёма', 'приёмов')}</span>
            </div>
        </div>`;
}

function csvCell(value) {
    return `"${String(value ?? '').replace(/"/g, '""')}"`;
}

export async function render(container, ctx) {
    const monthStart = startOfMonth(new Date());
    let from = toISO(monthStart);
    let to = todayISO();
    let doctors = [];
    let appointmentStats = null;
    let doctorStats = null;
    let selectedDoctorId = '';

    container.innerHTML = `
        <div class="page-head">
            <div>
                <span class="eyebrow">Аналитика клиники</span>
                <h1>Отчёты</h1>
                <p>Статистика приёмов и выручка за выбранный период. Доступно только администратору.</p>
            </div>
            <div class="page-actions">
                <button class="btn btn-secondary" type="button" data-print>${icon('printer', 17)} Печать</button>
                <button class="btn btn-primary" type="button" data-export>${icon('download', 17)} Экспорт CSV</button>
            </div>
        </div>

        <section class="card filter-card">
            <form class="filters report-filters" data-report-form>
                <label class="field">
                    <span class="field-label">С даты</span>
                    <input class="input" type="date" name="from" value="${from}" required>
                </label>
                <label class="field">
                    <span class="field-label">По дату</span>
                    <input class="input" type="date" name="to" value="${to}" required>
                </label>
                <label class="field">
                    <span class="field-label">Врач в отчёте</span>
                    <select class="input" name="doctorId">
                        <option value="">Все врачи</option>
                    </select>
                </label>
                <button class="btn btn-primary" type="submit" data-submit>${icon('chart', 17)} Построить отчёт</button>
            </form>
            <div class="report-presets">
                <span class="muted-text">Быстрый выбор:</span>
                <button class="chip-button" type="button" data-preset="month">Текущий месяц</button>
                <button class="chip-button" type="button" data-preset="prev-month">Прошлый месяц</button>
                <button class="chip-button" type="button" data-preset="30">Последние 30 дней</button>
                <button class="chip-button" type="button" data-preset="year">Текущий год</button>
                <span class="form-error inline-error hidden" data-report-error role="alert"></span>
            </div>
        </section>

        <div data-report-content>${loadingState('Считаем статистику…')}</div>`;

    const form = container.querySelector('[data-report-form]');
    const content = container.querySelector('[data-report-content]');
    const errorBox = container.querySelector('[data-report-error]');
    const doctorSelect = form.elements.doctorId;

    try {
        doctors = await api('/doctors');
        doctorSelect.innerHTML = `
            <option value="">Все врачи</option>
            ${doctors.map(doctor => `<option value="${doctor.id}">${esc(doctor.fullName)}</option>`).join('')}`;
    } catch (error) {
        toast(mapApiError(error), 'error');
    }

    async function load() {
        errorBox.classList.add('hidden');
        content.innerHTML = loadingState('Считаем статистику…');
        try {
            const [stats, byDoctor] = await Promise.all([
                api('/reports/appointments', { params: { from, to } }),
                api('/reports/doctors', {
                    params: { from, to, doctorId: selectedDoctorId || undefined }
                })
            ]);
            appointmentStats = stats;
            doctorStats = byDoctor;
            draw();
        } catch (error) {
            content.innerHTML = emptyState('alert', 'Не удалось построить отчёт', mapApiError(error));
        }
    }

    function draw() {
        const stats = appointmentStats;
        const doctorRows = (doctorStats?.doctors || []).slice().sort((a, b) =>
            b.totalAppointments - a.totalAppointments || a.doctorFullName.localeCompare(b.doctorFullName, 'ru')
        );
        const maxTotal = Math.max(1, ...doctorRows.map(item => item.totalAppointments));

        content.innerHTML = `
            <div class="report-period">
                <span class="eyebrow">Период отчёта</span>
                <strong>${esc(fmtDate(stats.from))} — ${esc(fmtDate(stats.to))}</strong>
            </div>

            <section class="stats-grid stats-grid-4 report-stats">
                <article class="stat-card stat-teal">
                    <span class="stat-icon">${icon('calendar', 22)}</span>
                    <div class="stat-body">
                        <span class="stat-label">Всего приёмов</span>
                        <strong class="stat-value">${stats.totalAppointments}</strong>
                        <span class="stat-hint">включая все статусы</span>
                    </div>
                </article>
                <article class="stat-card stat-blue">
                    <span class="stat-icon">${icon('clock', 22)}</span>
                    <div class="stat-body">
                        <span class="stat-label">Запланировано</span>
                        <strong class="stat-value">${stats.scheduledAppointments}</strong>
                        <span class="stat-hint">ожидают проведения</span>
                    </div>
                </article>
                <article class="stat-card stat-mint">
                    <span class="stat-icon">${icon('checkCircle', 22)}</span>
                    <div class="stat-body">
                        <span class="stat-label">Завершено</span>
                        <strong class="stat-value">${stats.completedAppointments}</strong>
                        <span class="stat-hint">${stats.cancelledAppointments} отменено</span>
                    </div>
                </article>
                <article class="stat-card stat-amber">
                    <span class="stat-icon">${icon('wallet', 22)}</span>
                    <div class="stat-body">
                        <span class="stat-label">Выручка</span>
                        <strong class="stat-value stat-value-money">${fmtMoney(stats.completedRevenue)}</strong>
                        <span class="stat-hint">по завершённым приёмам</span>
                    </div>
                </article>
            </section>

            <div class="report-grid">
                <section class="card report-chart-card">
                    <div class="card-head">
                        <div>
                            <span class="eyebrow">Структура</span>
                            <h2>Статусы приёмов</h2>
                        </div>
                    </div>
                    <div class="report-chart-body">
                        ${reportDonut(stats)}
                        <div class="report-legend">
                            <div class="report-legend-row">
                                <span><i class="dot dot-teal"></i> Запланировано</span>
                                <strong>${stats.scheduledAppointments}</strong>
                            </div>
                            <div class="report-legend-row">
                                <span><i class="dot dot-green"></i> Завершено</span>
                                <strong>${stats.completedAppointments}</strong>
                            </div>
                            <div class="report-legend-row">
                                <span><i class="dot dot-red"></i> Отменено</span>
                                <strong>${stats.cancelledAppointments}</strong>
                            </div>
                        </div>
                    </div>
                    <div class="report-note">
                        Выручка считается только по завершённым приёмам и включает стоимость всех привязанных услуг.
                    </div>
                </section>

                <section class="card report-summary-card">
                    <div class="card-head">
                        <div>
                            <span class="eyebrow">Сводка</span>
                            <h2>Показатели периода</h2>
                        </div>
                    </div>
                    <div class="report-summary-list">
                        <div><span>Средняя выручка на завершённый приём</span><strong>${fmtMoney(stats.completedAppointments ? Number(stats.completedRevenue) / stats.completedAppointments : 0)}</strong></div>
                        <div><span>Доля завершённых приёмов</span><strong>${stats.totalAppointments ? Math.round(stats.completedAppointments / stats.totalAppointments * 100) : 0}%</strong></div>
                        <div><span>Доля отменённых приёмов</span><strong>${stats.totalAppointments ? Math.round(stats.cancelledAppointments / stats.totalAppointments * 100) : 0}%</strong></div>
                        <div><span>Врачей в отчёте</span><strong>${doctorRows.length}</strong></div>
                    </div>
                </section>
            </div>

            <section class="card table-card report-table-card">
                <div class="card-head report-table-head">
                    <div>
                        <span class="eyebrow">По врачам</span>
                        <h2>Статистика специалистов</h2>
                    </div>
                    <span class="muted-text">${selectedDoctorId ? 'Выбран один врач' : 'Все врачи, включая нулевые показатели'}</span>
                </div>
                ${doctorRows.length ? `
                    <div class="table-wrap">
                        <table class="data-table report-table">
                            <thead>
                                <tr>
                                    <th>Врач</th>
                                    <th>Специализация</th>
                                    <th class="align-center">Всего</th>
                                    <th class="align-center">План</th>
                                    <th class="align-center">Завершено</th>
                                    <th class="align-center">Отменено</th>
                                    <th class="align-right">Выручка</th>
                                    <th class="report-bar-col">Распределение</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${doctorRows.map(item => `
                                    <tr>
                                        <td><strong>${esc(item.doctorFullName)}</strong></td>
                                        <td>${esc(item.specializationName)}</td>
                                        <td class="align-center">${item.totalAppointments}</td>
                                        <td class="align-center">${item.scheduledAppointments}</td>
                                        <td class="align-center">${item.completedAppointments}</td>
                                        <td class="align-center">${item.cancelledAppointments}</td>
                                        <td class="align-right table-money">${fmtMoney(item.completedRevenue)}</td>
                                        <td class="report-bar-col">
                                            <span class="report-bar"><i style="width:${Math.round(item.totalAppointments / maxTotal * 100)}%"></i></span>
                                        </td>
                                    </tr>`).join('')}
                            </tbody>
                        </table>
                    </div>` : emptyState('doctor', 'В отчёте нет врачей', 'Добавьте врачей, чтобы увидеть статистику.')}
            </section>`;
    }

    function setPeriod(fromDate, toDate) {
        from = fromDate;
        to = toDate;
        form.elements.from.value = from;
        form.elements.to.value = to;
    }

    form.addEventListener('submit', async event => {
        event.preventDefault();
        const nextFrom = form.elements.from.value;
        const nextTo = form.elements.to.value;
        if (!nextFrom || !nextTo) {
            errorBox.textContent = 'Укажите обе даты периода.';
            errorBox.classList.remove('hidden');
            return;
        }
        if (nextFrom > nextTo) {
            errorBox.textContent = 'Дата начала не может быть позже даты окончания.';
            errorBox.classList.remove('hidden');
            return;
        }
        errorBox.classList.add('hidden');
        from = nextFrom;
        to = nextTo;
        selectedDoctorId = doctorSelect.value;
        await load();
    });

    container.querySelectorAll('[data-preset]').forEach(button => {
        button.addEventListener('click', () => {
            const preset = button.dataset.preset;
            const now = new Date();
            if (preset === 'month') {
                setPeriod(toISO(startOfMonth(now)), todayISO());
            } else if (preset === 'prev-month') {
                const previous = new Date(now.getFullYear(), now.getMonth() - 1, 1);
                const end = new Date(now.getFullYear(), now.getMonth(), 0);
                setPeriod(toISO(previous), toISO(end));
            } else if (preset === '30') {
                const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 29);
                setPeriod(toISO(start), todayISO());
            } else if (preset === 'year') {
                setPeriod(`${now.getFullYear()}-01-01`, todayISO());
            }
            form.requestSubmit();
        });
    });

    container.querySelector('[data-print]').addEventListener('click', () => window.print());

    container.querySelector('[data-export]').addEventListener('click', () => {
        if (!appointmentStats || !doctorStats) {
            toast('Сначала постройте отчёт', 'info');
            return;
        }
        const rows = [
            ['Период', `${appointmentStats.from} — ${appointmentStats.to}`],
            ['Всего приёмов', appointmentStats.totalAppointments],
            ['Запланировано', appointmentStats.scheduledAppointments],
            ['Завершено', appointmentStats.completedAppointments],
            ['Отменено', appointmentStats.cancelledAppointments],
            ['Выручка', appointmentStats.completedRevenue],
            [],
            ['Врач', 'Специализация', 'Всего', 'Запланировано', 'Завершено', 'Отменено', 'Выручка'],
            ...(doctorStats.doctors || []).map(item => [
                item.doctorFullName,
                item.specializationName,
                item.totalAppointments,
                item.scheduledAppointments,
                item.completedAppointments,
                item.cancelledAppointments,
                item.completedRevenue
            ])
        ];
        const csv = '\uFEFF' + rows.map(row => row.map(csvCell).join(';')).join('\r\n');
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `report-${appointmentStats.from}-${appointmentStats.to}.csv`;
        link.click();
        URL.revokeObjectURL(url);
        toast('Отчёт выгружен в CSV');
    });

    await load();
}
