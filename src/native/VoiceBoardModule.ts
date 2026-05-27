import {NativeModules} from 'react-native';

export type Engine = 'claude' | 'local';
export type Mode = 'default' | 'formal' | 'generate';

type VoiceBoardModuleType = {
  getEngine(): Promise<Engine>;
  setEngine(value: Engine): Promise<void>;
  getMode(): Promise<Mode>;
  setMode(value: Mode): Promise<void>;
  setApiKey(value: string): Promise<void>;
  hasApiKey(): Promise<boolean>;
  isKeyboardEnabled(): Promise<boolean>;
  hasMicPermission(): Promise<boolean>;
  requestMicPermission(): Promise<boolean>;
  openImeSettings(): void;
  showImePicker(): void;
};

const {VoiceBoard} = NativeModules as {VoiceBoard: VoiceBoardModuleType};

if (!VoiceBoard) {
  throw new Error(
    'VoiceBoard native module is not linked. Rebuild the Android app.',
  );
}

export default VoiceBoard;
