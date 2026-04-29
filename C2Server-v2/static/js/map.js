// Map-specific state
let map = null;
let markers = {};
let polylines = {};
let locationHistory = {};
let refreshAllLoadingTimeout = null;
let offlineClients = {};

function setRefreshAllLoading(isLoading) {
    const refreshAllBtn = document.getElementById('refreshAllLocations');
    if (!refreshAllBtn) return;

    refreshAllBtn.classList.toggle('is-loading', isLoading);
    refreshAllBtn.disabled = isLoading;
}

const colors = ['#ff8a80', '#ff80ab', '#ea80fc', '#b388ff', '#8c9eff', '#82b1ff', '#84ffff', '#a7ffeb', '#b9f6ca', '#ccff90', '#f4ff81', '#ffe57f', '#ffd180', '#ff9e80', '#ffffff', '#e0e0e0', '#f8bbd0', '#c8e6c9', '#b2dfdb', '#b3e5fc', '#fdcefc', '#f0f4c3'];

function getColorForClient(clientId) {
    let hash = 0;
    for (let i = 0; i < clientId.length; i++) {
        hash = clientId.charCodeAt(i) + ((hash << 5) - hash);
    }
    return colors[Math.abs(hash) % colors.length];
}

// Render the clients list
function renderClients() {
    const clientsList = document.getElementById('clientsList');
    if (!clientsList) return; // In case we're on a page without this sidebar

    const clientCount = document.getElementById('clientCount');
    const connectedKeys = Object.keys(clients);
    const offlineKeys = Object.keys(offlineClients).filter(c => !connectedKeys.includes(c));

    if (clientCount) {
        clientCount.textContent = connectedKeys.length;
    }

    clientsList.innerHTML = '';

    // Render CONNECTED clients
    if (connectedKeys.length > 0) {
        const header = document.createElement('h4');
        header.style.color = 'var(--text-color)';
        header.style.margin = '10px 0 5px 0';
        header.textContent = 'Connected Clients';
        clientsList.appendChild(header);

        connectedKeys.forEach(clientId => {
            const client = clients[clientId];
            const clientCard = document.createElement('div');
            clientCard.className = 'client-card';

            const displayName = client.sys_info ? escapeHtml(client.sys_info) : client.client_id;
            
            // Parse battery
            let batteryStr = '--';
            if (client.battery) {
                const battMatch = client.battery.match(/Level:\s+(\d+%)/i);
                if (battMatch) batteryStr = `🔋 ${battMatch[1]}`;
            }
            
            // Parse network
            let networkStr = '--';
            let signalStr = '--';
            if (client.network_info) {
                if (client.network_info.includes('Type: Wi-Fi')) {
                    networkStr = '🌐 WiFi';
                    const rssiMatch = client.network_info.match(/RSSI:\s+(-?\d+\s+dBm)/i);
                    if (rssiMatch) signalStr = `📶 ${rssiMatch[1]}`;
                } else if (client.network_info.includes('Type: Cellular')) {
                    networkStr = '📡 Cellular';
                    const signalMatch = client.network_info.match(/Signal Strength:\s+(-?\d+\s+dBm)/i);
                    if (signalMatch) signalStr = `📶 ${signalMatch[1]}`;
                }
            }

            let displaySubtitle = '';
            if (client.user_name || client.email) {
                displaySubtitle = `<div class="client-email" style="font-size: 11px; margin-top: -4px; margin-bottom: 8px; color: var(--text-secondary);"><i class="bi bi-person-badge"></i> ${escapeHtml(client.user_name ? client.user_name : '')} ${client.email ? `&lt;${escapeHtml(client.email)}&gt;` : ''}</div>`;
            }

            clientCard.innerHTML = `
                <div class="client-id" style="color: ${getColorForClient(client.client_id)};">${displayName}</div>
                ${displaySubtitle}
                <div class="client-info">
                    <span>ID: ${client.client_id}</span>
                    <span>IP: ${client.ip}:${client.port}</span>
                    <span>Connected: ${client.connected_at}</span>
                </div>
                <div class="client-extra-info" style="font-size: 0.8em; color: var(--text-muted); display: flex; gap: 8px; margin-top: 4px;">
                    <span>${batteryStr}</span>
                    <span>${networkStr}</span>
                    <span>${signalStr}</span>
                </div>
                <div style="margin-top: 8px;">
                    <a href="/history?client_id=${client.client_id}" class="btn-small" style="text-decoration: none; display: inline-block; padding: 4px 8px; background-color: var(--primary-color, #4363d8); color: white; border-radius: 4px; font-size: 11px;">View History</a>
                </div>
            `;

            clientsList.appendChild(clientCard);
        });
    } else {
        const em = document.createElement('p');
        em.className = 'empty-message';
        em.textContent = 'No clients connected';
        clientsList.appendChild(em);
    }

    // Render OFFLINE clients
    if (offlineKeys.length > 0) {
        const header = document.createElement('h4');
        header.style.color = 'var(--text-color)';
        header.style.margin = '20px 0 5px 0';
        header.style.opacity = '0.7';
        header.textContent = 'Disconnected';
        clientsList.appendChild(header);
        
        offlineKeys.forEach(clientId => {
            const client = offlineClients[clientId];
            const clientCard = document.createElement('div');
            clientCard.className = 'client-card';
            clientCard.style.opacity = '0.6';
            clientCard.style.borderLeftColor = '#888';

            const displayName = client.sys_info ? escapeHtml(client.sys_info) : client.client_id;
            
            let displaySubtitle = '';
            if (client.user_name || client.email) {
                displaySubtitle = `<div class="client-email" style="font-size: 11px; margin-top: -4px; margin-bottom: 8px; color: var(--text-secondary);"><i class="bi bi-person-badge"></i> ${escapeHtml(client.user_name ? client.user_name : '')} ${client.email ? `&lt;${escapeHtml(client.email)}&gt;` : ''}</div>`;
            }

            clientCard.innerHTML = `
                <div class="client-id" style="color: #ccc;">${displayName}</div>
                ${displaySubtitle}
                <div class="client-info">
                    <span>ID: ${client.client_id}</span>
                    <span>Status: Offline</span>
                </div>
                <div style="margin-top: 8px;">
                    <a href="/history?client_id=${client.client_id}" class="btn-small" style="text-decoration: none; display: inline-block; padding: 4px 8px; background-color: var(--primary-color, #4363d8); color: white; border-radius: 4px; font-size: 11px;">View History</a>
                </div>
            `;
            clientsList.appendChild(clientCard);
        });
    }
}

