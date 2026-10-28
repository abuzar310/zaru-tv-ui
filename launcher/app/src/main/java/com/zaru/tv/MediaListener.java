package com.zaru.tv;

import android.service.notification.NotificationListenerService;

/**
 * The pass Android asks for before it lists what other apps are playing (MediaSessionManager.getActiveSessions),
 * for the Control Center's Now Playing card. It reads no notifications. Allow it over adb:
 *   cmd notification allow_listener com.zaru.tv/com.zaru.tv.MediaListener
 */
public class MediaListener extends NotificationListenerService {
}
