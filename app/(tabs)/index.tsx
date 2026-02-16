import React, { useEffect } from 'react';
import { View, Text, StyleSheet, Pressable, Platform } from 'react-native';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  withRepeat,
  withSequence,
  withTiming,
  withDelay,
  Easing,
  cancelAnimation,
} from 'react-native-reanimated';
import * as Haptics from 'expo-haptics';
import { ScreenContainer } from '@/components/screen-container';
import { useMotionCues } from '@/lib/motion-cues-context';
import { useMotionSensor } from '@/hooks/use-motion-sensor';
import { MotionDotsOverlay } from '@/components/motion-dots-overlay';
import { OnboardingScreen } from '@/components/onboarding-screen';
import { useNotificationToggle } from '@/hooks/use-notification-toggle';
import { useColors } from '@/hooks/use-colors';

export default function HomeScreen() {
  const { state, toggleActive } = useMotionCues();
  const { dotX, dotY } = useMotionSensor();
  const colors = useColors();

  // Set up notification toggle
  useNotificationToggle();

  // Animation for the toggle button
  const toggleScale = useSharedValue(1);

  // Demo animation for preview dots
  const previewX = useSharedValue(0);
  const previewY = useSharedValue(0);

  useEffect(() => {
    if (state.isActive) {
      previewX.value = withRepeat(
        withSequence(
          withTiming(8, { duration: 2000, easing: Easing.inOut(Easing.sin) }),
          withTiming(-6, { duration: 2500, easing: Easing.inOut(Easing.sin) }),
          withTiming(3, { duration: 1800, easing: Easing.inOut(Easing.sin) }),
          withTiming(0, { duration: 1500, easing: Easing.inOut(Easing.sin) }),
        ),
        -1,
        false
      );
      previewY.value = withRepeat(
        withSequence(
          withDelay(300, withTiming(-5, { duration: 1800, easing: Easing.inOut(Easing.sin) })),
          withTiming(7, { duration: 2200, easing: Easing.inOut(Easing.sin) }),
          withTiming(-3, { duration: 1600, easing: Easing.inOut(Easing.sin) }),
          withTiming(0, { duration: 1400, easing: Easing.inOut(Easing.sin) }),
        ),
        -1,
        false
      );
    } else {
      cancelAnimation(previewX);
      cancelAnimation(previewY);
      previewX.value = withTiming(0, { duration: 300 });
      previewY.value = withTiming(0, { duration: 300 });
    }
  }, [state.isActive, previewX, previewY]);

  const handleToggle = () => {
    if (Platform.OS !== 'web') {
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    }
    toggleScale.value = withSequence(
      withTiming(0.92, { duration: 80 }),
      withSpring(1, { damping: 12, stiffness: 200 })
    );
    toggleActive();
  };

  const toggleAnimatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: toggleScale.value }],
  }));

  const statusDotStyle = useAnimatedStyle(() => ({
    opacity: withTiming(state.isActive ? 1 : 0.3, { duration: 300 }),
    backgroundColor: state.isActive ? '#34C759' : '#8E8E93',
  }));

  const previewAnimatedStyle = useAnimatedStyle(() => ({
    opacity: withTiming(state.isActive ? 1 : 0.2, { duration: 400 }),
  }));

  // Show onboarding if not seen yet
  if (!state.isLoaded) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.background }} />
    );
  }

  if (!state.hasSeenOnboarding) {
    return <OnboardingScreen />;
  }

  return (
    <ScreenContainer
      containerClassName="bg-background"
      className="flex-1"
    >
      <View style={styles.container}>
        {/* Status indicator */}
        <View style={styles.statusRow}>
          <Animated.View style={[styles.statusDot, statusDotStyle]} />
          <Text style={[styles.statusText, { color: colors.muted }]}>
            {state.isActive ? 'アクティブ' : '無効'}
          </Text>
          <Text style={[styles.modeText, { color: colors.muted }]}>
            モード: {state.mode === 'on' ? 'オン' : state.mode === 'off' ? 'オフ' : '自動'}
          </Text>
        </View>

        {/* Main toggle area */}
        <View style={styles.toggleSection}>
          <Animated.View style={toggleAnimatedStyle}>
            <Pressable
              onPress={handleToggle}
              style={({ pressed }) => [
                styles.toggleButton,
                {
                  backgroundColor: state.isActive ? '#34C759' : colors.surface,
                  borderColor: state.isActive ? '#34C759' : colors.border,
                },
                pressed && { opacity: 0.9 },
              ]}
            >
              <View style={styles.toggleInner}>
                <View style={[
                  styles.powerIcon,
                  { borderColor: state.isActive ? '#FFFFFF' : colors.muted },
                ]}>
                  <View style={[
                    styles.powerIconLine,
                    { backgroundColor: state.isActive ? '#FFFFFF' : colors.muted },
                  ]} />
                </View>
              </View>
            </Pressable>
          </Animated.View>

          <Text style={[styles.toggleLabel, { color: colors.foreground }]}>
            {state.isActive ? 'オン' : 'オフ'}
          </Text>
          <Text style={[styles.toggleHint, { color: colors.muted }]}>
            タップして切り替え
          </Text>
        </View>

        {/* Live preview area */}
        <Animated.View style={[styles.previewContainer, { backgroundColor: colors.surface, borderColor: colors.border }, previewAnimatedStyle]}>
          <Text style={[styles.previewLabel, { color: colors.muted }]}>
            プレビュー
          </Text>
          <View style={styles.previewArea}>
            <MotionDotsOverlay
              dotX={state.isActive ? dotX : previewX}
              dotY={state.isActive ? dotY : previewY}
              isPreview={!state.isActive}
              previewDotX={0}
              previewDotY={0}
            />
            {/* Phone outline in preview */}
            <View style={[styles.phoneOutline, { borderColor: colors.border }]}>
              <View style={[styles.phoneLine, { backgroundColor: colors.border }]} />
              <View style={[styles.phoneLine, { backgroundColor: colors.border, width: '60%' }]} />
              <View style={[styles.phoneLine, { backgroundColor: colors.border, width: '80%' }]} />
              <View style={[styles.phoneLine, { backgroundColor: colors.border, width: '45%' }]} />
            </View>
          </View>
        </Animated.View>

        {/* Info text */}
        <Text style={[styles.infoText, { color: colors.muted }]}>
          画面の端にドットが表示され、車両の動きを表現します。{'\n'}
          乗り物酔いの軽減に役立つことがあります。
        </Text>
      </View>

      {/* Full-screen motion dots overlay when active */}
      {state.isActive && (
        <MotionDotsOverlay dotX={dotX} dotY={dotY} />
      )}
    </ScreenContainer>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 24,
    paddingTop: 16,
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 8,
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  statusText: {
    fontSize: 14,
    fontWeight: '500',
  },
  modeText: {
    fontSize: 13,
    marginLeft: 'auto',
  },
  toggleSection: {
    alignItems: 'center',
    paddingVertical: 32,
  },
  toggleButton: {
    width: 120,
    height: 120,
    borderRadius: 60,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 12,
    elevation: 4,
  },
  toggleInner: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  powerIcon: {
    width: 44,
    height: 44,
    borderRadius: 22,
    borderWidth: 3,
    alignItems: 'center',
    justifyContent: 'flex-start',
    paddingTop: 4,
  },
  powerIconLine: {
    width: 3,
    height: 16,
    borderRadius: 1.5,
    marginTop: -6,
  },
  toggleLabel: {
    fontSize: 22,
    fontWeight: '700',
    marginTop: 16,
  },
  toggleHint: {
    fontSize: 13,
    marginTop: 4,
  },
  previewContainer: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
    marginBottom: 16,
  },
  previewLabel: {
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 12,
  },
  previewArea: {
    height: 160,
    borderRadius: 12,
    overflow: 'hidden',
    position: 'relative',
    alignItems: 'center',
    justifyContent: 'center',
  },
  phoneOutline: {
    width: 80,
    height: 130,
    borderRadius: 12,
    borderWidth: 1.5,
    padding: 10,
    gap: 6,
    justifyContent: 'center',
  },
  phoneLine: {
    height: 3,
    borderRadius: 1.5,
    width: '70%',
  },
  infoText: {
    fontSize: 13,
    textAlign: 'center',
    lineHeight: 20,
  },
});
