import type {Mode} from '../native/VoiceBoardModule';

const ENDPOINT = 'https://api.anthropic.com/v1/messages';
const MODEL = 'claude-sonnet-4-20250514';
const MAX_TOKENS = 300;
const API_VERSION = '2023-06-01';

export const PROMPTS: Record<Mode, string> = {
  default:
    'You are a transcription cleaner. The user has spoken text that has been transcribed. Return only the cleaned transcription — fix obvious transcription errors, keep the original language (Hindi, English, or Hinglish mix). Return ONLY the final text, nothing else. No explanation, no quotes.',
  formal:
    'You are a professional writing assistant. The user spoke casually. Convert their speech into polished, formal written English. Return ONLY the final text, nothing else. No explanation, no preamble, no quotes.',
  generate:
    'You are a message writer. The user described what they want to say. Write the actual message for them. Keep it concise and natural. Return ONLY the final text, nothing else. No explanation, no preamble, no quotes.',
};

export const MODE_DESCRIPTIONS: Record<Mode, string> = {
  default: 'Cleans up transcription errors and keeps the original language as-is.',
  formal: 'Rewrites casual speech as polished, formal English.',
  generate: 'Treats your speech as a brief — writes the actual message for you.',
};

type ContentBlock = {type: string; text?: string};

export async function callClaude(
  transcript: string,
  mode: Mode,
  apiKey: string,
): Promise<string> {
  const res = await fetch(ENDPOINT, {
    method: 'POST',
    headers: {
      'x-api-key': apiKey,
      'anthropic-version': API_VERSION,
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      model: MODEL,
      max_tokens: MAX_TOKENS,
      system: PROMPTS[mode],
      messages: [{role: 'user', content: transcript}],
    }),
  });

  if (!res.ok) {
    const errBody = await res.text();
    throw new Error(`Claude API ${res.status}: ${errBody}`);
  }

  const data = (await res.json()) as {content?: ContentBlock[]};
  return (data.content ?? [])
    .filter(b => b.type === 'text' && typeof b.text === 'string')
    .map(b => b.text as string)
    .join('')
    .trim();
}
