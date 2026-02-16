import { useEffect, useRef, useCallback } from 'react';
import { Platform } from 'react-native';
import { useSharedValue } from 'react-native-reanimated';
import { useMotionCues } from '@/lib/motion-cues-context';

/**
 * Advanced Vehicle Motion Detection Filter
 * 
 * This hook uses DeviceMotion (fused accelerometer + gyroscope) to distinguish
 * vehicle motion from hand shaking or phone vibration.
 * 
 * Key principles:
 * 1. Vehicle motion is LOW FREQUENCY (< 1Hz), sustained, and smooth
 * 2. Hand shaking is HIGH FREQUENCY (> 2Hz), brief, and jerky
 * 3. Phone vibration is VERY HIGH FREQUENCY (> 5Hz), periodic
 * 
 * Filtering strategy:
 * - Two-stage low-pass filter to extract only very slow motion (vehicle turns, acceleration)
 * - Gyroscope-based shake detection: high rotation rate = hand movement, not vehicle
 * - Variance analysis: vehicle motion has low variance, shaking has high variance
 * - Sustained motion check: vehicle motion persists for seconds, shaking is brief
 */

// ===== FILTER PARAMETERS =====

// Stage 1: Heavy low-pass filter to extract vehicle motion envelope
// Very low alpha = very smooth, only passes slow changes (vehicle turns/accel)
const VEHICLE_FILTER_ALPHA = 0.04;

// Stage 2: Additional smoothing for dot animation (prevents jitter)
const DOT_SMOOTHING_ALPHA = 0.15;

// Maximum dot displacement in pixels
const MAX_DISPLACEMENT = 22;

// ===== SHAKE REJECTION PARAMETERS =====

// Gyroscope rotation rate threshold (rad/s) - above this = hand movement
// Vehicle turns cause ~0.1-0.3 rad/s, hand shaking causes 1-5+ rad/s
const GYRO_SHAKE_THRESHOLD = 1.2;

// Accelerometer jerk threshold (change in acceleration per sample)
// Vehicle motion changes slowly, hand shaking has rapid acceleration changes
const JERK_THRESHOLD = 0.8;

// Variance window size (number of samples to analyze)
const VARIANCE_WINDOW = 20;

// Variance threshold - high variance = shaking, low variance = vehicle
const VARIANCE_THRESHOLD = 0.15;

// ===== VEHICLE DETECTION PARAMETERS =====

// Minimum sustained motion duration for vehicle detection (in samples at ~60fps)
const VEHICLE_DETECTION_SAMPLES = 90; // ~1.5 seconds
// Threshold for considering motion as "vehicle-like" (low, sustained acceleration)
const VEHICLE_MOTION_MIN = 0.015;
const VEHICLE_MOTION_MAX = 0.5; // Above this is likely hand movement

// Suppression factor when shake is detected (0 = full suppression)
const SHAKE_SUPPRESSION = 0.05;

// Recovery speed after shake stops (how quickly dots resume responding)
const SHAKE_RECOVERY_SPEED = 0.02;

