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
    toast
} from '../ui.js';

export async function render(container, ctx) {
    container.innerHTML = loadingState('Загружаем специализации…');

    let specializations;
    let doctors;
    try {
        [specializations, doctors] = await Promise.all([
            api('/specializations'),
            api('/doctors')
        ]);
    } catch (error) {
        container.innerHTML = emptyState('alert', 'Не удалось загрузить специализации', mapApiError(error));
        return;
    }

    let search = '';
    const canEdit = ctx.role === 'ADMIN';

    container.innerHTML = `
        <div class="page-head">
            <div>
                <span class="eyebrow">Справочник клиники</span>
                <h1>Специализации</h1>
                <p>Направления медицинской помощи, к которым привязаны профили врачей.</p>
            </div>
            <div class="page-actions">
                <button class="btn btn-secondary" type="button" data-refresh>${icon('refresh', 17)} Обновить</button>
                ${canEdit ? `<button class="btn btn-primary" type="button" data-new>${icon('plus', 18)} Новая специализация</button>` : ''}
            </div>
        </div>

        <section class="mini-stats" data-mini-stats></section>

        <section class="card filter-card">
            <div class="filters filters-compact">
                <label class="field field-search field-grow">
                    <span class="field-label">Поиск</span>
                    <span class="input-icon-wrap">
                        ${icon('search', 17)}
                        <input class="input" type="search" data-search placeholder="Название или описание">
                    </span>
                </label>
                <button class="btn btn-ghost filter-reset" type="button" data-reset>${icon('close', 16)} Сбросить</button>
            </div>
            <div class="filter-meta"><span data-result-count></span></div>
        </section>

        <section class="specialization-grid" data-grid></section>`;

    const grid = container.querySelector('[data-grid]');
    const searchInput = container.querySelector('[data-search]');
    const resultCount = container.querySelector('[data-result-count]');
    const miniStats = container.querySelector('[data-mini-stats]');

    function countDoctors(specializationId) {
        return doctors.filter(doctor => doctor.specializationId === specializationId).length;
    }

    function renderStats() {
        const used = specializations.filter(item => countDoctors(item.id) > 0).length;
        miniStats.innerHTML = `
            <span class="mini-stat"><strong>${specializations.length}</strong> ${plural(specializations.length, 'направление', 'направления', 'направлений')}</span>
            <span class="mini-stat"><strong>${used}</strong> используется врачами</span>
            <span class="mini-stat"><strong>${doctors.length}</strong> ${plural(doctors.length, 'врач', 'врача', 'врачей')}</span>`;
    }

    function filtered() {
        const query = search.trim().toLocaleLowerCase('ru');
        return specializations
            .filter(item => !query || [item.name, item.description]
                .filter(Boolean).join(' ').toLocaleLowerCase('ru').includes(query))
            .sort((a, b) => a.name.localeCompare(b.name, 'ru'));
    }

    function draw() {
        const items = filtered();
        resultCount.textContent = `Найдено ${items.length} ${plural(items.length, 'специализация', 'специализации', 'специализаций')}`;

        if (!items.length) {
            grid.innerHTML = emptyState(
                'layers',
                'Специализации не найдены',
                'Добавьте направление, чтобы создать профиль врача.',
                canEdit
                    ? `<button class="btn btn-primary" type="button" data-empty-new>${icon('plus', 16)} Новая специализация</button>`
                    : ''
            );
            grid.querySelector('[data-empty-new]')?.addEventListener('click', () => openForm());
            return;
        }

        grid.innerHTML = items.map(item => {
            const doctorCount = countDoctors(item.id);
            return `
                <article class="specialization-card">
                    <div class="specialization-card-head">
                        <span class="specialization-icon">${icon('stethoscope', 20)}</span>
                        <div>
                            <h3>${esc(item.name)}</h3>
                            <span>${doctorCount} ${plural(doctorCount, 'врач', 'врача', 'врачей')}</span>
                        </div>
                    </div>
                    <p>${esc(item.description || 'Описание направления пока не добавлено.')}</p>
                    ${canEdit ? `
                        <div class="specialization-actions">
                            <button class="btn btn-secondary btn-sm" type="button" data-edit="${item.id}">${icon('edit', 15)} Изменить</button>
                            <button class="icon-btn icon-btn-danger" type="button" data-delete="${item.id}" title="Удалить" aria-label="Удалить">${icon('trash', 17)}</button>
                        </div>` : ''}
                </article>`;
        }).join('');

        grid.querySelectorAll('[data-edit]').forEach(button => {
            button.addEventListener('click', () => {
                const item = specializations.find(specialization => specialization.id === Number(button.dataset.edit));
                if (item) openForm(item);
            });
        });
        grid.querySelectorAll('[data-delete]').forEach(button => {
            button.addEventListener('click', () => {
                const item = specializations.find(specialization => specialization.id === Number(button.dataset.delete));
                if (item) deleteSpecialization(item);
            });
        });
    }

    function openForm(item = null) {
        if (!canEdit) {
            return;
        }
        const isEdit = Boolean(item);
        const formId = `specialization-form-${Date.now()}`;
        openModal({
            title: isEdit ? 'Изменить специализацию' : 'Новая специализация',
            subtitle: 'Название должно быть уникальным',
            size: 'md',
            body: `
                <form id="${formId}" class="form-stack" novalidate>
                    <label class="field">
                        <span class="field-label">Название <span class="required">*</span></span>
                        <input class="input" name="name" maxlength="150" value="${esc(item?.name || '')}"
                               placeholder="Например, Кардиология" required>
                    </label>
                    <label class="field">
                        <span class="field-label">Описание</span>
                        <textarea class="input textarea" name="description" maxlength="1000" rows="4"
                                  placeholder="Краткое описание направления">${esc(item?.description || '')}</textarea>
                    </label>
                    <div class="form-error hidden" data-form-error role="alert"></div>
                </form>`,
            footer: `
                <button class="btn btn-secondary" type="button" data-dismiss>Отмена</button>
                <button class="btn btn-primary" type="submit" form="${formId}" data-submit>
                    ${isEdit ? 'Сохранить' : 'Создать'}
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
                        description: form.elements.description.value.trim() || null
                    };
                    if (!payload.name) {
                        errorBox.textContent = 'Укажите название специализации.';
                        errorBox.classList.remove('hidden');
                        return;
                    }

                    submitButton.disabled = true;
                    submitButton.dataset.originalHtml = submitButton.innerHTML;
                    submitButton.innerHTML = '<span class="spinner"></span>Сохраняем…';
                    try {
                        if (isEdit) {
                            await api(`/specializations/${item.id}`, { method: 'PUT', body: payload });
                        } else {
                            await api('/specializations', { method: 'POST', body: payload });
                        }
                        toast(isEdit ? 'Специализация обновлена' : 'Специализация создана');
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

    async function deleteSpecialization(item) {
        const confirmed = await confirmDialog({
            title: 'Удалить специализацию?',
            message: `«${item.name}» будет удалена. Если к ней привязаны врачи, удаление будет отклонено.`,
            confirmLabel: 'Удалить',
            danger: true
        });
        if (!confirmed) {
            return;
        }
        try {
            await api(`/specializations/${item.id}`, { method: 'DELETE' });
            toast('Специализация удалена');
            await reload();
        } catch (error) {
            toast(mapApiError(error), 'error');
        }
    }

    async function reload() {
        try {
            [specializations, doctors] = await Promise.all([
                api('/specializations'),
                api('/doctors')
            ]);
            renderStats();
            draw();
        } catch (error) {
            toast(mapApiError(error), 'error');
        }
    }

    searchInput.addEventListener('input', debounce(() => {
        search = searchInput.value;
        draw();
    }, 220));
    container.querySelector('[data-reset]').addEventListener('click', () => {
        search = '';
        searchInput.value = '';
        draw();
    });
    container.querySelector('[data-new]')?.addEventListener('click', () => openForm());
    container.querySelector('[data-refresh]').addEventListener('click', reload);

    renderStats();
    draw();
}
