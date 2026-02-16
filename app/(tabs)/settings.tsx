import React from 'react';
import { View, Text, StyleSheet, ScrollView, Pressable, Platform, useColorScheme } from 'react-native';
import * as Haptics from 'expo-haptics';
import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { ScreenContainer } from '@/components/screen-container';
import { useMotionCues, MotionCuesMode, DotColor, DotSize, DotCount } from '@/lib/motion-cues-context';
import { useColors } from '@/hooks/use-colors';

function SectionHeader({ title }: { title: string }) {
  const colors = useColors();
  return (
    <Text style={[styles.sectionHeader, { color: colors.muted }]}>{title}</Text>
  );
}

function SettingRow({
  label,
  selected,
  onPress,
  icon,
  subtitle,
}: {
  label: string;
  selected?: boolean;
  onPress: () => void;
  icon?: string;
  subtitle?: string;
}) {
  const colors = useColors();
  return (
    <Pressable
      onPress={() => {
        if (Platform.OS !== 'web') {
          Haptics.selectionAsync();
        }
        onPress();
      }}
      style={({ pressed }) => [
        styles.settingRow,
        { backgroundColor: colors.surface, borderBottomColor: colors.border },
        pressed && { opacity: 0.7 },
      ]}
    >
      <View style={styles.settingRowLeft}>
        {icon && (
          <MaterialIcons name={icon as any} size={22} color={colors.primary} style={styles.settingIcon} />
        )}
        <View style={{ flex: 1 }}>
          <Text style={[styles.settingLabel, { color: colors.foreground }]}>{label}</Text>
          {subtitle && (
            <Text style={[styles.settingSubtitle, { color: colors.muted }]}>{subtitle}</Text>
          )}
        </View>
      </View>
      {selected !== undefined && (
        <MaterialIcons
          name={selected ? 'check-circle' : 'radio-button-unchecked'}
          size={22}
          color={selected ? colors.primary : colors.muted}
        />
      )}
    </Pressable>
  );
}

function ColorDot({ color, selected, onPress, isAdaptive }: {
  color: string;
  selected: boolean;
  onPress: () => void;
  isAdaptive?: boolean;
}) {
  const colors = useColors();
  return (
    <Pressable
      onPress={() => {
        if (Platform.OS !== 'web') {
          Haptics.selectionAsync();
        }
        onPress();
      }}
      style={({ pressed }) => [
        styles.colorDotOuter,
        selected && { borderColor: colors.primary, borderWidth: 2.5 },
        pressed && { opacity: 0.7 },
      ]}
    >
      {isAdaptive ? (
        <View style={styles.adaptiveDotInner}>
          <View style={[styles.adaptiveHalf, { backgroundColor: '#000000', borderTopLeftRadius: 12, borderBottomLeftRadius: 12 }]} />
          <View style={[styles.adaptiveHalf, { backgroundColor: '#FFFFFF', borderTopRightRadius: 12, borderBottomRightRadius: 12, borderLeftWidth: 0.5, borderLeftColor: 'rgba(128,128,128,0.3)' }]} />
        </View>
      ) : (
        <View style={[styles.colorDotInner, { backgroundColor: color }]} />
      )}
    </Pressable>
  );
}

// Only fixed colors (adaptive is handled separately)
const FIXED_DOT_COLORS: { key: Exclude<DotColor, 'adaptive'>; color: string }[] = [
  { key: 'black', color: '#000000' },
  { key: 'white', color: '#FFFFFF' },
  { key: 'blue', color: '#007AFF' },
  { key: 'green', color: '#34C759' },
  { key: 'red', color: '#FF3B30' },
];

