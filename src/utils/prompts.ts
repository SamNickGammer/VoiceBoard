import type {Mode} from '../native/VoiceBoardModule';

export const MODE_DESCRIPTIONS: Record<Mode, string> = {
  default:
    'Cleans up transcription errors. If your keyboard is English, Hindi speech is romanised to Hinglish; on a Hindi keyboard it stays in Devanagari.',
  formal: 'Rewrites casual speech into a polished, formal message.',
  generate:
    'Treats your speech as a brief and writes the actual message for you.',
};
