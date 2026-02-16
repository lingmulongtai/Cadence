import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';

const NOTIFICATION_ID = 'motion-cues-toggle';
const CHANNEL_ID = 'motion-cues-control';

// Set up notification handler
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: false,
    shouldSetBadge: false,
  }),
});

export async function setupNotificationChannel() {
  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(CHANNEL_ID, {
      name: 'Motion Cues Control',
      description: '車両モーションキューの制御通知',
      importance: Notifications.AndroidImportance.LOW,
      vibrationPattern: [0],
      enableVibrate: false,
      showBadge: false,
    });
  }
}

export async function requestNotificationPermissions(): Promise<boolean> {
  const { status: existingStatus } = await Notifications.getPermissionsAsync();
  let finalStatus = existingStatus;

  if (existingStatus !== 'granted') {
    const { status } = await Notifications.requestPermissionsAsync();
    finalStatus = status;
  }

  return finalStatus === 'granted';
}

export async function showToggleNotification(isActive: boolean) {
  if (Platform.OS === 'web') return;

  const hasPermission = await requestNotificationPermissions();
  if (!hasPermission) return;

  await setupNotificationChannel();

  // Dismiss existing notification first
  await Notifications.dismissNotificationAsync(NOTIFICATION_ID);

  await Notifications.scheduleNotificationAsync({
    identifier: NOTIFICATION_ID,
    content: {
      title: '車両モーションキュー',
      body: isActive
        ? 'モーションキューはオンです。タップしてオフにします。'
        : 'モーションキューはオフです。タップしてオンにします。',
      data: { action: 'toggle', currentState: isActive },
      sticky: true,
      autoDismiss: false,
    },
    trigger: null,
  });
}

export async function dismissToggleNotification() {
  if (Platform.OS === 'web') return;
  try {
    await Notifications.dismissNotificationAsync(NOTIFICATION_ID);
  } catch {
    // Ignore errors
  }
}
