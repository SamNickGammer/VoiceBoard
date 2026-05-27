import React, {useEffect, useState} from 'react';
import {
  ActivityIndicator,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import type {Engine, Mode} from '../native/VoiceBoardModule';
import {callClaude, MODE_DESCRIPTIONS} from '../utils/claudeApi';
import {
  getEngine,
  getMode,
  hasApiKey,
  setApiKey as saveApiKey,
  setEngine as saveEngine,
  setMode as saveMode,
} from '../utils/storage';

const MODES: Mode[] = ['default', 'formal', 'generate'];

export default function SettingsScreen() {
  const [engine, setEngine] = useState<Engine>('claude');
  const [mode, setMode] = useState<Mode>('default');
  const [keyInput, setKeyInput] = useState('');
  const [keyStatus, setKeyStatus] = useState<'unknown' | 'set' | 'unset'>('unknown');
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      const [e, m, has] = await Promise.all([getEngine(), getMode(), hasApiKey()]);
      setEngine(e);
      setMode(m);
      setKeyStatus(has ? 'set' : 'unset');
    })();
  }, []);

  const onSelectEngine = async (value: Engine) => {
    if (value === 'local') return; // disabled
    setEngine(value);
    await saveEngine(value);
  };

  const onSelectMode = async (value: Mode) => {
    setMode(value);
    await saveMode(value);
  };

  const onSaveKey = async () => {
    if (!keyInput.trim()) return;
    setSaving(true);
    try {
      await saveApiKey(keyInput.trim());
      setKeyStatus('set');
      setKeyInput('');
    } finally {
      setSaving(false);
    }
  };

  const onClearKey = async () => {
    setSaving(true);
    try {
      await saveApiKey('');
      setKeyStatus('unset');
      setTestResult(null);
    } finally {
      setSaving(false);
    }
  };

  const onTestKey = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      const text = await callClaude('hey can you confirm you got this', 'formal', keyInput.trim());
      setTestResult(text || '(empty response)');
    } catch (e) {
      setTestResult(`Error: ${(e as Error).message}`);
    } finally {
      setTesting(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.section}>Transcription engine</Text>
        <View style={styles.row}>
          <Pill
            label="Claude API"
            active={engine === 'claude'}
            onPress={() => onSelectEngine('claude')}
          />
          <Pill label="Local (Whisper)" active={engine === 'local'} disabled onPress={() => {}} />
        </View>
        <Text style={styles.hint}>Local Whisper arrives in Phase 2.</Text>

        <Text style={styles.section}>Default mode</Text>
        <View style={styles.row}>
          {MODES.map(m => (
            <Pill key={m} label={cap(m)} active={mode === m} onPress={() => onSelectMode(m)} />
          ))}
        </View>
        <View style={styles.modeBlock}>
          <Text style={styles.modeTitle}>{cap(mode)}</Text>
          <Text style={styles.modeDesc}>{MODE_DESCRIPTIONS[mode]}</Text>
        </View>

        <Text style={styles.section}>Claude API key</Text>
        <Text style={styles.hint}>
          Stored in Android Keystore (EncryptedSharedPreferences). The key never leaves the device
          except in requests to Anthropic. {keyStatus === 'set' ? 'A key is currently saved.' : 'No key saved yet.'}
        </Text>
        <TextInput
          style={styles.input}
          placeholder="sk-ant-..."
          placeholderTextColor="#9CA3AF"
          value={keyInput}
          onChangeText={setKeyInput}
          autoCapitalize="none"
          autoCorrect={false}
          secureTextEntry
        />
        <View style={styles.row}>
          <TouchableOpacity
            style={[styles.btn, {opacity: keyInput.trim() && !saving ? 1 : 0.4}]}
            onPress={onSaveKey}
            disabled={!keyInput.trim() || saving}>
            <Text style={styles.btnText}>{saving ? 'Saving...' : 'Save key'}</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.btn, styles.btnGhost, {marginLeft: 8}]}
            onPress={onClearKey}
            disabled={saving || keyStatus !== 'set'}>
            <Text style={[styles.btnText, {color: '#7F77DD'}]}>Clear</Text>
          </TouchableOpacity>
        </View>

        <TouchableOpacity
          style={[styles.testBtn, {opacity: keyInput.trim() && !testing ? 1 : 0.4}]}
          onPress={onTestKey}
          disabled={!keyInput.trim() || testing}>
          {testing ? (
            <ActivityIndicator color="#7F77DD" />
          ) : (
            <Text style={[styles.btnText, {color: '#7F77DD'}]}>Test API key (formal mode)</Text>
          )}
        </TouchableOpacity>
        {testResult ? <Text style={styles.testResult}>{testResult}</Text> : null}
      </ScrollView>
    </SafeAreaView>
  );
}

function Pill({
  label,
  active,
  onPress,
  disabled,
}: {
  label: string;
  active: boolean;
  onPress: () => void;
  disabled?: boolean;
}) {
  return (
    <TouchableOpacity
      style={[
        styles.pill,
        active ? styles.pillActive : styles.pillInactive,
        disabled ? {opacity: 0.5} : null,
      ]}
      onPress={onPress}
      disabled={disabled}>
      <Text style={active ? styles.pillTextActive : styles.pillTextInactive}>{label}</Text>
    </TouchableOpacity>
  );
}

const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

const styles = StyleSheet.create({
  safe: {flex: 1, backgroundColor: '#F3F4F6'},
  container: {padding: 20, paddingBottom: 40},
  section: {fontSize: 16, fontWeight: '700', color: '#111827', marginTop: 18, marginBottom: 8},
  row: {flexDirection: 'row', flexWrap: 'wrap'},
  hint: {fontSize: 12, color: '#6B7280', marginTop: 4, marginBottom: 8, lineHeight: 16},
  pill: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
    marginRight: 8,
    marginBottom: 8,
  },
  pillActive: {backgroundColor: '#7F77DD'},
  pillInactive: {borderWidth: 1, borderColor: '#9CA3AF'},
  pillTextActive: {color: '#FFFFFF', fontWeight: '600'},
  pillTextInactive: {color: '#374151', fontWeight: '500'},
  modeBlock: {
    padding: 12,
    backgroundColor: '#FFFFFF',
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#E5E7EB',
  },
  modeTitle: {fontWeight: '700', color: '#111827', marginBottom: 4},
  modeDesc: {color: '#374151', fontSize: 13, lineHeight: 18},
  input: {
    backgroundColor: '#FFFFFF',
    padding: 12,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#E5E7EB',
    color: '#111827',
    marginBottom: 8,
  },
  btn: {
    backgroundColor: '#7F77DD',
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 8,
  },
  btnGhost: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: '#7F77DD',
  },
  btnText: {color: '#FFFFFF', fontWeight: '600'},
  testBtn: {
    marginTop: 16,
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#7F77DD',
    alignItems: 'center',
  },
  testResult: {
    marginTop: 12,
    padding: 12,
    backgroundColor: '#FFFFFF',
    borderRadius: 8,
    color: '#111827',
    borderWidth: 1,
    borderColor: '#E5E7EB',
  },
});
