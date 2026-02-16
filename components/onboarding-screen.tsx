import React from 'react';
import { View, Text, StyleSheet, Pressable, Platform } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import * as Haptics from 'expo-haptics';
import { useMotionCues } from '@/lib/motion-cues-context';

function DotGridIcon() {
  const dotSize = 8;
  const gap = 6;
  const rows = 4;
  const cols = 2;
  const colGap = 18;

  return (
    <View style={styles.dotGridContainer}>
      {/* Left column */}
      <View style={{ marginRight: colGap }}>
        {Array.from({ length: rows }).map((_, i) => (
          <View
            key={`l-${i}`}
            style={{
              width: dotSize,
              height: dotSize,
              borderRadius: dotSize / 2,
              backgroundColor: '#007AFF',
              marginBottom: i < rows - 1 ? gap : 0,
            }}
          />
        ))}
      </View>
      {/* Right column */}
      <View>
        {Array.from({ length: rows }).map((_, i) => (
          <View
            key={`r-${i}`}
            style={{
              width: dotSize,
              height: dotSize,
              borderRadius: dotSize / 2,
              backgroundColor: '#007AFF',
              marginBottom: i < rows - 1 ? gap : 0,
            }}
          />
        ))}
      </View>
    </View>
  );
}

interface FeatureRowProps {
  icon: string;
  title: string;
  description: string;
}

function FeatureRow({ icon, title, description }: FeatureRowProps) {
  return (
    <View style={styles.featureRow}>
      <View style={styles.featureIconContainer}>
        <MaterialIcons name={icon as any} size={28} color="#007AFF" />
      </View>
      <View style={styles.featureTextContainer}>
        <Text style={styles.featureTitle}>{title}</Text>
        <Text style={styles.featureDescription}>{description}</Text>
      </View>
    </View>
  );
}

export function OnboardingScreen() {
  const { setOnboardingSeen } = useMotionCues();

  const handleContinue = () => {
    if (Platform.OS !== 'web') {
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    }
    setOnboardingSeen();
  };

  const insets = useSafeAreaInsets();

  return (
    <View style={[styles.container, { paddingTop: insets.top + 20, paddingBottom: insets.bottom + 20 }]}>
      <View style={styles.content}>
        <View style={styles.headerSection}>
          <DotGridIcon />
          <Text style={styles.title}>Vehicle Motion Cues</Text>
        </View>

        <View style={styles.featuresSection}>
          <FeatureRow
            icon="waves"
            title="乗り物酔いの軽減に役立ちます"
            description="画面の端に表示されるドットが車両の動きに合わせて動き、乗り物酔いの軽減に役立ちます。"
          />
          <FeatureRow
            icon="directions-car"
            title="走行中の車両で表示されます"
            description="車両が動き始めると、ドットが自動的に画面に表示されます。"
          />
          <FeatureRow
            icon="north"
            title="前方を向いているときに最適です"
            description="走行中の車両で前方を向いて座っているときに最適に動作します。"
          />
          <FeatureRow
            icon="notifications-active"
            title="通知からすばやく切り替え"
            description="通知パネルから車両モーションキューのオン/オフをすばやく切り替えられます。"
          />
        </View>
      </View>

      <Pressable
        onPress={handleContinue}
        style={({ pressed }) => [
          styles.continueButton,
          pressed && { opacity: 0.85, transform: [{ scale: 0.98 }] },
        ]}
      >
        <Text style={styles.continueButtonText}>続ける</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000000',
    paddingHorizontal: 24,
    justifyContent: 'space-between',
  },
  content: {
    flex: 1,
    justifyContent: 'center',
  },
  headerSection: {
    alignItems: 'center',
    marginBottom: 48,
  },
  dotGridContainer: {
    flexDirection: 'row',
    marginBottom: 20,
  },
  title: {
    fontSize: 32,
    fontWeight: '700',
    color: '#FFFFFF',
    textAlign: 'center',
    letterSpacing: -0.5,
  },
  featuresSection: {
    gap: 28,
  },
  featureRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 16,
  },
  featureIconContainer: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(0,122,255,0.15)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  featureTextContainer: {
    flex: 1,
  },
  featureTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#FFFFFF',
    marginBottom: 3,
  },
  featureDescription: {
    fontSize: 14,
    color: 'rgba(255,255,255,0.6)',
    lineHeight: 20,
  },
  continueButton: {
    backgroundColor: '#007AFF',
    borderRadius: 14,
    paddingVertical: 16,
    alignItems: 'center',
    marginTop: 20,
  },
  continueButtonText: {
    fontSize: 18,
    fontWeight: '600',
    color: '#FFFFFF',
  },
});
