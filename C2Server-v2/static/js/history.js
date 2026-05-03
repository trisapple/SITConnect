let map = null;
let routeLayers = [];
let markerLayers = [];
let allHistoryData = [];

function initMap() {
    if (map) map.remove();
    map = L.map('historyMap').setView([0, 0], 2);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
        maxZoom: 19
    }).addTo(map);
}

const colors = ['#ff8a80', '#ff80ab', '#ea80fc', '#b388ff', '#8c9eff', '#82b1ff', '#84ffff', '#a7ffeb', '#b9f6ca', '#ccff90', '#f4ff81', '#ffe57f', '#ffd180', '#ff9e80', '#ffffff', '#e0e0e0', '#f8bbd0', '#c8e6c9', '#b2dfdb', '#b3e5fc', '#fdcefc', '#f0f4c3'];

function getColorForClient(clientId) {
    let hash = 0;
    for (let i = 0; i < clientId.length; i++) {
        hash = clientId.charCodeAt(i) + ((hash << 5) - hash);
    }
    return colors[Math.abs(hash) % colors.length];
}

function drawHistoryMap(clientIdFilter) {
    routeLayers.forEach(layer => map.removeLayer(layer));
    markerLayers.forEach(layer => map.removeLayer(layer));
    routeLayers = [];
    markerLayers = [];

    const grouped = {};
    allHistoryData.forEach(item => {
        if (clientIdFilter !== 'all' && item.client_id !== clientIdFilter) return;
        if (!grouped[item.client_id]) grouped[item.client_id] = [];
        grouped[item.client_id].push(item);
    });

    const allLatLngs = [];

    Object.keys(grouped).forEach(clientId => {
        const points = grouped[clientId];
        points.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));

        const latLngs = points.map(p => [parseFloat(p.lat), parseFloat(p.lng)]);
        allLatLngs.push(...latLngs);

        const cColor = getColorForClient(clientId);

        const polyline = L.polyline(latLngs, {
            color: cColor,
            weight: 4,
            opacity: 0.8,
            dashArray: '5, 10'
        }).addTo(map);
        routeLayers.push(polyline);

        points.forEach((p, idx) => {
            const isLatest = idx === points.length - 1;

            const circleMarker = L.circleMarker([p.lat, p.lng], {
                radius: isLatest ? 8 : 4,
                fillColor: cColor,
                color: isLatest ? '#fff' : cColor,
                weight: isLatest ? 2 : 1,
                opacity: 1,
                fillOpacity: 0.8
            }).addTo(map);

            const ts = new Date(p.timestamp).toLocaleString();
            const popupContent = `
                <div style="min-width: 150px;">
                    <h4 style="margin: 0 0 5px 0; color: ${cColor};">${p.device_name || p.client_id}</h4>
                    <p style="margin: 3px 0; font-size: 12px;"><strong>Client ID:</strong> ${p.client_id}</p>
                    <p style="margin: 3px 0; font-size: 12px;"><strong>Time:</strong> ${ts}</p>
                    <p style="margin: 3px 0; font-size: 12px;"><strong>Lat:</strong> ${p.lat}</p>
                    <p style="margin: 3px 0; font-size: 12px;"><strong>Lng:</strong> ${p.lng}</p>
                    ${isLatest ? '<span style="background:var(--primary-color);color:#fff;padding:2px 6px;border-radius:10px;font-size:10px;">Latest Known Location</span>' : ''}
                    <br><button onclick="deleteHistoryEntry(${JSON.stringify(p.timestamp)})" style="margin-top:8px;padding:3px 8px;background:#c0392b;color:#fff;border:none;border-radius:4px;cursor:pointer;font-size:11px;"><i class="bi bi-trash"></i> Delete Entry</button>
                </div>
            `;
            circleMarker.bindPopup(popupContent);
            markerLayers.push(circleMarker);
        });
    });

    if (allLatLngs.length > 0) {
        const bounds = L.latLngBounds(allLatLngs);
        map.fitBounds(bounds, { padding: [50, 50] });
    } else {
        map.setView([0, 0], 2);
    }
}

let currentClientFilter = 'all';
const urlParams = new URLSearchParams(window.location.search);
if (urlParams.has('client_id')) {
    currentClientFilter = urlParams.get('client_id');
}

