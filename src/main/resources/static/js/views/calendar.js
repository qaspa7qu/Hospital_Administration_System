import { api } from '../api.js';
import {
    addDays,
    emptyState,
    esc,
    fmtDate,
    fmtMonth,
    fmtTime,
    icon,
    loadingState,
    mapApiError,
    minutesFromTime,
    monthMatrix,
    parseDate,
    plural,
    startOfMonth,
    statusBadge,
    timeFromMinutes,
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
    openAppointmentForm
} from '../appointment-shared.js';

export async function render(container, ctx) {
    container.innerHTML = loadingState('Загружаем календарь клиники…');

    let doctors;
    let patients;
    let appointments;
    try {
        [doctors, patients, appointments] = await Promise.all([
            api('/doctors'),
            api('/patients'),
            api('/appointments')
        ]);
    } catch (error) {
        container.innerHTML = emptyState('alert', 'Не удалось загрузить календарь', mapApiError(error));
        return;
    }

    if (!doctors.length) {
        container.innerHTML = emptyState(
            'doctor',
            'В клинике пока нет врачей',
            'Добавьте профиль врача и настройте рабочее расписание, чтобы открыть календарь.',
            ctx.role === 'ADMIN'
                ? `<a class="btn btn-primary" href="#/doctors">Добавить врача</a>`
                : ''
        );
        return;
    }

    const requestedDate = ctx.params.get('date');
    const requestedDoctor = ctx.params.get('doctorId');
    let selectedDate = parseDate(requestedDate) || new Date();
    let viewMonth = startOfMonth(selectedDate);
    let selectedDoctorId = requestedDoctor
        ? Number(requestedDoctor)
        : (doctors.find(doctor => doctor.active)?.id ?? doctors[0]?.id ?? null);

    if (!doctors.some(doctor => doctor.id === selectedDoctorId)) {
        selectedDoctorId = doctors[0]?.id ?? null;
    }

    const scheduleCache = new Map();
    let slotsRequestId = 0;

    container.innerHTML = `
        <div class="page-head">
            <div>
                <span class="eyebrow">Расписание клиники</span>
                <h1>Календарь приёмов</h1>
                <p>Выберите дату и врача — свободные слоты рассчитываются по рабочему расписанию.</p>
            </div>
            <div class="page-actions">
                <button class="btn btn-secondary" type="button" data-refresh>${icon('refresh', 17)} Обновить</button>
                <button class="btn btn-primary" type="button" data-new>${icon('plus', 18)} Новая запись</button>
            </div>
        </div>

        <div class="calendar-layout">
            <section class="card calendar-card">
                <div class="calendar-toolbar">
                    <div class="calendar-title">
                        <span class="eyebrow">Обзор месяца</span>
                        <h2 data-month-title></h2>
                    </div>
                    <div class="calendar-nav">
                        <button class="icon-btn" type="button" data-prev aria-label="Предыдущий месяц">${icon('chevronLeft')}</button>
                        <button class="btn btn-secondary btn-sm" type="button" data-today>Сегодня</button>
                        <button class="icon-btn" type="button" data-next aria-label="Следующий месяц">${icon('chevronRight')}</button>
                    </div>
                </div>

                <div class="calendar-weekdays">
                    ${WEEKDAYS_SHORT.map(day => `<span>${day}</span>`).join('')}
                </div>
                <div class="calendar-grid" data-calendar-grid></div>

                <div class="calendar-legend">
                    <span><i class="dot dot-teal"></i> запланирован</span>
                    <span><i class="dot dot-green"></i> завершён</span>
                    <span><i class="dot dot-red"></i> отменён</span>
                    <span class="calendar-legend-hint">Число внутри дня — количество приёмов</span>
                </div>
            </section>

            <aside class="card day-panel">
                <div class="day-panel-head">
                    <div>
                        <span class="eyebrow" data-day-weekday></span>
                        <h2 data-day-title></h2>
                    </div>
                    <span class="day-counter" data-day-counter></span>
                </div>

                <label class="field doctor-picker">
                    <span class="field-label">Врач</span>
                    <select class="input" data-doctor-select>
                        <option value="">Все врачи</option>
                        ${doctors.map(doctor => `
                            <option value="${doctor.id}" ${doctor.id === selectedDoctorId ? 'selected' : ''}>
                                ${esc(doctor.fullName)} · ${esc(doctor.specializationName)}
                            </option>`).join('')}
                    </select>
                </label>

                <div class="slots-section" data-slots></div>

                <div class="day-appointments">
                    <div class="section-mini-head">
                        <span>Записи на день</span>
                        <span data-day-count></span>
                    </div>
                    <div class="appointment-list" data-day-list></div>
                </div>
            </aside>
        </div>`;

    const monthTitle = container.querySelector('[data-month-title]');
    const calendarGrid = container.querySelector('[data-calendar-grid]');
    const dayWeekday = container.querySelector('[data-day-weekday]');
    const dayTitle = container.querySelector('[data-day-title]');
    const dayCounter = container.querySelector('[data-day-counter]');
    const slotsContainer = container.querySelector('[data-slots]');
    const dayList = container.querySelector('[data-day-list]');
    const dayCount = container.querySelector('[data-day-count]');
    const doctorSelect = container.querySelector('[data-doctor-select]');

    function appointmentsForDate(iso, doctorId = null) {
        return appointments
            .filter(item => item.appointmentDate === iso && (!doctorId || item.doctorId === doctorId))
            .sort((a, b) => a.startTime.localeCompare(b.startTime));
    }

    function renderCalendar() {
        monthTitle.textContent = fmtMonth(toISO(viewMonth));
        const today = todayISO();
        const selectedISO = toISO(selectedDate);

        calendarGrid.innerHTML = monthMatrix(viewMonth).map(day => {
            const iso = toISO(day);
            const dayAppointments = appointmentsForDate(iso);
            const outside = day.getMonth() !== viewMonth.getMonth();
            const isToday = iso === today;
            const isSelected = iso === selectedISO;
            const scheduled = dayAppointments.filter(item => item.status === 'SCHEDULED').length;
            const completed = dayAppointments.filter(item => item.status === 'COMPLETED').length;
            const cancelled = dayAppointments.filter(item => item.status === 'CANCELLED').length;

            return `
                <button class="cal-day ${outside ? 'is-outside' : ''} ${isToday ? 'is-today' : ''} ${isSelected ? 'is-selected' : ''}"
                        type="button" data-date="${iso}" aria-label="${esc(fmtDate(iso))}, ${dayAppointments.length} ${plural(dayAppointments.length, 'приём', 'приёма', 'приёмов')}">
                    <span class="cal-day-number">${day.getDate()}</span>
                    ${dayAppointments.length ? `
                        <span class="cal-day-count">${dayAppointments.length}</span>
                        <span class="cal-day-dots">
                            ${scheduled ? '<i class="dot dot-teal"></i>' : ''}
                            ${completed ? '<i class="dot dot-green"></i>' : ''}
                            ${cancelled ? '<i class="dot dot-red"></i>' : ''}
                        </span>` : '<span class="cal-day-free">свободно</span>'}
                </button>`;
        }).join('');
    }

    async function loadSchedules(doctorId) {
        if (!scheduleCache.has(doctorId)) {
            scheduleCache.set(doctorId, api(`/doctors/${doctorId}/schedule`));
        }
        return scheduleCache.get(doctorId);
    }

    async function renderDayPanel() {
        const iso = toISO(selectedDate);
        const selectedDoctor = doctors.find(doctor => doctor.id === selectedDoctorId) || null;
        const dayAppointments = appointmentsForDate(iso, selectedDoctor?.id || null);
        const allDayAppointments = appointmentsForDate(iso);

        dayWeekday.textContent = WEEKDAYS_FULL[WEEKDAY_ENUM[selectedDate.getDay()]];
        dayTitle.textContent = fmtDate(iso);
        dayCounter.textContent = `${allDayAppointments.length} ${plural(allDayAppointments.length, 'приём', 'приёма', 'приёмов')}`;
        dayCount.textContent = selectedDoctor
            ? `${dayAppointments.length} у ${selectedDoctor.lastName}`
            : `${allDayAppointments.length} всего`;

        dayList.innerHTML = dayAppointments.length
            ? dayAppointments.map(item => appointmentRow(item)).join('')
            : emptyState(
                'calendar',
                selectedDoctor ? 'У врача нет записей' : 'На этот день записей нет',
                'Выберите свободный слот, чтобы записать пациента.'
            );

        const requestId = ++slotsRequestId;

        if (!selectedDoctor) {
            slotsContainer.innerHTML = `
                <div class="section-mini-head"><span>Свободное время</span></div>
                ${emptyState('doctor', 'Выберите врача', 'Свободные слоты рассчитываются для конкретного врача.')}
                <div class="doctor-quick-pick">
                    ${doctors.filter(doctor => doctor.active).map(doctor => `
                        <button class="chip-button" type="button" data-pick-doctor="${doctor.id}">
                            ${esc(doctor.lastName)} ${esc(doctor.firstName?.[0] || '')}. · ${esc(doctor.specializationName)}
                        </button>`).join('')}
                </div>`;
            return;
        }

        slotsContainer.innerHTML = `
            <div class="section-mini-head">
                <span>Свободное время</span>
                <small>${esc(doctorLabel(selectedDoctor))}</small>
            </div>
            <div class="slot-loading"><span class="spinner"></span> Считаем слоты…</div>`;

        try {
            const schedules = await loadSchedules(selectedDoctor.id);
            if (requestId !== slotsRequestId) {
                return;
            }

            const day = WEEKDAY_ENUM[selectedDate.getDay()];
            const daySchedules = schedules.filter(schedule => schedule.active && schedule.dayOfWeek === day);
            const occupied = allDayAppointments.filter(item =>
                item.status !== 'CANCELLED' && item.doctorId === selectedDoctor.id
            );
            const now = new Date();
            const nowMinutes = now.getHours() * 60 + now.getMinutes();
            const isToday = iso === todayISO();

            const slots = new Map();
            daySchedules.forEach(schedule => {
                const start = minutesFromTime(schedule.startTime);
                const end = minutesFromTime(schedule.endTime);
                const duration = Number(schedule.slotDurationMinutes) || 30;
                for (let cursor = start; cursor + duration <= end; cursor += duration) {
                    const slotStart = timeFromMinutes(cursor);
                    if (!slots.has(slotStart)) {
                        slots.set(slotStart, {
                            start: slotStart,
                            end: timeFromMinutes(cursor + duration),
                            startMinutes: cursor,
                            endMinutes: cursor + duration
                        });
                    }
                }
            });

            const ordered = [...slots.values()].sort((a, b) => a.startMinutes - b.startMinutes);
            const annotated = ordered.map(slot => {
                const busy = occupied.some(item => {
                    const itemStart = minutesFromTime(item.startTime);
                    const itemEnd = minutesFromTime(item.endTime);
                    return itemStart < slot.endMinutes && itemEnd > slot.startMinutes;
                });
                return {
                    ...slot,
                    busy,
                    past: isToday && slot.startMinutes <= nowMinutes
                };
            });
            const freeCount = annotated.filter(slot => !slot.busy && !slot.past).length;

            const groups = [
                { label: 'Утро', from: 0, to: 12 * 60 },
                { label: 'День', from: 12 * 60, to: 17 * 60 },
                { label: 'Вечер', from: 17 * 60, to: 24 * 60 }
            ];

            const groupHtml = groups.map(group => {
                const groupSlots = annotated.filter(slot => slot.startMinutes >= group.from && slot.startMinutes < group.to);
                if (!groupSlots.length) {
                    return '';
                }
                return `
                    <div class="slot-group">
                        <span class="slot-group-label">${group.label}</span>
                        <div class="slot-grid">
                            ${groupSlots.map(slot => {
                                const classes = ['slot'];
                                let title = `${slot.start}–${slot.end}`;
                                let disabled = false;
                                if (slot.busy) {
                                    classes.push('slot-busy');
                                    title += ' · занято';
                                    disabled = true;
                                } else if (slot.past) {
                                    classes.push('slot-past');
                                    title += ' · время прошло';
                                    disabled = true;
                                } else {
                                    classes.push('slot-free');
                                    title += ' · свободно';
                                }
                                return `<button class="${classes.join(' ')}" type="button"
                                            data-slot="${slot.start}" title="${esc(title)}"
                                            ${disabled ? 'disabled' : ''}>
                                            ${slot.start}<small>${slot.end}</small>
                                        </button>`;
                            }).join('')}
                        </div>
                    </div>`;
            }).join('');

            slotsContainer.innerHTML = `
                <div class="section-mini-head">
                    <span>Свободное время</span>
                    <small>${freeCount} ${plural(freeCount, 'слот', 'слота', 'слотов')} свободно</small>
                </div>
                <div class="slot-legend">
                    <span><i class="slot-key slot-key-free"></i> свободно</span>
                    <span><i class="slot-key slot-key-busy"></i> занято</span>
                    <span><i class="slot-key slot-key-past"></i> прошло</span>
                </div>
                ${daySchedules.length
                    ? (annotated.length
                        ? groupHtml
                        : emptyState('clock', 'Нет слотов в расписании', 'Проверьте длительность смены и слота.'))
                    : emptyState(
                        'clock',
                        'Врач не принимает в этот день',
                        `${WEEKDAYS_FULL[day]} не входит в рабочее расписание врача.`
                    )}`;
        } catch (error) {
            if (requestId !== slotsRequestId) {
                return;
            }
            slotsContainer.innerHTML = emptyState('alert', 'Не удалось рассчитать слоты', mapApiError(error));
        }
    }

    function renderAll() {
        renderCalendar();
        renderDayPanel();
    }

    async function reloadAppointments() {
        try {
            appointments = await api('/appointments');
            renderAll();
        } catch (error) {
            toast(mapApiError(error), 'error');
        }
    }

    async function reloadAll() {
        container.querySelector('[data-refresh]').disabled = true;
        try {
            [doctors, patients, appointments] = await Promise.all([
                api('/doctors'),
                api('/patients'),
                api('/appointments')
            ]);
            scheduleCache.clear();
            if (!doctors.some(doctor => doctor.id === selectedDoctorId)) {
                selectedDoctorId = doctors.find(doctor => doctor.active)?.id ?? doctors[0]?.id ?? null;
            }
            doctorSelect.innerHTML = `
                <option value="">Все врачи</option>
                ${doctors.map(doctor => `
                    <option value="${doctor.id}" ${doctor.id === selectedDoctorId ? 'selected' : ''}>
                        ${esc(doctor.fullName)} · ${esc(doctor.specializationName)}
                    </option>`).join('')}`;
            renderAll();
            toast('Календарь обновлён', 'info');
        } catch (error) {
            toast(mapApiError(error), 'error');
        } finally {
            container.querySelector('[data-refresh]').disabled = false;
        }
    }

    function shiftMonth(amount) {
        const target = new Date(viewMonth.getFullYear(), viewMonth.getMonth() + amount, 1);
        const lastDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate();
        selectedDate = new Date(target.getFullYear(), target.getMonth(), Math.min(selectedDate.getDate(), lastDay));
        viewMonth = target;
        renderCalendar();
        renderDayPanel();
    }

    calendarGrid.addEventListener('click', event => {
        const button = event.target.closest('[data-date]');
        if (!button) {
            return;
        }
        const date = parseDate(button.dataset.date);
        if (!date) {
            return;
        }
        selectedDate = date;
        if (date.getMonth() !== viewMonth.getMonth() || date.getFullYear() !== viewMonth.getFullYear()) {
            viewMonth = startOfMonth(date);
        }
        renderCalendar();
        renderDayPanel();
    });

    slotsContainer.addEventListener('click', event => {
        const pick = event.target.closest('[data-pick-doctor]');
        if (pick) {
            selectedDoctorId = Number(pick.dataset.pickDoctor);
            doctorSelect.value = String(selectedDoctorId);
            renderDayPanel();
            return;
        }

        const slot = event.target.closest('[data-slot]');
        if (!slot) {
            return;
        }
        const doctor = doctors.find(item => item.id === selectedDoctorId);
        if (!doctor) {
            return;
        }
        openAppointmentForm({
            defaults: {
                doctorId: doctor.id,
                date: toISO(selectedDate),
                time: slot.dataset.slot
            },
            doctors,
            patients,
            onSaved: reloadAppointments
        });
    });

    dayList.addEventListener('click', event => {
        const button = event.target.closest('[data-appointment]');
        if (!button) {
            return;
        }
        const appointment = appointments.find(item => item.id === Number(button.dataset.appointment));
        if (appointment) {
            openAppointmentDetails(appointment, {
                role: ctx.role,
                onChanged: reloadAppointments
            });
        }
    });

    container.querySelector('[data-new]').addEventListener('click', () => {
        openAppointmentForm({
            defaults: {
                doctorId: selectedDoctorId,
                date: toISO(selectedDate)
            },
            doctors,
            patients,
            onSaved: reloadAppointments
        });
    });

    container.querySelector('[data-refresh]').addEventListener('click', reloadAll);
    container.querySelector('[data-prev]').addEventListener('click', () => shiftMonth(-1));
    container.querySelector('[data-next]').addEventListener('click', () => shiftMonth(1));
    container.querySelector('[data-today]').addEventListener('click', () => {
        selectedDate = new Date();
        viewMonth = startOfMonth(selectedDate);
        renderCalendar();
        renderDayPanel();
    });
    doctorSelect.addEventListener('change', () => {
        selectedDoctorId = doctorSelect.value ? Number(doctorSelect.value) : null;
        renderDayPanel();
    });

    renderAll();
}
