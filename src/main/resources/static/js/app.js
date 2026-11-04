import { api, login, session } from './api.js';
import {
    confirmDialog,
    emptyState,
    esc,
    icon,
    initials,
    mapApiError,
    primaryRole,
    roleLabel,
    toast
} from './ui.js';
import * as dashboardView from './views/dashboard.js';
import * as calendarView from './views/calendar.js';
import * as appointmentsView from './views/appointments.js';
import * as patientsView from './views/patients.js';
import * as doctorsView from './views/doctors.js';
import * as specializationsView from './views/specializations.js';
import * as servicesView from './views/services.js';
import * as reportsView from './views/reports.js';
import * as usersView from './views/users.js';
import * as doctorView from './views/doctor.js';

const ROUTES = {
    dashboard: {
        path: 'dashboard',
        label: 'Обзор',
        title: 'Обзор',
        eyebrow: 'Рабочее место',
        icon: 'dashboard',
        roles: ['ADMIN', 'RECEPTIONIST', 'DOCTOR'],
        render: dashboardView.render
    },
    calendar: {
        path: 'calendar',
        label: 'Календарь',
        title: 'Календарь приёмов',
        eyebrow: 'Расписание клиники',
        icon: 'calendar',
        roles: ['ADMIN', 'RECEPTIONIST'],
        render: calendarView.render
    },
    appointments: {
        path: 'appointments',
        label: 'Записи',
        title: 'Записи на приём',
        eyebrow: 'Приёмы и визиты',
        icon: 'clipboard',
        roles: ['ADMIN', 'RECEPTIONIST', 'DOCTOR'],
        render: appointmentsView.render
    },
    patients: {
        path: 'patients',
        label: 'Пациенты',
        title: 'Пациенты',
        eyebrow: 'Картотека клиники',
        icon: 'patients',
        roles: ['ADMIN', 'RECEPTIONIST'],
        render: patientsView.render
    },
    doctors: {
        path: 'doctors',
        label: 'Врачи',
        title: 'Врачи',
        eyebrow: 'Медицинский персонал',
        icon: 'doctor',
        roles: ['ADMIN', 'RECEPTIONIST'],
        render: doctorsView.render
    },
    specializations: {
        path: 'specializations',
        label: 'Специализации',
        title: 'Специализации',
        eyebrow: 'Справочник клиники',
        icon: 'layers',
        roles: ['ADMIN'],
        render: specializationsView.render
    },
    services: {
        path: 'services',
        label: 'Услуги',
        title: 'Медицинские услуги',
        eyebrow: 'Прайс клиники',
        icon: 'briefcase',
        roles: ['ADMIN', 'RECEPTIONIST', 'DOCTOR'],
        render: servicesView.render
    },
    reports: {
        path: 'reports',
        label: 'Отчёты',
        title: 'Отчёты',
        eyebrow: 'Аналитика клиники',
        icon: 'chart',
        roles: ['ADMIN'],
        render: reportsView.render
    },
    users: {
        path: 'users',
        label: 'Сотрудники',
        title: 'Сотрудники',
        eyebrow: 'Доступ и роли',
        icon: 'users',
        roles: ['ADMIN'],
        render: usersView.render
    },
    schedule: {
        path: 'schedule',
        label: 'Моё расписание',
        title: 'Моё расписание',
        eyebrow: 'Кабинет врача',
        icon: 'clock',
        roles: ['DOCTOR'],
        render: doctorView.renderSchedule
    },
    profile: {
        path: 'profile',
        label: 'Мой профиль',
        title: 'Мой профиль',
        eyebrow: 'Кабинет врача',
        icon: 'user',
        roles: ['DOCTOR'],
        render: doctorView.renderProfile
    }
};

const NAV_GROUPS = [
    { label: 'Работа', items: ['dashboard', 'calendar', 'appointments', 'patients'] },
    { label: 'Клиника', items: ['doctors', 'specializations', 'services'] },
    { label: 'Управление', items: ['reports', 'users'] },
    { label: 'Кабинет врача', items: ['schedule', 'profile'] }
];