function renderClientList(clientsMap) {
    const clientsList = document.getElementById('clientsList');
    const clientCount = document.getElementById('clientCount');
    const clients = Object.keys(clientsMap).sort();

    clientCount.textContent = clients.length;

    if (clients.length === 0) {
        clientsList.innerHTML = '<p class="empty-message">No history available</p>';
        return;
    }

    clientsList.innerHTML = '';

    clients.forEach(clientId => {
        const clientCard = document.createElement('div');
        clientCard.className = 'client-card';
        if (currentClientFilter === clientId && currentClientFilter !== 'all') {
            clientCard.style.borderLeft = `4px solid ${getColorForClient(clientId)}`;
            clientCard.style.backgroundColor = 'var(--surface-bg-alt)';
        } else if (currentClientFilter !== 'all') {
            clientCard.style.opacity = '0.5';
        }

        let displaySubtitle = '';
        if (clientsMap[clientId].user_name || clientsMap[clientId].email) {
            displaySubtitle = `<div class="client-email" style="font-size: 11px; margin-top: -4px; margin-bottom: 8px; color: var(--text-secondary);"><i class="bi bi-person-badge"></i> ${escapeHtml(clientsMap[clientId].user_name ? clientsMap[clientId].user_name : '')} ${clientsMap[clientId].email ? `&lt;${escapeHtml(clientsMap[clientId].email)}&gt;` : ''}</div>`;
        }

        clientCard.innerHTML = `
            <div style="display:flex;justify-content:space-between;align-items:flex-start;">
                <div class="client-id" style="color: ${getColorForClient(clientId)};">${escapeHtml(clientsMap[clientId].name)}</div>
                <button class="delete-client-btn" data-client-id="${escapeHtml(clientId)}" title="Delete all history for this client" style="background:none;border:none;cursor:pointer;color:#c0392b;font-size:14px;padding:0 2px;line-height:1;"><i class="bi bi-trash"></i></button>
            </div>
            ${displaySubtitle}
            <div class="client-info">
                <span>ID: ${clientId}</span>
            </div>
        `;

        const deleteBtn = clientCard.querySelector('.delete-client-btn');
        if (deleteBtn) {
            deleteBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                deleteClientHistory(deleteBtn.dataset.clientId);
            });
        }

        clientCard.addEventListener('click', () => {
            currentClientFilter = clientId;
            const url = new URL(window.location);
            if (clientId === 'all') {
                url.searchParams.delete('client_id');
            } else {
                url.searchParams.set('client_id', clientId);
            }
            window.history.pushState({}, '', url);
            renderClientList(clientsMap);
            drawHistoryMap(clientId);
        });

        clientsList.appendChild(clientCard);
    });
}

function deleteHistoryEntry(timestamp) {
    if (!confirm('Delete this location entry?')) return;
    fetch('/api/location_history', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ timestamp })
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) loadHistory();
        else alert('Delete failed: ' + (data.error || 'unknown error'));
    })
    .catch(err => alert('Delete failed: ' + err));
}

function deleteClientHistory(clientId) {
    if (!confirm(`Delete ALL location history for client ${clientId}?`)) return;
    fetch('/api/location_history', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ client_id: clientId })
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            if (currentClientFilter === clientId) {
                currentClientFilter = 'all';
                const url = new URL(window.location);
                url.searchParams.delete('client_id');
                window.history.pushState({}, '', url);
            }
            loadHistory();
        } else {
            alert('Delete failed: ' + (data.error || 'unknown error'));
        }
    })
    .catch(err => alert('Delete failed: ' + err));
}

function escapeHtml(unsafe) {
    return (unsafe || '').replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

function loadHistory() {
    fetch('/api/location_history')
        .then(res => res.json())
        .then(data => {
            allHistoryData = data.history || [];

            const clientsMap = {};
            allHistoryData.forEach(d => {
                clientsMap[d.client_id] = {
                    name: d.device_name || d.client_id,
                    email: d.email || '',
                    user_name: d.user_name || ''
                };
            });

            renderClientList(clientsMap);
            drawHistoryMap(currentClientFilter);
        })
        .catch(err => {
            console.error('Error loading history:', err);
            alert('Error loading location history data.');
        });
}

document.addEventListener('DOMContentLoaded', () => {
    initMap();
    loadHistory();

    document.getElementById('refreshMapBtn').addEventListener('click', (e) => {
        const btn = e.currentTarget;
        btn.classList.add('is-loading');
        btn.disabled = true;

        const minWaitP = new Promise(res => setTimeout(res, 600));
        const fetchP = fetch('/api/location_history')
            .then(res => res.json())
            .then(data => {
                allHistoryData = data.history || [];

                const clientsMap = {};
                allHistoryData.forEach(d => {
                    clientsMap[d.client_id] = {
                        name: d.device_name || d.client_id,
                        email: d.email || '',
                        user_name: d.user_name || ''
                    };
                });

                renderClientList(clientsMap);
                drawHistoryMap(currentClientFilter);
            })
            .catch(err => {
                console.error('Error loading history:', err);
                alert('Error loading location history data.');
            });

        Promise.all([fetchP, minWaitP]).finally(() => {
            btn.classList.remove('is-loading');
            btn.disabled = false;
        });
    });

    document.getElementById('showAllBtn').addEventListener('click', () => {
        currentClientFilter = 'all';
        const url = new URL(window.location);
        url.searchParams.delete('client_id');
        window.history.pushState({}, '', url);

        const clientsMap = {};
        allHistoryData.forEach(d => {
            clientsMap[d.client_id] = {
                name: d.device_name || d.client_id,
                email: d.email || '',
                user_name: d.user_name || ''
            };
        });
        renderClientList(clientsMap);
        drawHistoryMap('all');
    });
});
