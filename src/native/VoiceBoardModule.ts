import {NativeEventEmitter, NativeModules} from 'react-native';

export type TranscriptionEngine = 'groq' | 'local';
export type Mode = 'default' | 'formal' | 'generate';

export type ModelCatalogEntry = {
  name: string;
  label: string;
  url: string;
  sizeMb: number;
  installed: boolean;
  active: boolean;
};

export type DownloadEvent = {
  name: string;
  status: 'step' | 'info' | 'progress' | 'done' | 'error';
  message: string;
  bytes?: number;
  total?: number;
};

type VoiceBoardModuleType = {
  getMode(): Promise<Mode>;
  setMode(value: Mode): Promise<void>;

  setClaudeApiKey(value: string): Promise<void>;
  hasClaudeApiKey(): Promise<boolean>;

  getTranscriptionEngine(): Promise<TranscriptionEngine>;
  setTranscriptionEngine(value: TranscriptionEngine): Promise<void>;
  setGroqApiKey(value: string): Promise<void>;
  hasGroqApiKey(): Promise<boolean>;
  testGroqKey(value: string): Promise<string>;

  hasMicPermission(): Promise<boolean>;
  requestMicPermission(): Promise<boolean>;

  hasOverlayPermission(): Promise<boolean>;
  requestOverlayPermission(): void;

  isAccessibilityEnabled(): Promise<boolean>;
  openAccessibilitySettings(): void;

  startOverlay(): Promise<boolean>;
  stopOverlay(): Promise<boolean>;
  isOverlayRunning(): Promise<boolean>;

  listModelCatalog(): Promise<ModelCatalogEntry[]>;
  downloadModel(name: string): void;
  cancelDownload(name: string): void;
  deleteModel(name: string): Promise<boolean>;
  setActiveModel(name: string | null): Promise<void>;
  getActiveModel(): Promise<string | null>;
  isLocalWhisperLibAvailable(): Promise<boolean>;
};

const {VoiceBoard} = NativeModules as {VoiceBoard: VoiceBoardModuleType};

if (!VoiceBoard) {
  throw new Error(
    'VoiceBoard native module is not linked. Rebuild the Android app.',
  );
}

export const VoiceBoardEvents = new NativeEventEmitter(NativeModules.VoiceBoard);

export default VoiceBoard;
