import VoiceBoard, {type Engine, type Mode} from '../native/VoiceBoardModule';

export const getEngine = () => VoiceBoard.getEngine();
export const setEngine = (value: Engine) => VoiceBoard.setEngine(value);

export const getMode = () => VoiceBoard.getMode();
export const setMode = (value: Mode) => VoiceBoard.setMode(value);

export const setApiKey = (value: string) => VoiceBoard.setApiKey(value);
export const hasApiKey = () => VoiceBoard.hasApiKey();

export const isKeyboardEnabled = () => VoiceBoard.isKeyboardEnabled();
export const hasMicPermission = () => VoiceBoard.hasMicPermission();
export const requestMicPermission = () => VoiceBoard.requestMicPermission();

export const openImeSettings = () => VoiceBoard.openImeSettings();
export const showImePicker = () => VoiceBoard.showImePicker();