// Initialize map
function initializeMap() {
    map = L.map('map').setView([0, 0], 2);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
        maxZoom: 19
    }).addTo(map);
}

// Add or update marker on map
function addMarkerToMap(clientId, locationData, clientName) {
    const lat = locationData.lat;
    const lng = locationData.lng;

    if (!lat || !lng) return;

    // Track location history
    if (locationData.history && (!locationHistory[clientId] || locationHistory[clientId].length < locationData.history.length)) {
        locationHistory[clientId] = locationData.history.map(p => [p.lat, p.lng]);
    } else if (!locationHistory[clientId]) {
        locationHistory[clientId] = [];
    }
    
    const history = locationHistory[clientId];
    const isNewPos = history.length === 0 || 
                    history[history.length - 1][0] !== lat || 
                    history[history.length - 1][1] !== lng;
                    
    if (isNewPos) {
        history.push([lat, lng]);
    }

    // Draw/Update polyline connecting history
    if (history.length > 1) {
        if (polylines[clientId]) {
            polylines[clientId].setLatLngs(history);
        } else {
            const polylineColor = getColorForClient(clientId);
            polylines[clientId] = L.polyline(history, {
                color: polylineColor,
                weight: 3,
                opacity: 0.7,
                dashArray: '5, 10',
                lineJoin: 'round'
            }).addTo(map);
        }
    }

    // Remove existing marker if present
    if (markers[clientId]) {
        map.removeLayer(markers[clientId]);
    }

    // Create custom icon
    const iconColor = getColorForClient(clientId);
    const customIcon = L.divIcon({
        className: 'custom-marker',
        html: `<div style="background-color: ${iconColor}; width: 30px; height: 30px; border-radius: 50%; border: 3px solid white; box-shadow: 0 2px 5px rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; color: white; font-weight: bold;">📱</div>`,
        iconSize: [30, 30],
        iconAnchor: [15, 15]
    });

    // Create marker
    const marker = L.marker([lat, lng], { icon: customIcon }).addTo(map);

    // Build popup content
    const details = locationData.details || {};
    const popupContent = `
        <div style="min-width: 200px;">
            <h3 style="margin: 0 0 10px 0; color: #1e293b;">${escapeHtml(clientName)}</h3>
            <p style="margin: 5px 0;"><strong>Client ID:</strong> ${clientId}</p>
            <p style="margin: 5px 0;"><strong>Coordinates:</strong> ${lat.toFixed(6)}, ${lng.toFixed(6)}</p>
            ${details.altitude ? `<p style="margin: 5px 0;"><strong>Altitude:</strong> ${details.altitude}m</p>` : ''}
            ${details.accuracy ? `<p style="margin: 5px 0;"><strong>Accuracy:</strong> ${details.accuracy}m</p>` : ''}
            ${details.speed ? `<p style="margin: 5px 0;"><strong>Speed:</strong> ${details.speed} m/s</p>` : ''}
            ${details.provider ? `<p style="margin: 5px 0;"><strong>Provider:</strong> ${details.provider}</p>` : ''}
            ${details.access ? `<p style="margin: 5px 0;"><strong>Access:</strong> ${details.access}</p>` : ''}
            <p style="margin: 5px 0; font-size: 12px; color: #64748b;"><strong>Updated:</strong> ${locationData.updated_at}</p>
            <div style="margin-top: 10px;">
                <a href="/history?client_id=${clientId}" style="display: inline-block; padding: 6px 10px; background-color: var(--primary-color, #4363d8); color: white; text-decoration: none; border-radius: 4px; font-size: 12px;">View Location History</a>
            </div>
        </div>
    `;

    marker.bindPopup(popupContent);
    markers[clientId] = marker;

    // If this is the first marker, zoom to it
    if (Object.keys(markers).length === 1) {
        map.setView([lat, lng], 13);
    } else {
        // Fit bounds to show all markers
        const bounds = L.latLngBounds(Object.values(markers).map(m => m.getLatLng()));
        map.fitBounds(bounds, { padding: [50, 50] });
    }
}

