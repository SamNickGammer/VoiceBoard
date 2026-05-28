import type {Mode} from '../native/VoiceBoardModule';

export const MODE_DESCRIPTIONS: Record<Mode, string> = {
  default:
    'Smart dictation. Applies self-corrections ("4 no no 5" → 5), drops filler ("um", "matlab"), preserves your voice. Hinglish on English keyboards, Devanagari on Hindi keyboards.',
  formal:
    'Polished message. Same intent-recovery as default, then rewritten in a professional but human tone.',
  generate:
    'Treats your speech as a brief — writes the actual message the recipient should receive. Use when you want the AI to author for you, not transcribe you.',
};
