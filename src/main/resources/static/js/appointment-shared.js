import { api } from './api.js';
import {
    confirmDialog,
    emptyState,
    esc,
    fmtDate,
    fmtMoney,
    fmtTime,
    icon,
    mapApiError,
    minutesFromTime,
    openModal,
    parseDate,
    setButtonLoading,
    statusBadge,
    statusLabel,
    timeFromMinutes,
    toast,
    todayISO,
    toISO,
    WEEKDAY_ENUM
} from './ui.js';

export function patientFullName(patient) {
    return [patient?.lastName, patient?.firstName, patient?.middleName]
        .filter(Boolean)
        .join(' ');
}

export function patientLabel(patient) {
    const name = patientFullName(patient);
    return patient?.phone ? `${name} · ${patient.phone}` : name;
}

export function doctorLabel(doctor) {
    return doctor?.fullName || [doctor?.lastName, doctor?.firstName].filter(Boolean).join(' ');
}

function formatSlotRange(start, end) {
    return `${start}–${end}`;
}

export async function openAppointmentForm({
    appointment = null,
    defaults = {},
    doctors = null,
    patients = null,
    onSaved
} = {}) {
    const [doctorList, patientList] = await Promise.all([
        doctors ? Promise.resolve(doctors) : api('/doctors'),
        patients ? Promise.resolve(patients) : api('/patients')
    ]);
    const editableDoctors = doctorList.filter(doctor =>
        doctor.active || doctor.id === appointment?.doctorId
    );

    if (!patientList.length) {
        openModal({
            title: 'Новая запись',
            size: 'sm',
            body: emptyState(
                'patients',
                'Сначала добавьте пациента',
                'Для создания записи нужна карточка пациента.',
                `<button class="btn btn-primary" type="button" data-go-patients>Перейти к пациентам</button>`
            ),
            onMount: ({ root, close }) => {
                root.querySelector('[data-go-patients]')?.addEventListener('click', () => {
                    close();
                    window.location.hash = '#/patients';
                });
            }
        });
        return;
    }
    if (!editableDoctors.length) {
        openModal({
            title: 'Новая запись',
            size: 'sm',
            body: emptyState(
                'doctor',
                'Нет доступных врачей',
                'Добавьте врача и настройте его рабочее расписание.',
                `<button class="btn btn-primary" type="button" data-go-doctors>Перейти к врачам</button>`
            ),
            onMount: ({ root, close }) => {
                root.querySelector('[data-go-doctors]')?.addEventListener('click', () => {
                    close();
                    window.location.hash = '#/doctors';
                });
            }
        });
        return;
    }

    const isEdit = Boolean(appointment);
    const initialDoctorId = appointment?.doctorId ?? defaults.doctorId ?? editableDoctors[0]?.id ?? '';
    const initialDate = appointment?.appointmentDate ?? defaults.date ?? todayISO();
    const initialTime = appointment?.startTime?.slice(0, 5) ?? defaults.time ?? '';
    const initialPatient = appointment
        ? patientList.find(patient => patient.id === appointment.patientId)
        : null;
    const patientOptions = patientList
        .map(patient => `<option value="${esc(patientLabel(patient))}"></option>`)
        .join('');

    const doctorOptions = editableDoctors
        .map(doctor => `
            <option value="${doctor.id}" ${doctor.id === initialDoctorId ? 'selected' : ''}>
                ${esc(doctorLabel(doctor))}${doctor.active ? '' : ' · неактивен'}
            </option>`)
        .join('');

    const formId = `appointment-form-${Date.now()}`;
    const body = `
        <form id="${formId}" class="form-stack" novalidate>
            <div class="form-grid">
                <label class="field field-span-2">
                    <span class="field-label">Пациент <span class="required">*</span></span>
                    <input class="input" name="patient" list="patients-${formId}"
                           value="${esc(initialPatient ? patientLabel(initialPatient) : '')}"
                           placeholder="Начните вводить фамилию или телефон" autocomplete="off" required>
                    <datalist id="patients-${formId}">${patientOptions}</datalist>
                </label>

                <label class="field">
                    <span class="field-label">Врач <span class="required">*</span></span>
                    <select class="input" name="doctorId" required>${doctorOptions}</select>
                </label>

                <label class="field">
                    <span class="field-label">Дата приёма <span class="required">*</span></span>
                    <input class="input" type="date" name="appointmentDate"
                           value="${esc(initialDate)}" min="${todayISO()}" required>
                </label>

                <label class="field field-span-2">
                    <span class="field-label">Свободное время <span class="required">*</span></span>
                    <select class="input" name="startTime" required>
                        <option value="">Загрузка свободных слотов…</option>
                    </select>
                    <span class="field-hint" data-slot-hint>Слоты рассчитаны по рабочему расписанию врача.</span>
                </label>

                <label class="field field-span-2">
                    <span class="field-label">Причина обращения</span>
                    <input class="input" name="reason" maxlength="500"
                           value="${esc(appointment?.reason || '')}"
                           placeholder="Например, консультация или повторный приём">
                </label>

                <label class="field field-span-2">
                    <span class="field-label">Заметки</span>
                    <textarea class="input textarea" name="notes" maxlength="1000" rows="3"
                              placeholder="Дополнительная информация для врача">${esc(appointment?.notes || '')}</textarea>
                </label>
            </div>
            <div class="form-error hidden" data-form-error role="alert"></div>
        </form>`;

    const footer = `
        <button class="btn btn-secondary" type="button" data-dismiss>Отмена</button>
        <button class="btn btn-primary" type="submit" form="${formId}" data-submit>
            ${isEdit ? 'Сохранить изменения' : 'Записать на приём'}
        </button>`;

    let modal;
    modal = openModal({
        title: isEdit ? 'Изменить запись' : 'Новая запись на приём',
        subtitle: isEdit ? 'Перенос возможен только для запланированного приёма' : 'Свободные слоты проверяются по расписанию врача',
        size: 'lg',
        body,
        footer,
        onMount: ({ root, close }) => {
            const form = root.querySelector(`#${formId}`);
            const dateInput = form.elements.appointmentDate;
            const doctorSelect = form.elements.doctorId;
            const timeSelect = form.elements.startTime;
            const hint = root.querySelector('[data-slot-hint]');
            const errorBox = root.querySelector('[data-form-error]');
            const submitButton = root.querySelector('[data-submit]');
            let slotsRequest = 0;

            root.querySelector('[data-dismiss]').addEventListener('click', close);

            function showError(message) {
                errorBox.textContent = message;
                errorBox.classList.remove('hidden');
            }

            function hideError() {
                errorBox.textContent = '';
                errorBox.classList.add('hidden');
            }

            async function refreshSlots() {
                hideError();
                const doctorId = Number(doctorSelect.value);
                const date = dateInput.value;
                const requestId = ++slotsRequest;

                if (!doctorId || !date) {
                    timeSelect.innerHTML = '<option value="">Сначала выберите врача и дату</option>';
                    timeSelect.disabled = true;
                    return;
                }

                const selectedBefore = timeSelect.value;
                timeSelect.disabled = true;
                timeSelect.innerHTML = '<option value="">Загрузка слотов…</option>';
                hint.textContent = 'Проверяем расписание и занятость врача…';

                try {
                    const [schedules, dayAppointments] = await Promise.all([
                        api(`/doctors/${doctorId}/schedule`),
                        api('/appointments', { params: { doctorId, date } })
                    ]);
                    if (requestId !== slotsRequest) {
                        return;
                    }

                    const day = WEEKDAY_ENUM[parseDate(date).getDay()];
                    const daySchedules = schedules.filter(schedule =>
                        schedule.active && schedule.dayOfWeek === day
                    );
                    const occupied = dayAppointments.filter(item =>
                        item.status !== 'CANCELLED' && item.id !== appointment?.id
                    );

                    const slots = new Map();
                    daySchedules.forEach(schedule => {
                        const start = minutesFromTime(schedule.startTime);
                        const end = minutesFromTime(schedule.endTime);
                        const duration = Number(schedule.slotDurationMinutes) || 30;
                        for (let cursor = start; cursor + duration <= end; cursor += duration) {
                            const slotStart = timeFromMinutes(cursor);
                            const slotEnd = timeFromMinutes(cursor + duration);
                            if (!slots.has(slotStart)) {
                                slots.set(slotStart, {
                                    start: slotStart,
                                    end: slotEnd,
                                    startMinutes: cursor,
                                    endMinutes: cursor + duration
                                });
                            }
                        }
                    });

                    const sorted = [...slots.values()].sort((a, b) => a.startMinutes - b.startMinutes);
                    const now = new Date();
                    const nowMinutes = now.getHours() * 60 + now.getMinutes();
                    const isToday = date === todayISO();
                    const currentTime = appointment?.appointmentDate === date
                        ? appointment.startTime?.slice(0, 5)
                        : null;

                    const available = sorted.filter(slot => {
                        const overlap = occupied.some(item => {
                            const itemStart = minutesFromTime(item.startTime);
                            const itemEnd = minutesFromTime(item.endTime);
                            return itemStart < slot.endMinutes && itemEnd > slot.startMinutes;
                        });
                        const past = isToday && slot.startMinutes <= nowMinutes;
                        return !overlap && (!past || slot.start === currentTime);
                    });

                    if (!sorted.length) {
                        timeSelect.innerHTML = '<option value="">Врач не принимает в этот день</option>';
                        timeSelect.disabled = true;
                        hint.textContent = 'В рабочем расписании врача нет смены на выбранный день.';
                        return;
                    }
                    if (!available.length) {
                        timeSelect.innerHTML = '<option value="">Свободных слотов нет</option>';
                        timeSelect.disabled = true;
                        hint.textContent = 'Все слоты на этот день уже заняты. Выберите другую дату.';
                        return;
                    }

                    timeSelect.innerHTML = available
                        .map(slot => `<option value="${slot.start}">${formatSlotRange(slot.start, slot.end)}</option>`)
                        .join('');
                    timeSelect.disabled = false;

                    const preferred = selectedBefore || initialTime || currentTime;
                    if (preferred && available.some(slot => slot.start === preferred)) {
                        timeSelect.value = preferred;
                    }

                    hint.textContent = `Свободно ${available.length} ${available.length === 1 ? 'слот' : available.length < 5 ? 'слота' : 'слотов'} из ${sorted.length}.`;
                } catch (error) {
                    if (requestId !== slotsRequest) {
                        return;
                    }
                    timeSelect.innerHTML = '<option value="">Не удалось загрузить слоты</option>';
                    timeSelect.disabled = true;
                    hint.textContent = 'Попробуйте выбрать другую дату или врача.';
                    showError(mapApiError(error));
                }
            }

            dateInput.addEventListener('change', refreshSlots);
            doctorSelect.addEventListener('change', refreshSlots);

            form.addEventListener('submit', async event => {
                event.preventDefault();
                hideError();

                const label = form.elements.patient.value.trim();
                const patient = patientList.find(item => patientLabel(item) === label)
                    || patientList.find(item =>
                        patientFullName(item).toLocaleLowerCase('ru') === label.toLocaleLowerCase('ru')
                    );

                if (!patient) {
                    showError('Выберите пациента из подсказок списка.');
                    form.elements.patient.focus();
                    return;
                }
                if (!form.elements.startTime.value) {
                    showError('Выберите свободное время приёма.');
                    return;
                }

                const payload = {
                    patientId: patient.id,
                    doctorId: Number(doctorSelect.value),
                    appointmentDate: dateInput.value,
                    startTime: timeSelect.value,
                    reason: form.elements.reason.value.trim() || null,
                    notes: form.elements.notes.value.trim() || null
                };

                setButtonLoading(submitButton, true, isEdit ? 'Сохраняем…' : 'Записываем…');
                try {
                    const saved = isEdit
                        ? await api(`/appointments/${appointment.id}`, { method: 'PUT', body: payload })
                        : await api('/appointments', { method: 'POST', body: payload });
                    toast(isEdit ? 'Запись обновлена' : 'Пациент записан на приём');
                    close();
                    onSaved?.(saved);
                } catch (error) {
                    showError(mapApiError(error));
                    setButtonLoading(submitButton, false);
                }
            });

            refreshSlots();
        }
    });

    return modal;
}