export default function SettingsScreen() {
  const { state, setMode, setDotColor, setDotSize, setDotCount, setSensitivity } = useMotionCues();
  const colors = useColors();
  const colorScheme = useColorScheme();

  const modeOptions: { value: MotionCuesMode; label: string; subtitle: string; icon: string }[] = [
    { value: 'on', label: 'オン', subtitle: 'ドットを常に表示します', icon: 'visibility' },
    { value: 'automatic', label: '自動', subtitle: '車両の動きを検出したときに表示します', icon: 'auto-fix-high' },
    { value: 'off', label: 'オフ', subtitle: 'ドットを表示しません', icon: 'visibility-off' },
  ];

  const sensitivityLevels = [
    { value: 0.25, label: '低' },
    { value: 0.5, label: '中' },
    { value: 0.75, label: '高' },
    { value: 1.0, label: '最高' },
  ];

  return (
    <ScreenContainer containerClassName="bg-background">
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        <Text style={[styles.screenTitle, { color: colors.foreground }]}>設定</Text>

        {/* Mode Section */}
        <SectionHeader title="モード" />
        <View style={[styles.sectionContainer, { backgroundColor: colors.surface, borderColor: colors.border }]}>
          {modeOptions.map((option) => (
            <SettingRow
              key={option.value}
              label={option.label}
              subtitle={option.subtitle}
              icon={option.icon}
              selected={state.mode === option.value}
              onPress={() => setMode(option.value)}
            />
          ))}
        </View>

        {/* Appearance Section */}
        <SectionHeader title="外観" />
        <View style={[styles.sectionContainer, { backgroundColor: colors.surface, borderColor: colors.border }]}>
          {/* Color */}
          <View style={[styles.settingRow, { backgroundColor: colors.surface, borderBottomColor: colors.border }]}>
            <Text style={[styles.settingLabel, { color: colors.foreground }]}>カラー</Text>
            <View style={styles.colorDotsRow}>
              {/* Adaptive (inverse) color option - first */}
              <ColorDot
                color=""
                selected={state.dotColor === 'adaptive'}
                onPress={() => setDotColor('adaptive')}
                isAdaptive
              />
              {/* Fixed color options */}
              {FIXED_DOT_COLORS.map((item) => (
                <ColorDot
                  key={item.key}
                  color={item.color}
                  selected={state.dotColor === item.key}
                  onPress={() => setDotColor(item.key)}
                />
              ))}
            </View>
          </View>

          {/* Adaptive color description */}
          {state.dotColor === 'adaptive' && (
            <View style={[styles.colorDescRow, { backgroundColor: colors.surface, borderBottomColor: colors.border }]}>
              <MaterialIcons name="auto-awesome" size={16} color={colors.primary} />
              <Text style={[styles.colorDescText, { color: colors.muted }]}>
                画面の背景に応じて自動的に反対色で表示されます（iOS準拠）
              </Text>
            </View>
          )}

          {/* Size */}
          <SettingRow
            label="標準サイズ"
            selected={state.dotSize === 'standard'}
            onPress={() => setDotSize('standard')}
          />
          <SettingRow
            label="より大きな点"
            selected={state.dotSize === 'larger'}
            onPress={() => setDotSize('larger')}
          />
        </View>

        {/* Count */}
        <SectionHeader title="表示数" />
        <View style={[styles.sectionContainer, { backgroundColor: colors.surface, borderColor: colors.border }]}>
          <SettingRow
            label="標準 (8個)"
            selected={state.dotCount === 'standard'}
            onPress={() => setDotCount('standard')}
          />
          <SettingRow
            label="より多くの点 (12個)"
            selected={state.dotCount === 'more'}
            onPress={() => setDotCount('more')}
          />
        </View>

        {/* Sensitivity */}
        <SectionHeader title="感度" />
        <View style={[styles.sectionContainer, { backgroundColor: colors.surface, borderColor: colors.border }]}>
          {sensitivityLevels.map((level) => (
            <SettingRow
              key={level.value}
              label={level.label}
              selected={state.sensitivity === level.value}
              onPress={() => setSensitivity(level.value)}
            />
          ))}
        </View>

        {/* Motion Filter Info */}
        <SectionHeader title="モーションフィルター" />
        <View style={[styles.aboutContainer, { backgroundColor: colors.surface, borderColor: colors.border }]}>
          <View style={styles.filterInfoRow}>
            <MaterialIcons name="filter-alt" size={20} color={colors.primary} />
            <Text style={[styles.filterInfoTitle, { color: colors.foreground }]}>高度なフィルタリング</Text>
          </View>
          <Text style={[styles.aboutText, { color: colors.muted }]}>
            加速度センサーとジャイロスコープを組み合わせた高度なフィルタリングにより、車両の動きのみを検出します。
            スマートフォンを振ったり、手の動きによる振動は自動的に除外されます。
          </Text>
          <View style={styles.filterFeatures}>
            <View style={styles.filterFeatureRow}>
              <MaterialIcons name="check" size={16} color={colors.success} />
              <Text style={[styles.filterFeatureText, { color: colors.muted }]}>車両の旋回・加速・減速を検出</Text>
            </View>
            <View style={styles.filterFeatureRow}>
              <MaterialIcons name="close" size={16} color={colors.error} />
              <Text style={[styles.filterFeatureText, { color: colors.muted }]}>手振り・スマホの振動を除外</Text>
            </View>
            <View style={styles.filterFeatureRow}>
              <MaterialIcons name="check" size={16} color={colors.success} />
              <Text style={[styles.filterFeatureText, { color: colors.muted }]}>ジャイロスコープによる回転検出</Text>
            </View>
            <View style={styles.filterFeatureRow}>
              <MaterialIcons name="close" size={16} color={colors.error} />
              <Text style={[styles.filterFeatureText, { color: colors.muted }]}>急激な加速度変化を無視</Text>
            </View>
          </View>
        </View>

        {/* About */}
        <SectionHeader title="について" />
        <View style={[styles.aboutContainer, { backgroundColor: colors.surface, borderColor: colors.border }]}>
          <Text style={[styles.aboutText, { color: colors.muted }]}>
            車両モーションキューは、画面の端にアニメーションのドットを表示し、車両の動きを視覚的に表現します。
            これにより、目で見ている情報と体が感じている動きの間の感覚的な不一致を軽減し、乗り物酔いの症状を和らげることができます。
          </Text>
          <Text style={[styles.aboutText, { color: colors.muted, marginTop: 12 }]}>
            この機能は、車内で前方を向いて座っているときに最も効果的に動作します。
            運転中は使用しないでください。
          </Text>
        </View>

        <View style={styles.bottomSpacer} />
      </ScrollView>
    </ScreenContainer>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: 16,
    paddingTop: 8,
  },
  screenTitle: {
    fontSize: 34,
    fontWeight: '700',
    marginBottom: 8,
    letterSpacing: -0.5,
  },
  sectionHeader: {
    fontSize: 13,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginTop: 24,
    marginBottom: 8,
    marginLeft: 4,
  },
  sectionContainer: {
    borderRadius: 12,
    overflow: 'hidden',
    borderWidth: 0.5,
  },
  settingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderBottomWidth: 0.5,
  },
  settingRowLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  settingIcon: {
    marginRight: 12,
  },
  settingLabel: {
    fontSize: 16,
    fontWeight: '400',
  },
  settingSubtitle: {
    fontSize: 12,
    marginTop: 2,
  },
  colorDotsRow: {
    flexDirection: 'row',
    gap: 10,
    flexWrap: 'wrap',
  },
  colorDotOuter: {
    width: 32,
    height: 32,
    borderRadius: 16,
    borderWidth: 1.5,
    borderColor: 'transparent',
    alignItems: 'center',
    justifyContent: 'center',
  },
  colorDotInner: {
    width: 24,
    height: 24,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(128,128,128,0.3)',
  },
  adaptiveDotInner: {
    width: 24,
    height: 24,
    borderRadius: 12,
    flexDirection: 'row',
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'rgba(128,128,128,0.3)',
  },
  adaptiveHalf: {
    flex: 1,
  },
  colorDescRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 10,
    gap: 8,
    borderBottomWidth: 0.5,
  },
  colorDescText: {
    fontSize: 12,
    flex: 1,
    lineHeight: 16,
  },
  aboutContainer: {
    borderRadius: 12,
    padding: 16,
    borderWidth: 0.5,
  },
  aboutText: {
    fontSize: 14,
    lineHeight: 22,
  },
  filterInfoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 8,
  },
  filterInfoTitle: {
    fontSize: 15,
    fontWeight: '600',
  },
  filterFeatures: {
    marginTop: 12,
    gap: 6,
  },
  filterFeatureRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  filterFeatureText: {
    fontSize: 13,
    lineHeight: 18,
  },
  bottomSpacer: {
    height: 40,
  },
});
