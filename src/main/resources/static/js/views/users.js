import { api } from '../api.js';
import {
    confirmDialog,
    debounce,
    emptyState,
    esc,
    icon,
    loadingState,
    mapApiError,
    openModal,
    plural,
    roleLabel,
    toast
} from '../ui.js';

export async function render(container, ctx) {
    container.innerHTML = loadingState('Загружаем сотрудников…');

    let users;
    try {
        users = await api('/admin/users');
    } catch (error) {
        container.innerHTML = emptyState('alert', 'Не удалось загрузить сотрудников', mapApiError(error));
        return;
    }

    const state = { search: '', role: '' };

    container.innerHTML = `
        <div class="page-head">
            <div>
                <span class="eyebrow">Доступ и роли</span>
                <h1>Сотрудники</h1>
                <p>Учётные записи администраторов, врачей и сотрудников регистратуры.</p>
            </div>
            <div class="page-actions">
                <button class="btn btn-secondary" type="button" data-refresh>${icon('refresh', 17)} Обновить</button>
                <button class="btn btn-primary" type="button" data-new>${icon('userPlus', 18)} Новый сотрудник</button>
            </div>
        </div>

        <section class="mini-stats" data-mini-stats></section>

        <section class="card filter-card">
            <div class="filters filters-compact">
                <label class="field field-search field-grow">
                    <span class="field-label">Поиск</span>
                    <span class="input-icon-wrap">
                        ${icon('search', 17)}
                        <input class="input" type="search" data-search placeholder="Имя, логин, email или телефон">
                    </span>
                </label>
                <label class="field">
                    <span class="field-label">Роль</span>
                    <select class="input" data-role>
                        <option value="">Все роли</option>
                        <option value="ADMIN">Администратор</option>
                        <option value="DOCTOR">Врач</option>
                        <option value="RECEPTIONIST">Регистратура</option>
                    </select>
                </label>
                <button class="btn btn-ghost filter-reset" type="button" data-reset>${icon('close', 16)} Сбросить</button>
            </div>
            <div class="filter-meta"><span data-result-count></span></div>
        </section>

        <section class="card table-card">
            <div class="table-wrap" data-table></div>
        </section>`;

    const searchInput = container.querySelector('[data-search]');
    const roleSelect = container.querySelector('[data-role]');
    const tableWrap = container.querySelector('[data-table]');
    const resultCount = container.querySelector('[data-result-count]');
    const miniStats = container.querySelector('[data-mini-stats]');

    function renderStats() {
        const admins = users.filter(user => user.roles.includes('ADMIN')).length;
        const doctors = users.filter(user => user.roles.includes('DOCTOR')).length;
        const receptionists = users.filter(user => user.roles.includes('RECEPTIONIST')).length;
        miniStats.innerHTML = `
            <span class="mini-stat"><strong>${users.length}</strong> ${plural(users.length, 'сотрудник', 'сотрудника', 'сотрудников')}</span>
            <span class="mini-stat"><strong>${admins}</strong> админ.</span>
            <span class="mini-stat"><strong>${doctors}</strong> врачей</span>
            <span class="mini-stat"><strong>${receptionists}</strong> регистратура</span>`;
    }

    function filtered() {
        const search = state.search.trim().toLocaleLowerCase('ru');
        return users
            .filter(user => {
                if (state.role && !user.roles.includes(state.role)) return false;
                if (!search) return true;
                return [user.fullName, user.username, user.email, user.phone]
                    .filter(Boolean).join(' ').toLocaleLowerCase('ru').includes(search);
            })
            .sort((a, b) => a.fullName.localeCompare(b.fullName, 'ru'));
    }

    function draw() {
        const items = filtered();
        resultCount.textContent = `Найдено ${items.length} ${plural(items.length, 'сотрудник', 'сотрудника', 'сотрудников')}`;

        if (!items.length) {
            tableWrap.innerHTML = emptyState(
                'users',
                'Сотрудники не найдены',
                'Измените фильтры или создайте новую учётную запись.',
                `<button class="btn btn-primary" type="button" data-empty-new>${icon('userPlus', 16)} Новый сотрудник</button>`
            );
            tableWrap.querySelector('[data-empty-new]')?.addEventListener('click', () => openForm());
            return;
        }

        tableWrap.innerHTML = `
            <table class="data-table">
                <thead>
                    <tr>
                        <th>Сотрудник</th>
                        <th>Логин</th>
                        <th>Контакты</th>
                        <th>Роли</th>
                        <th>Доступ</th>
                        <th></th>
                    </tr>
                </thead>
                <tbody>
                    ${items.map(user => `
                        <tr>
                            <td>
                                <div class="table-person">
                                    <span class="avatar avatar-sm">${esc((user.fullName || '')[0] || '')}</span>
                                    <strong>${esc(user.fullName)}</strong>
                                </div>
                            </td>
                            <td><code class="inline-code">${esc(user.username)}</code></td>
                            <td>
                                <div class="table-stack">
                                    <span>${esc(user.phone || '—')}</span>
                                    <small>${esc(user.email || '')}</small>
                                </div>
                            </td>
                            <td>
                                <div class="role-badges">
                                    ${user.roles.map(role => `<span class="role-badge role-${role.toLowerCase()}">${esc(roleLabel(role))}</span>`).join('')}
                                </div>
                            </td>
                            <td>
                                <span class="active-pill ${user.enabled ? 'is-active' : 'is-inactive'}">${user.enabled ? 'Активен' : 'Заблокирован'}</span>
                            </td>
                            <td class="table-actions">
                                <button class="icon-btn" type="button" data-edit="${user.id}" title="Изменить" aria-label="Изменить">${icon('edit', 17)}</button>
                                ${user.id === ctx.user.id
                                    ? `<button class="icon-btn" type="button" disabled title="Нельзя удалить текущую учётную запись">${icon('trash', 17)}</button>`
                                    : `<button class="icon-btn icon-btn-danger" type="button" data-delete="${user.id}" title="Удалить" aria-label="Удалить">${icon('trash', 17)}</button>`}
                            </td>
                        </tr>`).join('')}
                </tbody>
            </table>`;

        tableWrap.querySelectorAll('[data-edit]').forEach(button => {
            button.addEventListener('click', () => {
                const user = users.find(item => item.id === Number(button.dataset.edit));
                if (user) openForm(user);
            });
        });
        tableWrap.querySelectorAll('[data-delete]').forEach(button => {
            button.addEventListener('click', () => {
                const user = users.find(item => item.id === Number(button.dataset.delete));
                if (user) deleteUser(user);
            });
        });
    }

    function openForm(user = null) {
        const isEdit = Boolean(user);
        const formId = `user-form-${Date.now()}`;
        const selectedRoles = new Set(user?.roles || ['RECEPTIONIST']);

        openModal({
            title: isEdit ? 'Изменить сотрудника' : 'Новый сотрудник',
            subtitle: isEdit ? `Учётная запись ${user.username}` : 'Пароль должен содержать не менее 8 символов',
            size: 'md',
            body: `
                <form id="${formId}" class="form-stack" novalidate>
                    <div class="form-grid">
                        <label class="field">
                            <span class="field-label">Фамилия и имя <span class="required">*</span></span>
                            <input class="input" name="fullName" maxlength="255" value="${esc(user?.fullName || '')}" placeholder="Иванов Иван" required>
                        </label>
                        <label class="field">
                            <span class="field-label">Логин <span class="required">*</span></span>
                            <input class="input" name="username" minlength="3" maxlength="100" value="${esc(user?.username || '')}" autocomplete="off" required>
                        </label>
                        <label class="field">
                            <span class="field-label">${isEdit ? 'Новый пароль' : 'Пароль'} ${isEdit ? '' : '<span class="required">*</span>'}</span>
                            <input class="input" type="password" name="password" minlength="8" maxlength="100"
                                   autocomplete="new-password" placeholder="${isEdit ? 'Оставьте пустым, чтобы не менять' : 'Не менее 8 символов'}" ${isEdit ? '' : 'required'}>
                        </label>
                        <label class="field">
                            <span class="field-label">Телефон</span>
                            <input class="input" name="phone" maxlength="30" value="${esc(user?.phone || '')}" placeholder="+7 900 000-00-00">
                        </label>
                        <label class="field field-span-2">
                            <span class="field-label">Email</span>
                            <input class="input" type="email" name="email" maxlength="150" value="${esc(user?.email || '')}" placeholder="employee@clinic.ru">
                        </label>
                    </div>

                    <div class="field">
                        <span class="field-label">Роли <span class="required">*</span></span>
                        <div class="role-options">
                            ${['ADMIN', 'DOCTOR', 'RECEPTIONIST'].map(role => `
                                <label class="role-option">
                                    <input type="checkbox" name="role" value="${role}" ${selectedRoles.has(role) ? 'checked' : ''}>
                                    <span>
                                        <strong>${esc(roleLabel(role))}</strong>
                                        <small>${role === 'ADMIN' ? 'Полный доступ и настройки' : role === 'DOCTOR' ? 'Кабинет врача и свои приёмы' : 'Записи, пациенты и календарь'}</small>
                                    </span>
                                </label>`).join('')}
                        </div>
                    </div>

                    <label class="checkbox-field">
                        <input type="checkbox" name="enabled" ${user?.enabled === false ? '' : 'checked'}>
                        <span><strong>Учётная запись активна</strong><small>Заблокированный сотрудник не сможет войти.</small></span>
                    </label>

                    <div class="form-error hidden" data-form-error role="alert"></div>
                </form>`,
            footer: `
                <button class="btn btn-secondary" type="button" data-dismiss>Отмена</button>
                <button class="btn btn-primary" type="submit" form="${formId}" data-submit>
                    ${isEdit ? 'Сохранить изменения' : 'Создать сотрудника'}
                </button>`,
            onMount: ({ root, close }) => {
                const form = root.querySelector(`#${formId}`);
                const errorBox = root.querySelector('[data-form-error]');
                const submitButton = root.querySelector('[data-submit]');
                root.querySelector('[data-dismiss]').addEventListener('click', close);

                form.addEventListener('submit', async event => {
                    event.preventDefault();
                    errorBox.classList.add('hidden');
                    const roles = [...form.querySelectorAll('[name="role"]:checked')].map(input => input.value);
                    const payload = {
                        username: form.elements.username.value.trim(),
                        fullName: form.elements.fullName.value.trim(),
                        email: form.elements.email.value.trim() || null,
                        phone: form.elements.phone.value.trim() || null,
                        enabled: form.elements.enabled.checked,
                        roles
                    };
                    const password = form.elements.password.value;
                    if (!payload.username || !payload.fullName) {
                        errorBox.textContent = 'Заполните имя и логин.';
                        errorBox.classList.remove('hidden');
                        return;
                    }
                    if (!isEdit && password.length < 8) {
                        errorBox.textContent = 'Пароль должен содержать не менее 8 символов.';
                        errorBox.classList.remove('hidden');
                        return;
                    }
                    if (isEdit && password && password.length < 8) {
                        errorBox.textContent = 'Новый пароль должен содержать не менее 8 символов.';
                        errorBox.classList.remove('hidden');
                        return;
                    }
                    if (!roles.length) {
                        errorBox.textContent = 'Выберите хотя бы одну роль.';
                        errorBox.classList.remove('hidden');
                        return;
                    }
                    if (password) {
                        payload.password = password;
                    }

                    submitButton.disabled = true;
                    submitButton.dataset.originalHtml = submitButton.innerHTML;
                    submitButton.innerHTML = '<span class="spinner"></span>Сохраняем…';
                    try {
                        if (isEdit) {
                            await api(`/admin/users/${user.id}`, { method: 'PUT', body: payload });
                        } else {
                            await api('/admin/users', { method: 'POST', body: payload });
                        }
                        toast(isEdit ? 'Сотрудник обновлён' : 'Сотрудник создан');
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

    async function deleteUser(user) {
        const confirmed = await confirmDialog({
            title: 'Удалить сотрудника?',
            message: `Учётная запись «${user.fullName}» будет удалена. Если с ней связан профиль врача, удаление будет отклонено.`,
            confirmLabel: 'Удалить',
            danger: true
        });
        if (!confirmed) {
            return;
        }
        try {
            await api(`/admin/users/${user.id}`, { method: 'DELETE' });
            toast('Сотрудник удалён');
            await reload();
        } catch (error) {
            toast(mapApiError(error), 'error');
        }
    }

    async function reload() {
        try {
            users = await api('/admin/users');
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
    roleSelect.addEventListener('change', () => {
        state.role = roleSelect.value;
        draw();
    });
    container.querySelector('[data-reset]').addEventListener('click', () => {
        state.search = '';
        state.role = '';
        searchInput.value = '';
        roleSelect.value = '';
        draw();
    });
    container.querySelector('[data-new]').addEventListener('click', () => openForm());
    container.querySelector('[data-refresh]').addEventListener('click', reload);

    renderStats();
    draw();
}
