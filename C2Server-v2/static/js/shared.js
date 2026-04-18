// Global state
let socket;
let clients = {};

// Initialize SocketIO connection
function initializeSocket() {
    socket = io();
    window.socket = socket; // Expose globally for inline scripts

    socket.on('client_connected', (data) => {
        clients[data.client_id] = data;
        if (window.onClientConnected) window.onClientConnected(data);
    });

    socket.on('client_disconnected', (data) => {
        const client = clients[data.client_id];
        delete clients[data.client_id];
        if (window.onClientDisconnected) window.onClientDisconnected(data.client_id, client);
    });

    socket.on('client_info_updated', (data) => {
        if (clients[data.client_id]) {
            clients[data.client_id].sys_info = data.sys_info;
            // Also merge the other properties to prevent them from being lost if info emits differently formatted object
            if (data.battery !== undefined) clients[data.client_id].battery = data.battery;
            if (data.network_info !== undefined) clients[data.client_id].network_info = data.network_info;
            
            if (window.onClientInfoUpdated) window.onClientInfoUpdated(data);
        }
    });

    socket.on('client_status_updated', (data) => {
        if (clients[data.client_id]) {
            clients[data.client_id].battery = data.battery;
            clients[data.client_id].network_info = data.network_info;
            if (window.onClientStatusUpdated) window.onClientStatusUpdated(data);
        }
    });

    socket.on('command_response', (data) => {
        if (window.onCommandResponse) window.onCommandResponse(data);
    });

    socket.on('location_updated', (data) => {
        if (window.onLocationUpdated) window.onLocationUpdated(data);
    });

    socket.on('folder_download_progress', (data) => {
        console.log('Folder download progress:', data);
    });
}

// Load existing clients on page load
async function loadClients() {
    try {
        const response = await fetch('/api/clients', { cache: 'no-store' });
        const data = await response.json();

        const clientList = Array.isArray(data) ? data : (Array.isArray(data.clients) ? data.clients : []);

        clientList.forEach(client => {
            if (clients[client.client_id]) {
                // Merge data instead of full overwrite to preserve local state until API updates match
                Object.assign(clients[client.client_id], client);
            } else {
                clients[client.client_id] = client;
            }
        });

        if (window.onClientsLoaded) window.onClientsLoaded(clientList);
    } catch (error) {
        console.error('Error loading clients:', error);
    }
}

// Escape HTML to prevent XSS
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    initializeSocket();
    loadClients();

    if (window.onPageLoad) window.onPageLoad();
});
