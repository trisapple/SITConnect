// Map-specific state
let map = null;
let markers = {};
let polylines = {};
let locationHistory = {};
let refreshAllLoadingTimeout = null;

function setRefreshAllLoading(isLoading) {
    const refreshAllBtn = document.getElementById('refreshAllLocations');
    if (!refreshAllBtn) return;

    refreshAllBtn.classList.toggle('is-loading', isLoading);
    refreshAllBtn.disabled = isLoading;
}

// Render the clients list
function renderClients() {
    const clientsList = document.getElementById('clientsList');
    const clientCount = document.getElementById('clientCount');

    clientCount.textContent = Object.keys(clients).length;

    if (Object.keys(clients).length === 0) {
        clientsList.innerHTML = '<p class="empty-message">No clients connected</p>';
        return;
    }

    clientsList.innerHTML = '';

    Object.values(clients).forEach(client => {
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

        clientCard.innerHTML = `
            <div class="client-id">${displayName}</div>
            <div class="client-info">
                <span>IP: ${client.ip}:${client.port}</span>
                <span>Connected: ${client.connected_at}</span>
            </div>
            <div class="client-extra-info" style="font-size: 0.8em; color: var(--text-muted); display: flex; gap: 8px; margin-top: 4px;">
                <span>${batteryStr}</span>
                <span>${networkStr}</span>
                <span>${signalStr}</span>
            </div>
        `;

        clientsList.appendChild(clientCard);
    });
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
            const polylineColor = '#2563eb';
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
    const iconColor = '#2563eb';
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
            <p style="margin: 5px 0;"><strong>Coordinates:</strong> ${lat.toFixed(6)}, ${lng.toFixed(6)}</p>
            ${details.altitude ? `<p style="margin: 5px 0;"><strong>Altitude:</strong> ${details.altitude}m</p>` : ''}
            ${details.accuracy ? `<p style="margin: 5px 0;"><strong>Accuracy:</strong> ${details.accuracy}m</p>` : ''}
            ${details.speed ? `<p style="margin: 5px 0;"><strong>Speed:</strong> ${details.speed} m/s</p>` : ''}
            ${details.provider ? `<p style="margin: 5px 0;"><strong>Provider:</strong> ${details.provider}</p>` : ''}
            ${details.access ? `<p style="margin: 5px 0;"><strong>Access:</strong> ${details.access}</p>` : ''}
            <p style="margin: 5px 0; font-size: 12px; color: #64748b;"><strong>Updated:</strong> ${locationData.updated_at}</p>
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

window.onPageLoad = () => {
    initializeMap();

    // Add event listener for refresh button
    const refreshAllBtn = document.getElementById('refreshAllLocations');
    if (refreshAllBtn) {
        refreshAllBtn.addEventListener('click', refreshAllLocations);
    }
};