export function useMotionSensor() {
  const { state, setVehicleDetected } = useMotionCues();
  const dotX = useSharedValue(0);
  const dotY = useSharedValue(0);

  // Stage 1: Vehicle motion envelope (very slow changes only)
  const vehicleX = useRef(0);
  const vehicleY = useRef(0);

  // Stage 2: Dot animation smoothing
  const smoothDotX = useRef(0);
  const smoothDotY = useRef(0);

  // Previous acceleration for jerk calculation
  const prevAccelX = useRef(0);
  const prevAccelY = useRef(0);

  // Shake detection state
  const shakeConfidence = useRef(0); // 0 = no shake, 1 = full shake
  const isShaking = useRef(false);

  // Variance analysis buffers
  const accelXBuffer = useRef<number[]>([]);
  const accelYBuffer = useRef<number[]>([]);

  // Vehicle detection
  const vehicleMotionBuffer = useRef<number[]>([]);
  const vehicleConfidence = useRef(0);

  const subscriptionRef = useRef<{ remove: () => void } | null>(null);

  const processMotionData = useCallback((data: {
    acceleration: { x: number; y: number; z: number } | null;
    accelerationIncludingGravity: { x: number; y: number; z: number };
    rotationRate: { alpha: number; beta: number; gamma: number } | null;
  }) => {
    // Use linear acceleration (gravity removed) if available, otherwise use raw
    const accelX = data.acceleration?.x ?? data.accelerationIncludingGravity.x;
    const accelY = data.acceleration?.y ?? data.accelerationIncludingGravity.y;

    // Normalize to g-units if using accelerationIncludingGravity
    const ax = data.acceleration ? accelX / 9.81 : accelX;
    const ay = data.acceleration ? accelY / 9.81 : accelY;

    // ===== SHAKE DETECTION =====

    // 1. Gyroscope-based shake detection
    let gyroShake = false;
    if (data.rotationRate) {
      const rotMagnitude = Math.sqrt(
        data.rotationRate.alpha ** 2 +
        data.rotationRate.beta ** 2 +
        data.rotationRate.gamma ** 2
      );
      // Convert deg/s to rad/s for comparison
      const rotRadPerSec = rotMagnitude * (Math.PI / 180);
      gyroShake = rotRadPerSec > GYRO_SHAKE_THRESHOLD;
    }

    // 2. Jerk-based shake detection (rapid acceleration changes)
    const jerkX = Math.abs(ax - prevAccelX.current);
    const jerkY = Math.abs(ay - prevAccelY.current);
    const jerkMagnitude = Math.sqrt(jerkX * jerkX + jerkY * jerkY);
    const jerkShake = jerkMagnitude > JERK_THRESHOLD;
    prevAccelX.current = ax;
    prevAccelY.current = ay;

    // 3. Variance-based shake detection
    accelXBuffer.current.push(ax);
    accelYBuffer.current.push(ay);
    if (accelXBuffer.current.length > VARIANCE_WINDOW) {
      accelXBuffer.current.shift();
      accelYBuffer.current.shift();
    }

    let varianceShake = false;
    if (accelXBuffer.current.length >= VARIANCE_WINDOW) {
      const meanX = accelXBuffer.current.reduce((a, b) => a + b, 0) / VARIANCE_WINDOW;
      const meanY = accelYBuffer.current.reduce((a, b) => a + b, 0) / VARIANCE_WINDOW;
      const varX = accelXBuffer.current.reduce((sum, v) => sum + (v - meanX) ** 2, 0) / VARIANCE_WINDOW;
      const varY = accelYBuffer.current.reduce((sum, v) => sum + (v - meanY) ** 2, 0) / VARIANCE_WINDOW;
      const totalVariance = varX + varY;
      varianceShake = totalVariance > VARIANCE_THRESHOLD;
    }

    // Combine shake signals: any two of three = definitely shaking
    const shakeSignals = [gyroShake, jerkShake, varianceShake].filter(Boolean).length;
    const currentlyShaking = shakeSignals >= 2;

    // Update shake confidence with hysteresis
    if (currentlyShaking) {
      // Quick ramp up when shaking detected
      shakeConfidence.current = Math.min(1, shakeConfidence.current + 0.15);
    } else {
      // Slow recovery after shaking stops
      shakeConfidence.current = Math.max(0, shakeConfidence.current - SHAKE_RECOVERY_SPEED);
    }
    isShaking.current = shakeConfidence.current > 0.3;

    // ===== VEHICLE MOTION EXTRACTION =====

    // Calculate suppression factor (1 = no suppression, 0 = full suppression)
    const suppressionFactor = isShaking.current
      ? SHAKE_SUPPRESSION
      : 1 - shakeConfidence.current * 0.8;

    // Stage 1: Heavy low-pass filter for vehicle motion envelope
    vehicleX.current += VEHICLE_FILTER_ALPHA * (ax * suppressionFactor - vehicleX.current);
    vehicleY.current += VEHICLE_FILTER_ALPHA * (ay * suppressionFactor - vehicleY.current);

    // Calculate sensitivity multiplier
    const sensitivityMultiplier = 0.5 + state.sensitivity * 1.5;

    // Map filtered values to dot displacement
    // x-axis: lateral movement (vehicle turning)
    // y-axis: forward/backward (acceleration/braking)
    const targetX = -vehicleX.current * MAX_DISPLACEMENT * sensitivityMultiplier;
    const targetY = vehicleY.current * MAX_DISPLACEMENT * sensitivityMultiplier;

    // Stage 2: Additional smoothing for dot animation
    smoothDotX.current += DOT_SMOOTHING_ALPHA * (targetX - smoothDotX.current);
    smoothDotY.current += DOT_SMOOTHING_ALPHA * (targetY - smoothDotY.current);

    // Clamp to max displacement
    dotX.value = Math.max(-MAX_DISPLACEMENT, Math.min(MAX_DISPLACEMENT, smoothDotX.current));
    dotY.value = Math.max(-MAX_DISPLACEMENT, Math.min(MAX_DISPLACEMENT, smoothDotY.current));

    // ===== VEHICLE DETECTION (for automatic mode) =====
    const motionMagnitude = Math.sqrt(vehicleX.current ** 2 + vehicleY.current ** 2);
    vehicleMotionBuffer.current.push(motionMagnitude);
    if (vehicleMotionBuffer.current.length > VEHICLE_DETECTION_SAMPLES) {
      vehicleMotionBuffer.current.shift();
    }

    if (vehicleMotionBuffer.current.length >= VEHICLE_DETECTION_SAMPLES) {
      const avgMotion = vehicleMotionBuffer.current.reduce((a, b) => a + b, 0) / vehicleMotionBuffer.current.length;
      // Vehicle motion: sustained, moderate acceleration without shaking
      const isVehicleMotion = avgMotion > VEHICLE_MOTION_MIN &&
        avgMotion < VEHICLE_MOTION_MAX &&
        !isShaking.current;

      // Smooth vehicle detection with hysteresis
      if (isVehicleMotion) {
        vehicleConfidence.current = Math.min(1, vehicleConfidence.current + 0.02);
      } else {
        vehicleConfidence.current = Math.max(0, vehicleConfidence.current - 0.01);
      }

      setVehicleDetected(vehicleConfidence.current > 0.5);
    }
  }, [state.sensitivity, dotX, dotY, setVehicleDetected]);

  useEffect(() => {
    if (Platform.OS === 'web') {
      return;
    }

    let subscription: { remove: () => void } | null = null;

    const startSensor = async () => {
      try {
        // Use DeviceMotion for fused accelerometer + gyroscope data
        const { DeviceMotion } = await import('expo-sensors');
        const isAvailable = await DeviceMotion.isAvailableAsync();
        if (!isAvailable) {
          // Fallback to accelerometer only
          const { Accelerometer } = await import('expo-sensors');
          const accelAvailable = await Accelerometer.isAvailableAsync();
          if (!accelAvailable) return;

          Accelerometer.setUpdateInterval(16);
          subscription = Accelerometer.addListener((data) => {
            processMotionData({
              acceleration: null,
              accelerationIncludingGravity: { x: data.x, y: data.y, z: 0 },
              rotationRate: null,
            });
          });
          subscriptionRef.current = subscription;
          return;
        }

        // Set high update rate for smooth animation
        DeviceMotion.setUpdateInterval(16);

        subscription = DeviceMotion.addListener((data) => {
          processMotionData({
            acceleration: data.acceleration,
            accelerationIncludingGravity: data.accelerationIncludingGravity,
            rotationRate: data.rotationRate,
          });
        });
        subscriptionRef.current = subscription;
      } catch (e) {
        // Sensor not available, silently fail
      }
    };

    startSensor();

    return () => {
      if (subscription) {
        subscription.remove();
      }
      subscriptionRef.current = null;
    };
  }, [processMotionData]);

  return { dotX, dotY };
}
