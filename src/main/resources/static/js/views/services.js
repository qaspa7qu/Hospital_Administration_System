import { api } from '../api.js';
import {
    confirmDialog,
    debounce,
    emptyState,
    esc,
    fmtMoney,
    icon,
    loadingState,
    mapApiError,
    openModal,
    plural,
    toast
} from '../ui.js';

export async function render(container, ctx) {
    container.innerHTML = loadingState('Загружаем справочник услуг…');

    let services;
    try {
        services = await api('/services');
    } catch (error) {
        container.innerHTML = emptyState('alert', 'Не удалось загрузить услуги', mapApiError(error));
        return;
    }

    const canEdit = ctx.role === 'ADMIN';
    const state = { search: '', activity: '' };

    container.innerHTML = `
        <div class="page-head">
            <div>
                <span class="eyebrow">Прайс клиники</span>
                <h1>Медицинские услуги</h1>
                <p>Стоимость и длительность услуг, которые можно привязать к приёму.</p>
            </div>
            <div class="page-actions">
                <button class="btn btn-secondary" type="button" data-refresh>${icon('refresh', 17)} Обновить</button>
                ${canEdit ? `<button class="btn btn-primary" type="button" data-new>${icon('plus', 18)} Новая услуга</button>` : ''}
            </div>
        </div>

        ${!canEdit ? `
            <div class="notice notice-info">
                ${icon('info', 18)}
                <span>Справочник доступен для просмотра. Изменять услуги может только администратор клиники.</span>
            </div>` : ''}

        <section class="mini-stats" data-mini-stats></section>

        <section class="card filter-card">
            <div class="filters filters-compact">
                <label class="field field-search field-grow">
                    <span class="field-label">Поиск услуги</span>
                    <span class="input-icon-wrap">
                        ${icon('search', 17)}
                        <input class="input" type="search" data-search placeholder="Название или описание">
                    </span>
                </label>
                <label class="field">
                    <span class="field-label">Статус</span>
                    <select class="input" data-activity>
                        <option value="">Все услуги</option>
                        <option value="active">Только активные</option>
                        <option value="inactive">Только неактивные</option>
                    </select>
                </label>
                <button class="btn btn-ghost filter-reset" type="button" data-reset>${icon('close', 16)} Сбросить</button>
            </div>
            <div class="filter-meta"><span data-result-count></span></div>
        </section>

        <section class="service-grid" data-grid></section>`;

    const grid = container.querySelector('[data-grid]');
    const searchInput = container.querySelector('[data-search]');
    const activitySelect = container.querySelector('[data-activity]');
    const resultCount = container.querySelector('[data-result-count]');
    const miniStats = container.querySelector('[data-mini-stats]');

    function renderStats() {
        const active = services.filter(item => item.active).length;
        const free = services.filter(item => Number(item.price) === 0).length;
        const average = services.length
            ? services.reduce((sum, item) => sum + Number(item.price || 0), 0) / services.length
            : 0;
        miniStats.innerHTML = `
            <span class="mini-stat"><strong>${services.length}</strong> ${plural(services.length, 'услуга', 'услуги', 'услуг')}</span>
            <span class="mini-stat mini-stat-completed"><strong>${active}</strong> активных</span>
            <span class="mini-stat"><strong>${free}</strong> бесплатных</span>
            <span class="mini-stat"><strong>${fmtMoney(average)}</strong> средняя цена</span>`;
    }

    function filtered() {
        const search = state.search.trim().toLocaleLowerCase('ru');
        return services
            .filter(item => {
                if (state.activity === 'active' && !item.active) return false;
                if (state.activity === 'inactive' && item.active) return false;
                if (!search) return true;
                return [item.name, item.description]
                    .filter(Boolean).join(' ').toLocaleLowerCase('ru').includes(search);
            })
            .sort((a, b) => a.name.localeCompare(b.name, 'ru'));
    }

    function draw() {
        const items = filtered();
        resultCount.textContent = `Найдено ${items.length} ${plural(items.length, 'услуга', 'услуги', 'услуг')}`;

        if (!items.length) {
            grid.innerHTML = emptyState(
                'briefcase',
                'Услуги не найдены',
                'Измените фильтры или добавьте услугу в справочник.',
                canEdit
                    ? `<button class="btn btn-primary" type="button" data-empty-new>${icon('plus', 16)} Новая услуга</button>`
                    : ''
            );
            grid.querySelector('[data-empty-new]')?.addEventListener('click', () => openForm());
            return;
        }

        grid.innerHTML = items.map(item => `
            <article class="service-card ${item.active ? '' : 'is-inactive'}">
                <div class="service-card-head">
                    <span class="service-icon">${icon('briefcase', 20)}</span>
                    <span class="active-pill ${item.active ? 'is-active' : 'is-inactive'}">${item.active ? 'Активна' : 'Неактивна'}</span>
                </div>
                <h3>${esc(item.name)}</h3>
                <p>${esc(item.description || 'Описание услуги не добавлено.')}</p>
                <div class="service-meta">
                    <span class="service-price">${Number(item.price) === 0 ? 'Бесплатно' : fmtMoney(item.price)}</span>
                    <span class="service-duration">${icon('clock', 15)} ${item.durationMinutes} мин</span>
                </div>
                ${canEdit ? `
                    <div class="service-actions">
                        <button class="btn btn-secondary btn-sm" type="button" data-edit="${item.id}">${icon('edit', 15)} Изменить</button>
                        <button class="icon-btn icon-btn-danger" type="button" data-delete="${item.id}" title="Удалить" aria-label="Удалить">${icon('trash', 17)}</button>
                    </div>` : ''}
            </article>`).join('');

        grid.querySelectorAll('[data-edit]').forEach(button => {
            button.addEventListener('click', () => {
                const item = services.find(service => service.id === Number(button.dataset.edit));
                if (item) openForm(item);
            });
        });
        grid.querySelectorAll('[data-delete]').forEach(button => {
            button.addEventListener('click', () => {
                const item = services.find(service => service.id === Number(button.dataset.delete));
                if (item) deleteService(item);
            });
        });
    }

    function openForm(item = null) {
        if (!canEdit) {
            return;
        }
        const isEdit = Boolean(item);
        const formId = `service-form-${Date.now()}`;
        openModal({
            title: isEdit ? 'Изменить услугу' : 'Новая услуга',
            subtitle: 'Цена фиксируется в записи в момент добавления услуги',
            size: 'md',
            body: `
                <form id="${formId}" class="form-stack" novalidate>
                    <label class="field">
                        <span class="field-label">Название <span class="required">*</span></span>
                        <input class="input" name="name" maxlength="200" value="${esc(item?.name || '')}"
                               placeholder="Например, Консультация кардиолога" required>
                    </label>
                    <label class="field">
                        <span class="field-label">Описание</span>
                        <textarea class="input textarea" name="description" maxlength="1000" rows="3"
                                  placeholder="Что входит в услугу">${esc(item?.description || '')}</textarea>
                    </label>
                    <div class="form-grid">
                        <label class="field">
                            <span class="field-label">Цена, ₽ <span class="required">*</span></span>
                            <input class="input" type="number" name="price" min="0" max="99999999.99" step="0.01"
                                   value="${item ? Number(item.price).toFixed(2) : '0.00'}" required>
                            <span class="field-hint">Для бесплатной услуги укажите 0.00.</span>
                        </label>
                        <label class="field">
                            <span class="field-label">Длительность, минут <span class="required">*</span></span>
                            <input class="input" type="number" name="durationMinutes" min="5" max="480" step="5"
                                   value="${item?.durationMinutes || 30}" required>
                        </label>
                    </div>
                    <label class="checkbox-field">
                        <input type="checkbox" name="active" ${item?.active === false ? '' : 'checked'}>
                        <span><strong>Услуга активна</strong><small>Неактивную услугу нельзя добавить к новой записи.</small></span>
                    </label>
                    <div class="form-error hidden" data-form-error role="alert"></div>
                </form>`,
            footer: `
                <button class="btn btn-secondary" type="button" data-dismiss>Отмена</button>
                <button class="btn btn-primary" type="submit" form="${formId}" data-submit>
                    ${isEdit ? 'Сохранить' : 'Создать услугу'}
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
                        name: form.elements.name.value.trim(),
                        description: form.elements.description.value.trim() || null,
                        price: Number(form.elements.price.value),
                        durationMinutes: Number(form.elements.durationMinutes.value),
                        active: form.elements.active.checked
                    };
                    if (!payload.name) {
                        errorBox.textContent = 'Укажите название услуги.';
                        errorBox.classList.remove('hidden');
                        return;
                    }
                    if (!Number.isFinite(payload.price) || payload.price < 0) {
                        errorBox.textContent = 'Цена не может быть отрицательной.';
                        errorBox.classList.remove('hidden');
                        return;
                    }
                    if (payload.durationMinutes < 5 || payload.durationMinutes > 480) {
                        errorBox.textContent = 'Длительность услуги должна быть от 5 до 480 минут.';
                        errorBox.classList.remove('hidden');
                        return;
                    }

                    submitButton.disabled = true;
                    submitButton.dataset.originalHtml = submitButton.innerHTML;
                    submitButton.innerHTML = '<span class="spinner"></span>Сохраняем…';
                    try {
                        if (isEdit) {
                            await api(`/services/${item.id}`, { method: 'PUT', body: payload });
                        } else {
                            await api('/services', { method: 'POST', body: payload });
                        }
                        toast(isEdit ? 'Услуга обновлена' : 'Услуга добавлена');
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

    async function deleteService(item) {
        const confirmed = await confirmDialog({
            title: 'Удалить услугу?',
            message: `«${item.name}» будет удалена из справочника. Если услуга используется в записях, удаление будет отклонено.`,
            confirmLabel: 'Удалить',
            danger: true
        });
        if (!confirmed) {
            return;
        }
        try {
            await api(`/services/${item.id}`, { method: 'DELETE' });
            toast('Услуга удалена');
            await reload();
        } catch (error) {
            toast(mapApiError(error), 'error');
        }
    }

    async function reload() {
        try {
            services = await api('/services');
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
    activitySelect.addEventListener('change', () => {
        state.activity = activitySelect.value;
        draw();
    });
    container.querySelector('[data-reset]').addEventListener('click', () => {
        state.search = '';
        state.activity = '';
        searchInput.value = '';
        activitySelect.value = '';
        draw();
    });
    container.querySelector('[data-new]')?.addEventListener('click', () => openForm());
    container.querySelector('[data-refresh]').addEventListener('click', reload);

    renderStats();
    draw();
}
