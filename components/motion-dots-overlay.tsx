import React, { useMemo } from 'react';
import { StyleSheet, useWindowDimensions, useColorScheme } from 'react-native';
import Animated, {
  useAnimatedStyle,
  withSpring,
  withTiming,
  Easing,
  SharedValue,
} from 'react-native-reanimated';
import { useMotionCues, DotColor } from '@/lib/motion-cues-context';

const SPRING_CONFIG = {
  damping: 18,
  stiffness: 120,
  mass: 0.8,
};

// Fixed color options (non-adaptive)
const DOT_COLORS: Record<Exclude<DotColor, 'adaptive'>, { light: string; dark: string }> = {
  black: { light: 'rgba(0,0,0,0.65)', dark: 'rgba(255,255,255,0.65)' },
  white: { light: 'rgba(255,255,255,0.8)', dark: 'rgba(255,255,255,0.8)' },
  blue: { light: 'rgba(0,122,255,0.7)', dark: 'rgba(10,132,255,0.7)' },
  green: { light: 'rgba(52,199,89,0.7)', dark: 'rgba(48,209,88,0.7)' },
  red: { light: 'rgba(255,59,48,0.7)', dark: 'rgba(255,69,58,0.7)' },
};

/**
 * iOS Vehicle Motion Cues uses adaptive inverse-color dots.
 * The dots appear in a color that contrasts with the current screen background.
 * 
 * On iOS, the system uses the actual pixel content behind each dot to determine
 * the inverse color. Since we can't sample pixels in React Native, we approximate
 * this by using the current color scheme:
 * - Light mode → dark dots (black with opacity)
 * - Dark mode → light dots (white with opacity)
 * 
 * This matches the iOS behavior where dots are always visible regardless of
 * the background content.
 */
function getAdaptiveColor(colorScheme: string | null | undefined): string {
  // iOS uses a semi-transparent dot that inverts against the background.
  // In light mode: dark dots for contrast against light backgrounds
  // In dark mode: light dots for contrast against dark backgrounds
  if (colorScheme === 'dark') {
    return 'rgba(255,255,255,0.55)';
  }
  return 'rgba(0,0,0,0.55)';
}

function getDotColor(dotColor: DotColor, colorScheme: string | null | undefined): string {
  if (dotColor === 'adaptive') {
    return getAdaptiveColor(colorScheme);
  }
  const scheme = colorScheme === 'dark' ? 'dark' : 'light';
  return DOT_COLORS[dotColor]?.[scheme] ?? DOT_COLORS.black[scheme];
}

interface MotionDotsOverlayProps {
  dotX: SharedValue<number>;
  dotY: SharedValue<number>;
  isPreview?: boolean;
  previewDotX?: number;
  previewDotY?: number;
}

function MotionDot({
  baseX,
  baseY,
  dotX,
  dotY,
  size,
  color,
  isPreview,
  previewDotX,
  previewDotY,
}: {
  baseX: number;
  baseY: number;
  dotX: SharedValue<number>;
  dotY: SharedValue<number>;
  size: number;
  color: string;
  isPreview?: boolean;
  previewDotX?: number;
  previewDotY?: number;
}) {
  const animatedStyle = useAnimatedStyle(() => {
    if (isPreview) {
      return {
        transform: [
          { translateX: withSpring(previewDotX ?? 0, SPRING_CONFIG) },
          { translateY: withSpring(previewDotY ?? 0, SPRING_CONFIG) },
        ],
      };
    }
    return {
      transform: [
        { translateX: withSpring(dotX.value, SPRING_CONFIG) },
        { translateY: withSpring(dotY.value, SPRING_CONFIG) },
      ],
    };
  });

  return (
    <Animated.View
      style={[
        {
          position: 'absolute',
          left: baseX - size / 2,
          top: baseY - size / 2,
          width: size,
          height: size,
          borderRadius: size / 2,
          backgroundColor: color,
        },
        animatedStyle,
      ]}
    />
  );
}

export function MotionDotsOverlay({
  dotX,
  dotY,
  isPreview = false,
  previewDotX = 0,
  previewDotY = 0,
}: MotionDotsOverlayProps) {
  const { state } = useMotionCues();
  const { width, height } = useWindowDimensions();
  const colorScheme = useColorScheme();

  const dotColor = getDotColor(state.dotColor, colorScheme);
  const dotSize = state.dotSize === 'larger' ? 11 : 8;
  const dotCount = state.dotCount === 'more' ? 6 : 4;

  // Calculate dot positions
  const dotPositions = useMemo(() => {
    const positions: { x: number; y: number }[] = [];
    const marginX = 14;
    // Dots span the middle 55% of screen height, centered
    const topOffset = height * 0.225;
    const bottomOffset = height * 0.775;
    const verticalSpan = bottomOffset - topOffset;
    const spacing = verticalSpan / (dotCount - 1);

    // Left column
    for (let i = 0; i < dotCount; i++) {
      positions.push({
        x: marginX,
        y: topOffset + i * spacing,
      });
    }

    // Right column
    for (let i = 0; i < dotCount; i++) {
      positions.push({
        x: width - marginX,
        y: topOffset + i * spacing,
      });
    }

    return positions;
  }, [width, height, dotCount]);

  const fadeStyle = useAnimatedStyle(() => {
    return {
      opacity: withTiming(1, { duration: 350, easing: Easing.inOut(Easing.ease) }),
    };
  });

  return (
    <Animated.View
      style={[
        StyleSheet.absoluteFill,
        { pointerEvents: 'none' },
        fadeStyle,
      ]}
    >
      {dotPositions.map((pos, index) => (
        <MotionDot
          key={index}
          baseX={pos.x}
          baseY={pos.y}
          dotX={dotX}
          dotY={dotY}
          size={dotSize}
          color={dotColor}
          isPreview={isPreview}
          previewDotX={previewDotX}
          previewDotY={previewDotY}
        />
      ))}
    </Animated.View>
  );
}