export async function openAppointmentDetails(appointment, { role, onChanged } = {}) {
    const canSeePatient = role === 'ADMIN' || role === 'RECEPTIONIST';
    const canManageBilling = canSeePatient;

    const [patientResult, billingResult] = await Promise.allSettled([
        canSeePatient ? api(`/patients/${appointment.patientId}`) : Promise.resolve(null),
        canManageBilling
            ? api(`/appointments/${appointment.id}/services`)
            : Promise.resolve(null)
    ]);

    const patient = patientResult.status === 'fulfilled' ? patientResult.value : null;
    const billing = billingResult.status === 'fulfilled' ? billingResult.value : null;
    const total = appointment.totalPrice ?? billing?.totalPrice ?? 0;
    const isScheduled = appointment.status === 'SCHEDULED';

    const body = `
        <div class="detail-topline">
            ${statusBadge(appointment.status)}
            <span class="detail-id">Запись №${appointment.id}</span>
        </div>

        <div class="detail-hero">
            <div class="detail-date">
                <span class="detail-date-day">${esc(String(parseDate(appointment.appointmentDate)?.getDate() || ''))}</span>
                <span class="detail-date-month">${esc(fmtDate(appointment.appointmentDate).replace(/^\d+\s+/, ''))}</span>
            </div>
            <div>
                <div class="detail-time">${fmtTime(appointment.startTime)} — ${fmtTime(appointment.endTime)}</div>
                <div class="detail-subtitle">${esc(fmtDate(appointment.appointmentDate))}</div>
            </div>
            <div class="detail-price">
                <span>Стоимость</span>
                <strong>${fmtMoney(total)}</strong>
            </div>
        </div>

        <div class="detail-grid">
            <div class="detail-item">
                <span class="detail-label">Пациент</span>
                <strong>${esc(appointment.patientFullName)}</strong>
                ${patient?.phone ? `<span class="detail-muted">${esc(patient.phone)}</span>` : ''}
            </div>
            <div class="detail-item">
                <span class="detail-label">Врач</span>
                <strong>${esc(appointment.doctorFullName)}</strong>
                <span class="detail-muted">${esc(appointment.specializationName || '')}</span>
            </div>
            <div class="detail-item">
                <span class="detail-label">Причина обращения</span>
                <strong>${esc(appointment.reason || 'Не указана')}</strong>
            </div>
            <div class="detail-item">
                <span class="detail-label">Заметки</span>
                <span class="detail-notes">${esc(appointment.notes || 'Нет заметок')}</span>
            </div>
        </div>

        ${canManageBilling ? `
            <div class="detail-services">
                <div class="detail-services-head">
                    <div>
                        <span class="detail-label">Услуги в приёме</span>
                        <strong>${billing?.services?.length || 0} ${billing?.services?.length === 1 ? 'позиция' : 'позиций'}</strong>
                    </div>
                    <button class="btn btn-secondary btn-sm" type="button" data-open-billing>
                        ${icon('wallet', 16)} Управлять услугами
                    </button>
                </div>
                ${billing?.services?.length
                    ? `<div class="detail-services-list">
                        ${billing.services.map(item => `
                            <div class="detail-service-row">
                                <span>${esc(item.serviceName)} × ${item.quantity}</span>
                                <strong>${fmtMoney(item.lineTotal)}</strong>
                            </div>`).join('')}
                       </div>`
                    : `<p class="detail-muted detail-services-empty">Услуги пока не добавлены.</p>`}
            </div>` : ''}`;

    let actions = `<button class="btn btn-secondary" type="button" data-detail-close>Закрыть</button>`;

    if (canSeePatient && isScheduled) {
        actions = `
            <button class="btn btn-secondary" type="button" data-detail-close>Закрыть</button>
            <button class="btn btn-ghost" type="button" data-detail-edit>${icon('edit', 16)} Изменить</button>
            <button class="btn btn-danger-soft" type="button" data-detail-cancel>${icon('ban', 16)} Отменить</button>
            <button class="btn btn-success-soft" type="button" data-detail-complete>${icon('check', 16)} Завершить</button>`;
    }

    const modal = openModal({
        title: 'Карточка записи',
        subtitle: `${fmtDate(appointment.appointmentDate)}, ${fmtTime(appointment.startTime)}`,
        size: 'lg',
        body,
        footer: actions,
        onMount: ({ root, close }) => {
            root.querySelector('[data-detail-close]')?.addEventListener('click', close);

            async function changeStatus(status) {
                const labels = {
                    COMPLETED: {
                        title: 'Завершить приём?',
                        message: 'Запись перейдёт в терминальный статус «Завершён». Изменить её больше нельзя.',
                        label: 'Завершить',
                        danger: false
                    },
                    CANCELLED: {
                        title: 'Отменить запись?',
                        message: 'Слот освободится, но вернуть запись в статус «Запланирован» будет нельзя.',
                        label: 'Отменить запись',
                        danger: true
                    }
                };
                const config = labels[status];
                const confirmed = await confirmDialog({
                    title: config.title,
                    message: config.message,
                    confirmLabel: config.label,
                    danger: config.danger
                });
                if (!confirmed) {
                    return;
                }
                try {
                    await api(`/appointments/${appointment.id}/status`, {
                        method: 'PATCH',
                        body: { status }
                    });
                    toast(status === 'COMPLETED' ? 'Приём завершён' : 'Запись отменена');
                    close();
                    onChanged?.();
                } catch (error) {
                    toast(mapApiError(error), 'error');
                }
            }

            root.querySelector('[data-detail-complete]')?.addEventListener('click', () => changeStatus('COMPLETED'));
            root.querySelector('[data-detail-cancel]')?.addEventListener('click', () => changeStatus('CANCELLED'));

            root.querySelector('[data-detail-edit]')?.addEventListener('click', () => {
                close();
                openAppointmentForm({
                    appointment,
                    onSaved: () => onChanged?.()
                });
            });

            root.querySelector('[data-open-billing]')?.addEventListener('click', () => {
                openBillingModal(appointment, { onChanged });
            });
        }
    });

    return modal;
}

