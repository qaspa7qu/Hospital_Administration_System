const ICONS = {
    dashboard: '<rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/>',
    calendar: '<rect x="3" y="4" width="18" height="17" rx="3"/><path d="M16 2v4M8 2v4M3 9h18"/><path d="M8 13h.01M12 13h.01M16 13h.01M8 17h.01M12 17h.01"/>',
    clipboard: '<rect x="5" y="4" width="14" height="18" rx="2"/><path d="M9 4V2h6v2M9 10h6M9 14h6M9 18h4"/>',
    patients: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>',
    doctor: '<path d="M4.8 3.2A.8.8 0 0 0 4 4v4.5a6 6 0 0 0 6 6 6 6 0 0 0 6-6V4a.8.8 0 0 0-.8-.8H14M10 2v4"/><path d="M10 14.5V17a5 5 0 0 0 10 0v-2"/><circle cx="20" cy="13" r="2"/>',
    layers: '<path d="m12 2 9 5-9 5-9-5 9-5Z"/><path d="m3 12 9 5 9-5M3 17l9 5 9-5"/>',
    briefcase: '<rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2M2 12h20M12 12v3"/>',
    chart: '<path d="M3 3v18h18"/><path d="M7 15v4M12 11v8M17 7v12"/>',
    users: '<path d="M15 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="8" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M17 3.13a4 4 0 0 1 0 7.75"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
    logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5M21 12H9"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.2-3.2"/>',
    close: '<path d="m6 6 12 12M18 6 6 18"/>',
    edit: '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/>',
    trash: '<path d="M3 6h18M8 6V4h8v2M19 6l-1 15H6L5 6M10 11v6M14 11v6"/>',
    chevronLeft: '<path d="m15 18-6-6 6-6"/>',
    chevronRight: '<path d="m9 18 6-6-6-6"/>',
    chevronDown: '<path d="m6 9 6 6 6-6"/>',
    check: '<path d="m5 12 5 5L20 7"/>',
    checkCircle: '<circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/>',
    xCircle: '<circle cx="12" cy="12" r="9"/><path d="m9 9 6 6M15 9l-6 6"/>',
    phone: '<path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.4 2.1L8.1 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2Z"/>',
    mail: '<rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-10 6L2 7"/>',
    pin: '<path d="M20 10c0 5-8 12-8 12s-8-7-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/>',
    wallet: '<path d="M20 12V8a2 2 0 0 0-2-2H5a3 3 0 0 0 0 6h16v6a2 2 0 0 1-2 2H5a3 3 0 0 1-3-3V7"/><path d="M16 12h.01"/>',
    arrowRight: '<path d="M5 12h14M13 6l6 6-6 6"/>',
    menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
    refresh: '<path d="M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7"/>',
    alert: '<circle cx="12" cy="12" r="9"/><path d="M12 8v5M12 16h.01"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/>',
    more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
    calendarPlus: '<path d="M21 13V6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h8"/><path d="M16 2v4M8 2v4M3 10h18M19 16v6M16 19h6"/>',
    userPlus: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M19 8v6M22 11h-6"/>',
    download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/>',
    ban: '<circle cx="12" cy="12" r="9"/><path d="m5.6 5.6 12.8 12.8"/>',
    eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/>',
    stethoscope: '<path d="M4.8 3.2A.8.8 0 0 0 4 4v4.5a6 6 0 0 0 6 6 6 6 0 0 0 6-6V4a.8.8 0 0 0-.8-.8H14M10 2v4"/><path d="M10 14.5V17a5 5 0 0 0 10 0v-2"/><circle cx="20" cy="13" r="2"/>',
    sparkles: '<path d="m12 3 1.4 3.6L17 8l-3.6 1.4L12 13l-1.4-3.6L7 8l3.6-1.4L12 3ZM5 14l.8 2.2L8 17l-2.2.8L5 20l-.8-2.2L2 17l2.2-.8L5 14ZM19 14l.8 2.2L22 17l-2.2.8L19 20l-.8-2.2L16 17l2.2-.8L19 14Z"/>',
    filter: '<path d="M4 5h16l-6 7v6l-4 2v-8L4 5Z"/>',
    home: '<path d="m3 11 9-8 9 8"/><path d="M5 10v10h14V10M9 20v-6h6v6"/>',
    printer: '<path d="M6 9V2h12v7M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><rect x="6" y="14" width="12" height="8"/>',
    arrowLeft: '<path d="M19 12H5M11 18l-6-6 6-6"/>',
    activity: '<path d="M3 12h4l3-8 4 16 3-8h4"/>',
    userCheck: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="m16 11 2 2 4-4"/>'
};

export function icon(name, size = 20, className = '') {
    const paths = ICONS[name] || ICONS.info;
    return `<svg class="icon ${className}" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths}</svg>`;
}