const loginScreen = document.getElementById('login-screen');
const app = document.getElementById('app');
const loginForm = document.getElementById('login-form');
const loginError = document.getElementById('login-error');
const loginSubmit = document.getElementById('login-submit');
const navRoot = document.getElementById('sidebar-nav');
const roleRoot = document.getElementById('sidebar-role');
const sidebarUser = document.getElementById('sidebar-user');
const topbarUser = document.getElementById('topbar-user');
const pageTitle = document.getElementById('page-title');
const pageEyebrow = document.getElementById('page-eyebrow');
const view = document.getElementById('view');
const logoutButton = document.getElementById('logout-btn');
const menuButton = document.getElementById('menu-btn');
const sidebarBackdrop = document.getElementById('sidebar-backdrop');

let routeVersion = 0;
let currentRole = null;

function parseHash() {
    const raw = window.location.hash.replace(/^#\/?/, '');
    const [pathPart, queryPart = ''] = raw.split('?');
    return {
        name: pathPart || 'dashboard',
        params: new URLSearchParams(queryPart)
    };
}

function navigate(name, params = {}) {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
            query.set(key, value);
        }
    });
    const suffix = query.toString() ? `?${query}` : '';
    const hash = `#/${name}${suffix}`;
    if (window.location.hash === hash) {
        router();
    } else {
        window.location.hash = hash;
    }
}

function routeFor(name) {
    return ROUTES[name] || null;
}

function routeTitle(route, role) {
    if (route.path === 'appointments' && role === 'DOCTOR') {
        return 'Мои приёмы';
    }
    return route.title;
}

function routeLabel(route, role) {
    if (route.path === 'appointments' && role === 'DOCTOR') {
        return 'Мои приёмы';
    }
    return route.label;
}

function buildChrome(user, role) {
    roleRoot.innerHTML = `<span class="sidebar-role-dot"></span>${esc(roleLabel(role))}`;
    sidebarUser.innerHTML = `
        <span class="avatar avatar-sm">${esc(initials(user.fullName))}</span>
        <span class="sidebar-user-text">
            <strong>${esc(user.fullName)}</strong>
            <small>${esc(user.username)}</small>
        </span>`;
    topbarUser.innerHTML = `
        <span class="topbar-user-text">
            <strong>${esc(user.fullName)}</strong>
            <small>${esc(roleLabel(role))}</small>
        </span>
        <span class="avatar avatar-sm">${esc(initials(user.fullName))}</span>`;

    navRoot.innerHTML = NAV_GROUPS.map(group => {
        const items = group.items
            .map(key => ROUTES[key])
            .filter(route => route && route.roles.includes(role));
        if (!items.length) {
            return '';
        }
        return `
            <div class="nav-group">
                <span class="nav-group-label">${esc(group.label)}</span>
                ${items.map(route => `
                    <a class="nav-item" href="#/${route.path}" data-nav="${route.path}">
                        ${icon(route.icon, 19)}
                        <span>${esc(routeLabel(route, role))}</span>
                    </a>`).join('')}
            </div>`;
    }).join('');
}

function markActiveNav(name) {
    navRoot.querySelectorAll('[data-nav]').forEach(link => {
        link.classList.toggle('active', link.dataset.nav === name);
    });
}

function closeMobileSidebar() {
    app.classList.remove('sidebar-open');
    sidebarBackdrop.hidden = true;
}

function showLogin() {
    session.clear();
    app.classList.add('hidden');
    loginScreen.classList.remove('hidden');
    loginError.classList.add('hidden');
    loginError.textContent = '';
    closeMobileSidebar();
    window.setTimeout(() => document.getElementById('login-username')?.focus(), 50);
}

function showApp() {
    loginScreen.classList.add('hidden');
    app.classList.remove('hidden');
}

