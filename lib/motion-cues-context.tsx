import React, { createContext, useContext, useReducer, useEffect, useCallback } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';

export type MotionCuesMode = 'off' | 'on' | 'automatic';
export type DotColor = 'adaptive' | 'black' | 'white' | 'blue' | 'green' | 'red';
export type DotSize = 'standard' | 'larger';
export type DotCount = 'standard' | 'more';

export interface MotionCuesSettings {
  mode: MotionCuesMode;
  dotColor: DotColor;
  dotSize: DotSize;
  dotCount: DotCount;
  sensitivity: number;
  hasSeenOnboarding: boolean;
}

interface MotionCuesState extends MotionCuesSettings {
  isActive: boolean;
  isVehicleDetected: boolean;
  isLoaded: boolean;
}

type Action =
  | { type: 'SET_MODE'; payload: MotionCuesMode }
  | { type: 'SET_DOT_COLOR'; payload: DotColor }
  | { type: 'SET_DOT_SIZE'; payload: DotSize }
  | { type: 'SET_DOT_COUNT'; payload: DotCount }
  | { type: 'SET_SENSITIVITY'; payload: number }
  | { type: 'SET_ONBOARDING_SEEN' }
  | { type: 'SET_VEHICLE_DETECTED'; payload: boolean }
  | { type: 'TOGGLE_ACTIVE' }
  | { type: 'SET_ACTIVE'; payload: boolean }
  | { type: 'LOAD_SETTINGS'; payload: Partial<MotionCuesSettings> };

const STORAGE_KEY = '@motion_cues_settings';

const defaultSettings: MotionCuesSettings = {
  mode: 'on',
  dotColor: 'adaptive',
  dotSize: 'standard',
  dotCount: 'standard',
  sensitivity: 0.5,
  hasSeenOnboarding: false,
};

const initialState: MotionCuesState = {
  ...defaultSettings,
  isActive: false,
  isVehicleDetected: false,
  isLoaded: false,
};

function computeIsActive(state: MotionCuesState): boolean {
  if (state.mode === 'off') return false;
  if (state.mode === 'on') return true;
  if (state.mode === 'automatic') return state.isVehicleDetected;
  return false;
}

function reducer(state: MotionCuesState, action: Action): MotionCuesState {
  let newState: MotionCuesState;
  switch (action.type) {
    case 'SET_MODE':
      newState = { ...state, mode: action.payload };
      return { ...newState, isActive: computeIsActive(newState) };
    case 'SET_DOT_COLOR':
      return { ...state, dotColor: action.payload };
    case 'SET_DOT_SIZE':
      return { ...state, dotSize: action.payload };
    case 'SET_DOT_COUNT':
      return { ...state, dotCount: action.payload };
    case 'SET_SENSITIVITY':
      return { ...state, sensitivity: action.payload };
    case 'SET_ONBOARDING_SEEN':
      return { ...state, hasSeenOnboarding: true };
    case 'SET_VEHICLE_DETECTED':
      newState = { ...state, isVehicleDetected: action.payload };
      return { ...newState, isActive: computeIsActive(newState) };
    case 'TOGGLE_ACTIVE':
      if (state.mode === 'on') {
        newState = { ...state, mode: 'off' };
      } else {
        newState = { ...state, mode: 'on' };
      }
      return { ...newState, isActive: computeIsActive(newState) };
    case 'SET_ACTIVE':
      if (action.payload) {
        newState = { ...state, mode: 'on' };
      } else {
        newState = { ...state, mode: 'off' };
      }
      return { ...newState, isActive: computeIsActive(newState) };
    case 'LOAD_SETTINGS':
      newState = { ...state, ...action.payload, isLoaded: true };
      return { ...newState, isActive: computeIsActive(newState) };
    default:
      return state;
  }
}

interface MotionCuesContextType {
  state: MotionCuesState;
  dispatch: React.Dispatch<Action>;
  setMode: (mode: MotionCuesMode) => void;
  setDotColor: (color: DotColor) => void;
  setDotSize: (size: DotSize) => void;
  setDotCount: (count: DotCount) => void;
  setSensitivity: (value: number) => void;
  setOnboardingSeen: () => void;
  toggleActive: () => void;
  setActive: (active: boolean) => void;
  setVehicleDetected: (detected: boolean) => void;
}

const MotionCuesContext = createContext<MotionCuesContextType | undefined>(undefined);

export function MotionCuesProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);

  // Load settings from AsyncStorage on mount
  useEffect(() => {
    (async () => {
      try {
        const stored = await AsyncStorage.getItem(STORAGE_KEY);
        if (stored) {
          const parsed = JSON.parse(stored) as Partial<MotionCuesSettings>;
          dispatch({ type: 'LOAD_SETTINGS', payload: parsed });
        } else {
          dispatch({ type: 'LOAD_SETTINGS', payload: defaultSettings });
        }
      } catch {
        dispatch({ type: 'LOAD_SETTINGS', payload: defaultSettings });
      }
    })();
  }, []);

  // Save settings whenever they change
  useEffect(() => {
    if (!state.isLoaded) return;
    const settings: MotionCuesSettings = {
      mode: state.mode,
      dotColor: state.dotColor,
      dotSize: state.dotSize,
      dotCount: state.dotCount,
      sensitivity: state.sensitivity,
      hasSeenOnboarding: state.hasSeenOnboarding,
    };
    AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  }, [state.mode, state.dotColor, state.dotSize, state.dotCount, state.sensitivity, state.hasSeenOnboarding, state.isLoaded]);

  const setMode = useCallback((mode: MotionCuesMode) => dispatch({ type: 'SET_MODE', payload: mode }), []);
  const setDotColor = useCallback((color: DotColor) => dispatch({ type: 'SET_DOT_COLOR', payload: color }), []);
  const setDotSize = useCallback((size: DotSize) => dispatch({ type: 'SET_DOT_SIZE', payload: size }), []);
  const setDotCount = useCallback((count: DotCount) => dispatch({ type: 'SET_DOT_COUNT', payload: count }), []);
  const setSensitivity = useCallback((value: number) => dispatch({ type: 'SET_SENSITIVITY', payload: value }), []);
  const setOnboardingSeen = useCallback(() => dispatch({ type: 'SET_ONBOARDING_SEEN' }), []);
  const toggleActive = useCallback(() => dispatch({ type: 'TOGGLE_ACTIVE' }), []);
  const setActive = useCallback((active: boolean) => dispatch({ type: 'SET_ACTIVE', payload: active }), []);
  const setVehicleDetected = useCallback((detected: boolean) => dispatch({ type: 'SET_VEHICLE_DETECTED', payload: detected }), []);

  return (
    <MotionCuesContext.Provider
      value={{
        state,
        dispatch,
        setMode,
        setDotColor,
        setDotSize,
        setDotCount,
        setSensitivity,
        setOnboardingSeen,
        toggleActive,
        setActive,
        setVehicleDetected,
      }}
    >
      {children}
    </MotionCuesContext.Provider>
  );
}

export function useMotionCues() {
  const context = useContext(MotionCuesContext);
  if (!context) {
    throw new Error('useMotionCues must be used within a MotionCuesProvider');
  }
  return context;
}
