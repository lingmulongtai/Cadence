import { describe, it, expect } from 'vitest';

// Test the reducer logic directly without React components
type MotionCuesMode = 'off' | 'on' | 'automatic';
type DotColor = 'black' | 'white' | 'blue' | 'green' | 'red';
type DotSize = 'standard' | 'larger';
type DotCount = 'standard' | 'more';

interface MotionCuesState {
  mode: MotionCuesMode;
  dotColor: DotColor;
  dotSize: DotSize;
  dotCount: DotCount;
  sensitivity: number;
  hasSeenOnboarding: boolean;
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
  | { type: 'LOAD_SETTINGS'; payload: Partial<any> };

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

const initialState: MotionCuesState = {
  mode: 'on',
  dotColor: 'black',
  dotSize: 'standard',
  dotCount: 'standard',
  sensitivity: 0.5,
  hasSeenOnboarding: false,
  isActive: false,
  isVehicleDetected: false,
  isLoaded: false,
};

describe('MotionCues Reducer', () => {
  it('should set mode to on and activate', () => {
    const state = reducer(initialState, { type: 'SET_MODE', payload: 'on' });
    expect(state.mode).toBe('on');
    expect(state.isActive).toBe(true);
  });

  it('should set mode to off and deactivate', () => {
    const state = reducer({ ...initialState, isActive: true }, { type: 'SET_MODE', payload: 'off' });
    expect(state.mode).toBe('off');
    expect(state.isActive).toBe(false);
  });

  it('should handle automatic mode with vehicle detection', () => {
    let state = reducer(initialState, { type: 'SET_MODE', payload: 'automatic' });
    expect(state.isActive).toBe(false); // No vehicle detected yet

    state = reducer(state, { type: 'SET_VEHICLE_DETECTED', payload: true });
    expect(state.isActive).toBe(true); // Vehicle detected

    state = reducer(state, { type: 'SET_VEHICLE_DETECTED', payload: false });
    expect(state.isActive).toBe(false); // Vehicle no longer detected
  });

  it('should toggle active state', () => {
    // Start with mode 'on'
    let state = reducer({ ...initialState, mode: 'on', isActive: true }, { type: 'TOGGLE_ACTIVE' });
    expect(state.mode).toBe('off');
    expect(state.isActive).toBe(false);

    // Toggle back
    state = reducer(state, { type: 'TOGGLE_ACTIVE' });
    expect(state.mode).toBe('on');
    expect(state.isActive).toBe(true);
  });

  it('should update dot color', () => {
    const state = reducer(initialState, { type: 'SET_DOT_COLOR', payload: 'blue' });
    expect(state.dotColor).toBe('blue');
  });

  it('should update dot size', () => {
    const state = reducer(initialState, { type: 'SET_DOT_SIZE', payload: 'larger' });
    expect(state.dotSize).toBe('larger');
  });

  it('should update dot count', () => {
    const state = reducer(initialState, { type: 'SET_DOT_COUNT', payload: 'more' });
    expect(state.dotCount).toBe('more');
  });

  it('should update sensitivity', () => {
    const state = reducer(initialState, { type: 'SET_SENSITIVITY', payload: 0.75 });
    expect(state.sensitivity).toBe(0.75);
  });

  it('should mark onboarding as seen', () => {
    const state = reducer(initialState, { type: 'SET_ONBOARDING_SEEN' });
    expect(state.hasSeenOnboarding).toBe(true);
  });

  it('should load settings and mark as loaded', () => {
    const state = reducer(initialState, {
      type: 'LOAD_SETTINGS',
      payload: { mode: 'on', dotColor: 'red', sensitivity: 0.8 },
    });
    expect(state.isLoaded).toBe(true);
    expect(state.mode).toBe('on');
    expect(state.dotColor).toBe('red');
    expect(state.sensitivity).toBe(0.8);
    expect(state.isActive).toBe(true);
  });

  it('should set active to true', () => {
    const state = reducer(initialState, { type: 'SET_ACTIVE', payload: true });
    expect(state.mode).toBe('on');
    expect(state.isActive).toBe(true);
  });

  it('should set active to false', () => {
    const state = reducer({ ...initialState, mode: 'on', isActive: true }, { type: 'SET_ACTIVE', payload: false });
    expect(state.mode).toBe('off');
    expect(state.isActive).toBe(false);
  });
});