export function esc(value) {
    return value === null || value === undefined
        ? ''
        : String(value).replace(/[&<>"']/g, char => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#39;'
        })[char]);
}

export function initials(name) {
    return String(name || '')
        .trim()
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map(part => part[0]?.toUpperCase() || '')
        .join('');
}

export function plural(count, one, few, many) {
    const value = Math.abs(Number(count) || 0);
    const mod10 = value % 10;
    const mod100 = value % 100;
    if (mod10 === 1 && mod100 !== 11) {
        return one;
    }
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
        return few;
    }
    return many;
}

export function parseDate(value) {
    if (!value) {
        return null;
    }
    if (value instanceof Date) {
        return new Date(value.getFullYear(), value.getMonth(), value.getDate());
    }
    const [year, month, day] = String(value).slice(0, 10).split('-').map(Number);
    if (!year || !month || !day) {
        return null;
    }
    return new Date(year, month - 1, day);
}

export function toISO(date) {
    if (!date) {
        return '';
    }
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

export function todayISO() {
    return toISO(new Date());
}

export function addDays(date, amount) {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate() + amount);
}

export function startOfWeek(date) {
    const copy = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const day = copy.getDay() || 7;
    return addDays(copy, 1 - day);
}

export function startOfMonth(date) {
    return new Date(date.getFullYear(), date.getMonth(), 1);
}

export function monthMatrix(date) {
    const start = startOfWeek(startOfMonth(date));
    return Array.from({ length: 42 }, (_, index) => addDays(start, index));
}

const longDateFormatter = new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric', month: 'long', year: 'numeric'
});
const shortDateFormatter = new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit', month: '2-digit', year: 'numeric'
});
const weekdayFormatter = new Intl.DateTimeFormat('ru-RU', { weekday: 'short' });
const monthFormatter = new Intl.DateTimeFormat('ru-RU', { month: 'long', year: 'numeric' });
const moneyFormatter = new Intl.NumberFormat('ru-RU', {
    style: 'currency', currency: 'RUB', minimumFractionDigits: 2, maximumFractionDigits: 2
});

export function fmtDate(value) {
    const date = parseDate(value);
    return date ? longDateFormatter.format(date) : '—';
}

export function fmtShortDate(value) {
    const date = parseDate(value);
    return date ? shortDateFormatter.format(date) : '—';
}

export function fmtMonth(value) {
    const date = parseDate(value) || new Date();
    return monthFormatter.format(date);
}

export function fmtWeekday(value) {
    const date = parseDate(value);
    return date ? weekdayFormatter.format(date) : '';
}

export function fmtTime(value) {
    if (!value) {
        return '—';
    }
    return String(value).slice(0, 5);
}

export function fmtMoney(value) {
    return moneyFormatter.format(Number(value || 0));
}

export function minutesFromTime(value) {
    const [hours, minutes] = String(value || '00:00').split(':').map(Number);
    return (hours || 0) * 60 + (minutes || 0);
}

export function timeFromMinutes(minutes) {
    const normalized = ((minutes % 1440) + 1440) % 1440;
    return `${String(Math.floor(normalized / 60)).padStart(2, '0')}:${String(normalized % 60).padStart(2, '0')}`;
}

export const WEEKDAYS_SHORT = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];
export const WEEKDAYS_FULL = {
    MONDAY: 'Понедельник',
    TUESDAY: 'Вторник',
    WEDNESDAY: 'Среда',
    THURSDAY: 'Четверг',
    FRIDAY: 'Пятница',
    SATURDAY: 'Суббота',
    SUNDAY: 'Воскресенье'
};
export const WEEKDAY_ENUM = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

export function statusLabel(status) {
    return {
        SCHEDULED: 'Запланирован',
        COMPLETED: 'Завершён',
        CANCELLED: 'Отменён'
    }[status] || status || '—';
}

export function statusBadge(status) {
    const css = {
        SCHEDULED: 'scheduled',
        COMPLETED: 'completed',
        CANCELLED: 'cancelled'
    }[status] || 'neutral';
    return `<span class="status status-${css}"><span class="status-dot"></span>${esc(statusLabel(status))}</span>`;
}

export function roleLabel(role) {
    return {
        ADMIN: 'Администратор',
        RECEPTIONIST: 'Регистратура',
        DOCTOR: 'Врач'
    }[role] || role || 'Сотрудник';
}

export function primaryRole(user) {
    const roles = user?.roles || [];
    return ['ADMIN', 'RECEPTIONIST', 'DOCTOR'].find(role => roles.includes(role)) || 'RECEPTIONIST';
}