function updateMeta(route, role) {
    const title = routeTitle(route, role);
    pageTitle.textContent = title;
    pageEyebrow.textContent = route.eyebrow;
    document.title = `МедРасписание — ${title}`;
}

async function router() {
    const current = session.get();
    if (!current) {
        showLogin();
        return;
    }

    showApp();
    const user = current.user;
    const role = primaryRole(user);
    currentRole = role;
    buildChrome(user, role);

    const { name, params } = parseHash();
    let route = routeFor(name);

    if (!route) {
        navigate('dashboard');
        return;
    }
    if (!route.roles.includes(role)) {
        toast('У этого раздела нет доступа для вашей роли', 'error');
        navigate('dashboard');
        return;
    }

    updateMeta(route, role);
    markActiveNav(route.path);
    closeMobileSidebar();

    const version = ++routeVersion;
    const page = document.createElement('div');
    page.className = 'page';
    view.replaceChildren(page);
    window.scrollTo({ top: 0, behavior: 'instant' });

    const ctx = {
        user,
        role,
        params,
        navigate
    };

    try {
        await route.render(page, ctx);
    } catch (error) {
        if (version !== routeVersion) {
            return;
        }
        page.innerHTML = emptyState(
            'alert',
            'Не удалось открыть раздел',
            mapApiError(error),
            `<button class="btn btn-primary" type="button" data-retry>Повторить</button>`
        );
        page.querySelector('[data-retry]')?.addEventListener('click', () => router());
    }
}

async function handleLogin(event) {
    event.preventDefault();
    loginError.classList.add('hidden');

    const username = loginForm.elements.username.value.trim();
    const password = loginForm.elements.password.value;
    if (!username || !password) {
        loginError.textContent = 'Введите логин и пароль.';
        loginError.classList.remove('hidden');
        return;
    }

    loginSubmit.disabled = true;
    loginSubmit.dataset.originalHtml = loginSubmit.innerHTML;
    loginSubmit.innerHTML = '<span class="spinner spinner-light"></span>Входим…';
    try {
        const response = await login(username, password);
        session.set(response);
        loginForm.elements.password.value = '';
        showApp();
        if (!window.location.hash || window.location.hash === '#/') {
            window.location.hash = '#/dashboard';
        } else {
            router();
        }
        toast(`Добро пожаловать, ${response.user.fullName}`);
    } catch (error) {
        loginError.textContent = mapApiError(error);
        loginError.classList.remove('hidden');
    } finally {
        loginSubmit.disabled = false;
        loginSubmit.innerHTML = loginSubmit.dataset.originalHtml;
    }
}

async function handleLogout() {
    const confirmed = await confirmDialog({
        title: 'Выйти из системы?',
        message: 'Текущая сессия будет завершена. Для продолжения работы потребуется снова войти.',
        confirmLabel: 'Выйти',
        danger: true
    });
    if (!confirmed) {
        return;
    }
    session.clear();
    showLogin();
    toast('Вы вышли из системы', 'info');
}

loginForm.addEventListener('submit', handleLogin);
logoutButton.addEventListener('click', handleLogout);

menuButton.addEventListener('click', () => {
    const open = !app.classList.contains('sidebar-open');
    app.classList.toggle('sidebar-open', open);
    sidebarBackdrop.hidden = !open;
});
sidebarBackdrop.addEventListener('click', closeMobileSidebar);

navRoot.addEventListener('click', event => {
    if (event.target.closest('a')) {
        closeMobileSidebar();
    }
});

window.addEventListener('hashchange', router);
window.addEventListener('medschedule:unauthorized', () => {
    if (!session.get()) {
        showLogin();
        toast('Сессия истекла. Войдите снова.', 'error');
    }
});

const existing = session.get();
if (existing) {
    showApp();
    if (!window.location.hash) {
        window.location.hash = '#/dashboard';
    } else {
        router();
    }
} else {
    showLogin();
}
