import { useEffect, useRef } from 'react';
import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import { useMotionCues } from '@/lib/motion-cues-context';
import { showToggleNotification, setupNotificationChannel } from '@/lib/notification-toggle';

export function useNotificationToggle() {
  const { state, toggleActive } = useMotionCues();
  const prevActiveRef = useRef(state.isActive);

  // Set up notification channel on mount
  useEffect(() => {
    if (Platform.OS !== 'web') {
      setupNotificationChannel();
    }
  }, []);

  // Listen for notification taps to toggle
  useEffect(() => {
    if (Platform.OS === 'web') return;

    const subscription = Notifications.addNotificationResponseReceivedListener((response) => {
      const data = response.notification.request.content.data;
      if (data?.action === 'toggle') {
        toggleActive();
      }
    });

    return () => subscription.remove();
  }, [toggleActive]);

  // Update notification when active state changes
  useEffect(() => {
    if (Platform.OS === 'web') return;
    if (!state.isLoaded) return;

    // Only update notification if state actually changed
    if (prevActiveRef.current !== state.isActive) {
      prevActiveRef.current = state.isActive;
      showToggleNotification(state.isActive);
    }
  }, [state.isActive, state.isLoaded]);
}