export async function openBillingModal(appointment, { onChanged } = {}) {
    const editable = appointment.status === 'SCHEDULED';
    let billing = null;
    let catalog = [];
    let busy = false;

    const footer = `<button class="btn btn-secondary" type="button" data-billing-close>Закрыть</button>`;
    const modal = openModal({
        title: 'Услуги и стоимость',
        subtitle: `${appointment.patientFullName} · ${fmtDate(appointment.appointmentDate)}`,
        size: 'lg',
        body: `<div class="loading-state"><span class="spinner spinner-large"></span><span>Загружаем услуги…</span></div>`,
        footer,
        onMount: ({ root, body, close }) => {
            root.querySelector('[data-billing-close]').addEventListener('click', close);
            body.addEventListener('click', handleClick);
            body.addEventListener('submit', handleSubmit);

            async function load() {
                try {
                    const requests = [api(`/appointments/${appointment.id}/services`)];
                    if (editable) {
                        requests.push(api('/services', { params: { activeOnly: true } }));
                    }
                    const [billingData, servicesData] = await Promise.all(requests);
                    billing = billingData;
                    catalog = servicesData || [];
                    render();
                } catch (error) {
                    body.innerHTML = emptyState('alert', 'Не удалось загрузить услуги', mapApiError(error));
                }
            }

            function render() {
                const attachedIds = new Set((billing?.services || []).map(item => item.serviceId));
                const available = catalog.filter(item => item.active && !attachedIds.has(item.id));
                const items = billing?.services || [];

                body.innerHTML = `
                    <div class="invoice-summary">
                        <div>
                            <span class="detail-label">Итоговая стоимость приёма</span>
                            <strong class="invoice-total">${fmtMoney(billing?.totalPrice || 0)}</strong>
                        </div>
                        <span class="invoice-count">${items.length} ${items.length === 1 ? 'услуга' : items.length < 5 ? 'услуги' : 'услуг'}</span>
                    </div>

                    ${!editable ? `
                        <div class="notice notice-info">
                            ${icon('info', 18)}
                            <span>Запись в статусе «${esc(statusLabel(appointment.status))}». Состав услуг доступен только для просмотра.</span>
                        </div>` : ''}

                    <div class="invoice-list">
                        ${items.length ? items.map(item => `
                            <div class="invoice-row">
                                <div class="invoice-row-main">
                                    <strong>${esc(item.serviceName)}</strong>
                                    <span>${fmtMoney(item.unitPrice)} за единицу</span>
                                </div>
                                ${editable ? `
                                    <div class="qty-control" aria-label="Количество услуги">
                                        <button class="qty-btn" type="button" data-action="decrease"
                                                data-item="${item.id}" data-quantity="${item.quantity}"
                                                ${item.quantity <= 1 ? 'disabled' : ''} aria-label="Уменьшить количество">−</button>
                                        <span class="qty-value">${item.quantity}</span>
                                        <button class="qty-btn" type="button" data-action="increase"
                                                data-item="${item.id}" data-quantity="${item.quantity}"
                                                ${item.quantity >= 100 ? 'disabled' : ''} aria-label="Увеличить количество">+</button>
                                    </div>` : `<span class="invoice-qty">× ${item.quantity}</span>`}
                                <strong class="invoice-line-total">${fmtMoney(item.lineTotal)}</strong>
                                ${editable ? `
                                    <button class="icon-btn icon-btn-danger" type="button" data-action="remove"
                                            data-item="${item.id}" data-name="${esc(item.serviceName)}"
                                            aria-label="Удалить услугу">${icon('trash', 17)}</button>` : ''}
                            </div>`).join('') : emptyState(
                                'wallet',
                                'Услуги не добавлены',
                                editable ? 'Добавьте услуги из справочника, чтобы рассчитать стоимость приёма.' : 'К этой записи услуги не привязаны.'
                            )}
                    </div>

                    ${editable ? `
                        <form class="invoice-add" data-add-form>
                            <label class="field">
                                <span class="field-label">Добавить услугу</span>
                                <select class="input" name="serviceId" ${available.length ? '' : 'disabled'}>
                                    ${available.length
                                        ? available.map(item => `<option value="${item.id}">${esc(item.name)} — ${fmtMoney(item.price)}</option>`).join('')
                                        : '<option value="">Все активные услуги уже добавлены</option>'}
                                </select>
                            </label>
                            <label class="field invoice-qty-field">
                                <span class="field-label">Кол-во</span>
                                <input class="input" type="number" name="quantity" min="1" max="100" value="1" ${available.length ? '' : 'disabled'}>
                            </label>
                            <button class="btn btn-primary" type="submit" ${available.length ? '' : 'disabled'}>
                                ${icon('plus', 16)} Добавить
                            </button>
                        </form>` : ''}`;
            }

            function setBusy(value) {
                busy = value;
                body.querySelectorAll('button, input, select').forEach(element => {
                    if (value) {
                        element.dataset.wasDisabled = element.disabled ? '1' : '0';
                        element.disabled = true;
                    } else if (element.dataset.wasDisabled === '0') {
                        element.disabled = false;
                    }
                });
            }

            async function mutate(action, successMessage) {
                if (busy) {
                    return;
                }
                setBusy(true);
                try {
                    const result = await action();
                    if (result) {
                        billing = result;
                    }
                    toast(successMessage);
                    render();
                    onChanged?.();
                } catch (error) {
                    toast(mapApiError(error), 'error');
                    setBusy(false);
                }
            }

            function handleClick(event) {
                const button = event.target.closest('[data-action]');
                if (!button) {
                    return;
                }
                const itemId = Number(button.dataset.item);
                const action = button.dataset.action;

                if (action === 'increase' || action === 'decrease') {
                    const quantity = Number(button.dataset.quantity) + (action === 'increase' ? 1 : -1);
                    mutate(
                        () => api(`/appointments/${appointment.id}/services/${itemId}`, {
                            method: 'PUT',
                            body: { quantity }
                        }),
                        'Количество обновлено'
                    );
                }

                if (action === 'remove') {
                    confirmDialog({
                        title: 'Убрать услугу?',
                        message: `«${button.dataset.name}» будет удалена из этой записи.`,
                        confirmLabel: 'Убрать',
                        danger: true
                    }).then(confirmed => {
                        if (!confirmed) {
                            return;
                        }
                        mutate(
                            async () => {
                                await api(`/appointments/${appointment.id}/services/${itemId}`, { method: 'DELETE' });
                                return api(`/appointments/${appointment.id}/services`);
                            },
                            'Услуга удалена'
                        );
                    });
                }
            }

            function handleSubmit(event) {
                const form = event.target.closest('[data-add-form]');
                if (!form) {
                    return;
                }
                event.preventDefault();
                const serviceId = Number(form.elements.serviceId.value);
                const quantity = Number(form.elements.quantity.value);
                if (!serviceId) {
                    return;
                }
                mutate(
                    () => api(`/appointments/${appointment.id}/services`, {
                        method: 'POST',
                        body: { serviceId, quantity }
                    }),
                    'Услуга добавлена'
                );
            }

            load();
        }
    });

    return modal;
}

export function appointmentRow(appointment, { showDoctor = true } = {}) {
    return `
        <button class="appointment-row" type="button" data-appointment="${appointment.id}">
            <span class="appointment-time">
                <strong>${fmtTime(appointment.startTime)}</strong>
                <small>${fmtTime(appointment.endTime)}</small>
            </span>
            <span class="appointment-main">
                <strong>${esc(appointment.patientFullName)}</strong>
                <small>${showDoctor ? esc(appointment.doctorFullName) : esc(appointment.reason || 'Приём')}</small>
            </span>
            <span class="appointment-meta">
                ${statusBadge(appointment.status)}
                <small>${fmtMoney(appointment.totalPrice)}</small>
            </span>
        </button>`;
}
