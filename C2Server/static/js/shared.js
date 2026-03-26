// Global state
let socket;
let clients = {};

// Initialize SocketIO connection
function initializeSocket() {
    socket = io();

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
            if (window.onClientInfoUpdated) window.onClientInfoUpdated(data);
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
        const response = await fetch('/api/clients');
        const data = await response.json();

        const clientList = Array.isArray(data) ? data : (Array.isArray(data.clients) ? data.clients : []);

        clientList.forEach(client => {
            clients[client.client_id] = client;
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