// Remove marker from map
function removeMarkerFromMap(clientId) {
    if (markers[clientId]) {
        map.removeLayer(markers[clientId]);
        delete markers[clientId];
    }
    if (polylines[clientId]) {
        map.removeLayer(polylines[clientId]);
        delete polylines[clientId];
    }
    if (locationHistory[clientId]) {
        delete locationHistory[clientId];
    }
}

// Refresh location for specific client
function refreshClientLocation(clientId) {
    if (socket) {
        socket.emit('refresh_location', { client_id: clientId });
        console.log(`Requesting location update for ${clients[clientId]?.sys_info || clientId}...`);
    }
}

// Refresh all client locations
function refreshAllLocations() {
    setRefreshAllLoading(true);

    if (refreshAllLoadingTimeout) {
        clearTimeout(refreshAllLoadingTimeout);
    }

    Object.keys(clients).forEach(clientId => {
        refreshClientLocation(clientId);
    });

    refreshAllLoadingTimeout = setTimeout(() => {
        setRefreshAllLoading(false);
        refreshAllLoadingTimeout = null;
    }, 1200);

    console.log('Requesting location updates for all clients...');
}

// Callback hooks for shared.js
window.onClientConnected = (data) => {
    // Client will send location separately
    renderClients();
};

window.onClientDisconnected = (clientId) => {
    removeMarkerFromMap(clientId);
    renderClients();
};

window.onClientStatusUpdated = function(data) {
    if (typeof renderClients === 'function') {
        renderClients();
    }
};

window.onLocationUpdated = (data) => {
    const clientId = data.client_id;
    const location = data.location;

    if (clients[clientId]) {
        clients[clientId].location = location;

        const clientName = clients[clientId].sys_info || clientId;
        addMarkerToMap(clientId, location, clientName);

        console.log(`📍 Location received for ${clientName}: ${location.lat.toFixed(6)}, ${location.lng.toFixed(6)}`);
    }
};

window.onClientsLoaded = (data) => {
    // Render the clients list
    renderClients();

    // Add existing client locations to map
    data.forEach(client => {
        if (client.location) {
            addMarkerToMap(client.client_id, client.location, client.sys_info || client.client_id);
        }
    });
};

function loadOfflineClients() {
    fetch('/api/location_history')
        .then(res => res.json())
        .then(data => {
            const history = data.history || [];
            history.forEach(item => {
                if (!offlineClients[item.client_id]) {
                    offlineClients[item.client_id] = {
                        client_id: item.client_id,
                        sys_info: item.device_name || item.client_id,
                        user_name: item.user_name || '',
                        email: item.email || ''
                    };
                } else {
                    if (item.device_name) offlineClients[item.client_id].sys_info = item.device_name;
                    if (item.user_name) offlineClients[item.client_id].user_name = item.user_name;
                    if (item.email) offlineClients[item.client_id].email = item.email;
                }
            });
            renderClients();
        })
        .catch(err => console.error("Error loading offline clients:", err));
}

window.onPageLoad = () => {
    initializeMap();
    loadOfflineClients();

    // Add event listener for refresh button
    const refreshAllBtn = document.getElementById('refreshAllLocations');
    if (refreshAllBtn) {
        refreshAllBtn.addEventListener('click', refreshAllLocations);
    }
};