export function mapApiError(error) {
    const message = error?.message || 'Произошла ошибка';
    const status = error?.status;

    const rules = [
        [/Doctor is already booked for the selected time interval/i, 'У выбранного врача уже есть приём на это время. Выберите другой слот.'],
        [/Doctor does not work on/i, 'Врач не принимает в выбранный день или время.'],
        [/Cannot book an appointment in the past/i, 'Нельзя создать запись на прошедшую дату.'],
        [/does not fit into the doctor's working hours/i, 'Приём не помещается в рабочее время врача.'],
        [/Doctor is inactive/i, 'Врач деактивирован и не может принимать записи.'],
        [/Only SCHEDULED appointments can be rescheduled/i, 'Перенести можно только запланированную запись.'],
        [/Appointment status cannot be changed/i, 'Статус этой записи уже нельзя изменить.'],
        [/Appointment cannot be returned to SCHEDULED/i, 'Запись нельзя вернуть в статус «Запланирован».'],
        [/Services can only be changed for SCHEDULED appointments/i, 'Состав услуг можно менять только у запланированной записи.'],
        [/Service is already added to this appointment/i, 'Эта услуга уже добавлена к записи.'],
        [/Service is inactive/i, 'Неактивную услугу нельзя добавить к записи.'],
        [/Cannot delete a patient with existing appointments/i, 'Нельзя удалить пациента, у которого есть записи на приём.'],
        [/Cannot delete a doctor with existing appointments/i, 'Нельзя удалить врача, у которого есть записи на приём.'],
        [/Cannot delete a service used in appointments/i, 'Нельзя удалить услугу, которая уже используется в записях.'],
        [/Cannot delete a user linked to a doctor profile/i, 'Нельзя удалить сотрудника, с которым связан профиль врача.'],
        [/Cannot remove DOCTOR role from a user with an existing doctor profile/i, 'Нельзя убрать роль врача у сотрудника с существующим профилем врача.'],
        [/User must have the DOCTOR role/i, 'Профиль врача можно создать только для сотрудника с ролью «Врач».'],
        [/A doctor profile already exists/i, 'У этого сотрудника уже есть профиль врача.'],
        [/A schedule with this day and start time already exists/i, 'На этот день и время уже есть рабочее расписание.'],
        [/Start time must be before end time/i, 'Время начала должно быть раньше времени окончания.'],
        [/Slot duration must be between/i, 'Длительность слота должна быть от 5 до 480 минут.'],
        [/Service quantity must be between/i, 'Количество услуги должно быть от 1 до 100.'],
        [/Username already exists/i, 'Такое имя пользователя уже занято.'],
        [/already exists/i, 'Такая запись уже существует.'],
        [/Invalid username or password/i, 'Неверный логин или пароль.'],
        [/Authentication required/i, 'Сессия истекла. Войдите снова.'],
        [/Insufficient permissions/i, 'Недостаточно прав для этого действия.']
    ];

    for (const [pattern, replacement] of rules) {
        if (pattern.test(message)) {
            return replacement;
        }
    }

    if (status === 403) {
        return 'Недостаточно прав для этого действия.';
    }
    if (status === 404) {
        return 'Запись не найдена. Возможно, она уже была удалена.';
    }
    if (status === 400) {
        return message || 'Проверьте заполнение полей.';
    }
    return message;
}

let modalLayer = 0;

export function openModal({
    title,
    subtitle = '',
    body = '',
    footer = '',
    size = 'md',
    onMount,
    onClose,
    dismissible = true
}) {
    const overlay = document.createElement('div');
    const layer = ++modalLayer;
    overlay.className = 'modal-overlay';
    overlay.style.zIndex = String(100 + layer);
    overlay.innerHTML = `
        <div class="modal modal-${size}" role="dialog" aria-modal="true" aria-labelledby="modal-title-${layer}">
            <div class="modal-head">
                <div>
                    <h2 id="modal-title-${layer}">${esc(title)}</h2>
                    ${subtitle ? `<p>${esc(subtitle)}</p>` : ''}
                </div>
                <button class="icon-btn modal-close" type="button" aria-label="Закрыть">${icon('close')}</button>
            </div>
            <div class="modal-body"></div>
            ${footer ? `<div class="modal-footer">${footer}</div>` : ''}
        </div>`;

    const bodyElement = overlay.querySelector('.modal-body');
    if (body instanceof Node) {
        bodyElement.append(body);
    } else {
        bodyElement.innerHTML = body;
    }

    let closed = false;
    const handleKey = event => {
        if (event.key === 'Escape' && dismissible) {
            close();
        }
    };
    const close = () => {
        if (closed) {
            return;
        }
        closed = true;
        document.removeEventListener('keydown', handleKey);
        overlay.classList.remove('open');
        overlay.classList.add('closing');
        window.setTimeout(() => {
            overlay.remove();
            modalLayer = Math.max(0, modalLayer - 1);
            if (!document.querySelector('.modal-overlay')) {
                document.body.classList.remove('modal-open');
            }
        }, 150);
        onClose?.();
    };

    overlay.querySelector('.modal-close').addEventListener('click', close);
    overlay.addEventListener('mousedown', event => {
        if (dismissible && event.target === overlay) {
            close();
        }
    });
    document.addEventListener('keydown', handleKey);
    document.body.append(overlay);
    document.body.classList.add('modal-open');
    requestAnimationFrame(() => {
        overlay.classList.add('open');
        const focusTarget = overlay.querySelector('input:not([type="hidden"]), select, textarea, button.btn-primary');
        focusTarget?.focus({ preventScroll: true });
    });

    if (onMount) {
        onMount({ root: overlay, body: bodyElement, close });
    }

    return { root: overlay, body: bodyElement, close };
}

export function confirmDialog({
    title = 'Подтвердите действие',
    message = '',
    confirmLabel = 'Подтвердить',
    cancelLabel = 'Отмена',
    danger = false
}) {
    return new Promise(resolve => {
        let settled = false;
        const finish = value => {
            if (settled) {
                return;
            }
            settled = true;
            resolve(value);
        };

        const dialog = openModal({
            title,
            size: 'sm',
            body: `<p class="confirm-text">${esc(message)}</p>`,
            footer: `
                <button class="btn btn-secondary" type="button" data-confirm="no">${esc(cancelLabel)}</button>
                <button class="btn ${danger ? 'btn-danger' : 'btn-primary'}" type="button" data-confirm="yes">${esc(confirmLabel)}</button>`,
            onMount: ({ root, close }) => {
                root.querySelector('[data-confirm="yes"]').addEventListener('click', () => {
                    finish(true);
                    close();
                });
                root.querySelector('[data-confirm="no"]').addEventListener('click', () => {
                    finish(false);
                    close();
                });
            },
            onClose: () => finish(false)
        });

        return dialog;
    });
}

export function toast(message, type = 'success') {
    const root = document.getElementById('toast-root');
    if (!root) {
        return;
    }
    const element = document.createElement('div');
    const iconName = type === 'error' ? 'alert' : type === 'info' ? 'info' : 'checkCircle';
    element.className = `toast toast-${type}`;
    element.setAttribute('role', type === 'error' ? 'alert' : 'status');
    element.innerHTML = `<span class="toast-icon">${icon(iconName, 19)}</span><span>${esc(message)}</span>`;
    root.append(element);
    requestAnimationFrame(() => element.classList.add('visible'));
    window.setTimeout(() => {
        element.classList.remove('visible');
        window.setTimeout(() => element.remove(), 220);
    }, 3600);
}

export function setButtonLoading(button, loading, loadingLabel = 'Подождите…') {
    if (!button) {
        return;
    }
    if (loading) {
        button.dataset.originalHtml = button.innerHTML;
        button.disabled = true;
        button.innerHTML = `<span class="spinner"></span>${esc(loadingLabel)}`;
    } else {
        button.disabled = false;
        if (button.dataset.originalHtml) {
            button.innerHTML = button.dataset.originalHtml;
            delete button.dataset.originalHtml;
        }
    }
}

export function emptyState(iconName, title, text = '', action = '') {
    return `
        <div class="empty-state">
            <span class="empty-icon">${icon(iconName, 24)}</span>
            <h3>${esc(title)}</h3>
            ${text ? `<p>${esc(text)}</p>` : ''}
            ${action}
        </div>`;
}

export function loadingState(label = 'Загружаем данные…') {
    return `
        <div class="loading-state">
            <span class="spinner spinner-large"></span>
            <span>${esc(label)}</span>
        </div>`;
}

export function pageHead({ eyebrow = '', title, description = '', actions = '' }) {
    return `
        <div class="page-head">
            <div>
                ${eyebrow ? `<span class="eyebrow">${esc(eyebrow)}</span>` : ''}
                <h1>${esc(title)}</h1>
                ${description ? `<p>${esc(description)}</p>` : ''}
            </div>
            ${actions ? `<div class="page-actions">${actions}</div>` : ''}
        </div>`;
}

export function debounce(fn, delay = 280) {
    let timeoutId;
    return (...args) => {
        window.clearTimeout(timeoutId);
        timeoutId = window.setTimeout(() => fn(...args), delay);
    };
}

export function optionList(items, selectedValue, labelFn, valueFn = item => item.id) {
    return items
        .map(item => {
            const value = valueFn(item);
            return `<option value="${esc(value)}" ${String(value) === String(selectedValue) ? 'selected' : ''}>${esc(labelFn(item))}</option>`;
        })
        .join('');
}
