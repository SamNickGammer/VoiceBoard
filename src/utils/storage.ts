import VoiceBoard, {
  type Mode,
  type TranscriptionEngine,
} from '../native/VoiceBoardModule';

export const getMode = () => VoiceBoard.getMode();
export const setMode = (value: Mode) => VoiceBoard.setMode(value);

export const getTranscriptionEngine = () => VoiceBoard.getTranscriptionEngine();
export const setTranscriptionEngine = (value: TranscriptionEngine) =>
  VoiceBoard.setTranscriptionEngine(value);
export const setGroqApiKey = (value: string) => VoiceBoard.setGroqApiKey(value);
export const hasGroqApiKey = () => VoiceBoard.hasGroqApiKey();
export const testGroqKey = (value: string) => VoiceBoard.testGroqKey(value);

export const hasMicPermission = () => VoiceBoard.hasMicPermission();
export const requestMicPermission = () => VoiceBoard.requestMicPermission();

export const hasOverlayPermission = () => VoiceBoard.hasOverlayPermission();
export const requestOverlayPermission = () => VoiceBoard.requestOverlayPermission();

export const isAccessibilityEnabled = () => VoiceBoard.isAccessibilityEnabled();
export const openAccessibilitySettings = () =>
  VoiceBoard.openAccessibilitySettings();

export const startOverlay = () => VoiceBoard.startOverlay();
export const stopOverlay = () => VoiceBoard.stopOverlay();
export const isOverlayRunning = () => VoiceBoard.isOverlayRunning();

export const listModelCatalog = () => VoiceBoard.listModelCatalog();
export const downloadModel = (name: string) => VoiceBoard.downloadModel(name);
export const cancelDownload = (name: string) =>
  VoiceBoard.cancelDownload(name);
export const deleteModel = (name: string) => VoiceBoard.deleteModel(name);
export const setActiveModel = (name: string | null) =>
  VoiceBoard.setActiveModel(name);
export const getActiveModel = () => VoiceBoard.getActiveModel();
export const isLocalWhisperLibAvailable = () =>
  VoiceBoard.isLocalWhisperLibAvailable();
