package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * The pass Android asks for before it lists what other apps are playing (MediaSessionManager.getActiveSessions),
 * for the Control Center's Now Playing card. It reads no notifications. Allow it over adb:
 *   cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.MediaListener
 */
public class MediaListener extends NotificationListenerService {
}
