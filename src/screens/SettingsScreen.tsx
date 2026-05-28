import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  ActivityIndicator,
  Alert,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {VoiceBoardEvents, type DownloadEvent, type ModelCatalogEntry, type Mode, type TranscriptionEngine} from '../native/VoiceBoardModule';
import {MODE_DESCRIPTIONS, callClaude} from '../utils/claudeApi';
import ConsoleView, {type ConsoleLine} from '../components/ConsoleView';
import {
  cancelDownload,
  deleteModel,
  downloadModel,
  getActiveModel,
  getMode,
  getTranscriptionEngine,
  hasClaudeApiKey,
  hasGroqApiKey,
  isLocalWhisperLibAvailable,
  listModelCatalog,
  setActiveModel,
  setClaudeApiKey as saveClaudeKey,
  setGroqApiKey as saveGroqKey,
  setMode as saveMode,
  setTranscriptionEngine,
  testGroqKey,
} from '../utils/storage';

const MODES: Mode[] = ['default', 'formal', 'generate'];

export default function SettingsScreen() {
  const [engine, setEngine] = useState<TranscriptionEngine>('groq');
  const [mode, setMode] = useState<Mode>('default');

  const [groqInput, setGroqInput] = useState('');
  const [groqSaved, setGroqSaved] = useState(false);
  const [groqTesting, setGroqTesting] = useState(false);
  const [groqTestResult, setGroqTestResult] = useState<string | null>(null);

  const [claudeInput, setClaudeInput] = useState('');
  const [claudeSaved, setClaudeSaved] = useState(false);
  const [claudeTesting, setClaudeTesting] = useState(false);
  const [claudeTestResult, setClaudeTestResult] = useState<string | null>(null);

  const [catalog, setCatalog] = useState<ModelCatalogEntry[]>([]);
  const [activeModel, setActive] = useState<string | null>(null);
  const [localLibOk, setLocalLibOk] = useState(false);
  const [logs, setLogs] = useState<ConsoleLine[]>([]);

  const refresh = useCallback(async () => {
    const [e, m, g, c, cat, am, lib] = await Promise.all([
      getTranscriptionEngine(),
      getMode(),
      hasGroqApiKey(),
      hasClaudeApiKey(),
      listModelCatalog(),
      getActiveModel(),
      isLocalWhisperLibAvailable(),
    ]);
    setEngine(e);
    setMode(m);
    setGroqSaved(g);
    setClaudeSaved(c);
    setCatalog(cat);
    setActive(am);
    setLocalLibOk(lib);
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  useEffect(() => {
    const sub = VoiceBoardEvents.addListener('whisper-download', (ev: DownloadEvent) => {
      const text =
        ev.status === 'progress' && ev.bytes && ev.total && ev.total > 0
          ? `${ev.name}: ${ev.message} (${Math.round((ev.bytes / ev.total) * 100)}%)`
          : `${ev.name}: ${ev.message}`;
      setLogs(prev => [...prev.slice(-200), {ts: Date.now(), level: ev.status, text}]);
      if (ev.status === 'done' || ev.status === 'error') {
        refresh();
      }
    });
    return () => sub.remove();
  }, [refresh]);

  const onSelectEngine = async (value: TranscriptionEngine) => {
    setEngine(value);
    await setTranscriptionEngine(value);
  };

  const onSelectMode = async (value: Mode) => {
    setMode(value);
    await saveMode(value);
  };

  const onSaveGroq = async () => {
    if (!groqInput.trim()) return;
    await saveGroqKey(groqInput.trim());
    setGroqSaved(true);
    setGroqInput('');
  };

  const onClearGroq = async () => {
    await saveGroqKey('');
    setGroqSaved(false);
    setGroqTestResult(null);
  };

  const onTestGroq = async () => {
    const key = groqInput.trim();
    if (!key) return;
    setGroqTesting(true);
    setGroqTestResult(null);
    try {
      const text = await testGroqKey(key);
      setGroqTestResult(text ? `Empty (expected) — auth OK. "${text}"` : 'Auth OK — empty (silence sent).');
    } catch (e) {
      setGroqTestResult(`Error: ${(e as Error).message}`);
    } finally {
      setGroqTesting(false);
    }
  };

  const onSaveClaude = async () => {
    if (!claudeInput.trim()) return;
    await saveClaudeKey(claudeInput.trim());
    setClaudeSaved(true);
    setClaudeInput('');
  };

  const onClearClaude = async () => {
    await saveClaudeKey('');
    setClaudeSaved(false);
    setClaudeTestResult(null);
  };

  const onTestClaude = async () => {
    const key = claudeInput.trim();
    if (!key) return;
    setClaudeTesting(true);
    setClaudeTestResult(null);
    try {
      const text = await callClaude('hey can you confirm you got this', 'formal', key);
      setClaudeTestResult(text || '(empty response)');
    } catch (e) {
      setClaudeTestResult(`Error: ${(e as Error).message}`);
    } finally {
      setClaudeTesting(false);
    }
  };

  const onDownload = (name: string) => {
    downloadModel(name);
    setLogs(prev => [...prev, {ts: Date.now(), level: 'step', text: `${name}: requested`}]);
  };

  const onCancel = (name: string) => {
    cancelDownload(name);
    setLogs(prev => [...prev, {ts: Date.now(), level: 'info', text: `${name}: cancel requested`}]);
  };

  const onDelete = (name: string) => {
    Alert.alert('Delete model', `Remove ${name}?`, [
      {text: 'Cancel'},
      {text: 'Delete', style: 'destructive', onPress: async () => {
        await deleteModel(name);
        await refresh();
      }},
    ]);
  };

  const onSetActive = async (name: string) => {
    await setActiveModel(name);
    setActive(name);
    setEngine('local');
    await setTranscriptionEngine('local');
  };

  const localReady = useMemo(
    () => localLibOk && !!activeModel,
    [localLibOk, activeModel],
  );

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.section}>Transcription engine</Text>
        <View style={styles.row}>
          <Pill label="Groq Whisper" active={engine === 'groq'} onPress={() => onSelectEngine('groq')} />
          <Pill
            label="Local Whisper"
            active={engine === 'local'}
            onPress={() => {
              if (!localReady) {
                Alert.alert(
                  'Local Whisper not ready',
                  !localLibOk
                    ? 'The native whisper.cpp library failed to load. See logs.'
                    : 'Download a model below before selecting Local.',
                );
                return;
              }
              onSelectEngine('local');
            }}
          />
        </View>
        <Text style={styles.hint}>
          Active engine is what the floating pill will use. Groq is free, online, ~1s. Local runs
          fully on-device (no network) but you need to download a model.
        </Text>

        <Text style={styles.section}>Claude mode (post-processing)</Text>
        <View style={styles.row}>
          {MODES.map(m => (
            <Pill key={m} label={cap(m)} active={mode === m} onPress={() => onSelectMode(m)} />
          ))}
        </View>
        <View style={styles.modeBlock}>
          <Text style={styles.modeTitle}>{cap(mode)}</Text>
          <Text style={styles.modeDesc}>{MODE_DESCRIPTIONS[mode]}</Text>
        </View>

        <Text style={styles.section}>Groq API key</Text>
        <Text style={styles.hint}>
          Free at console.groq.com. Stored in Android Keystore. {groqSaved ? 'Saved.' : 'Not set.'}
        </Text>
        <TextInput
          style={styles.input}
          placeholder="gsk_..."
          placeholderTextColor="#9CA3AF"
          value={groqInput}
          onChangeText={setGroqInput}
          autoCapitalize="none"
          autoCorrect={false}
          secureTextEntry
        />
        <View style={styles.row}>
          <TouchableOpacity
            style={[styles.btn, {opacity: groqInput.trim() ? 1 : 0.4}]}
            onPress={onSaveGroq} disabled={!groqInput.trim()}>
            <Text style={styles.btnText}>Save key</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.btn, styles.btnGhost, {marginLeft: 8, opacity: groqSaved ? 1 : 0.4}]}
            onPress={onClearGroq} disabled={!groqSaved}>
            <Text style={[styles.btnText, {color: '#7F77DD'}]}>Clear</Text>
          </TouchableOpacity>
        </View>
        <TouchableOpacity
          style={[styles.testBtn, {opacity: groqInput.trim() && !groqTesting ? 1 : 0.4}]}
          onPress={onTestGroq}
          disabled={!groqInput.trim() || groqTesting}>
          {groqTesting ? <ActivityIndicator color="#7F77DD" /> : (
            <Text style={[styles.btnText, {color: '#7F77DD'}]}>Test Groq key (sends 0.5s silence)</Text>
          )}
        </TouchableOpacity>
        {groqTestResult ? <Text style={styles.testResult}>{groqTestResult}</Text> : null}

        <Text style={styles.section}>Local Whisper models</Text>
        <Text style={styles.hint}>
          {localLibOk
            ? 'Native whisper.cpp library is loaded. Pick a model below.'
            : 'Native library not loaded yet — first install needs a rebuild. After rebuild this will say "loaded".'}
          {' '}Active model: {activeModel ?? 'none'}.
        </Text>
        {catalog.map(m => (
          <View key={m.name} style={styles.modelRow}>
            <View style={{flex: 1}}>
              <Text style={styles.modelTitle}>
                {m.name}{m.active ? '  ★' : ''}
              </Text>
              <Text style={styles.modelLabel}>{m.label}</Text>
            </View>
            <View style={{flexDirection: 'row'}}>
              {!m.installed ? (
                <TouchableOpacity style={styles.modelBtn} onPress={() => onDownload(m.name)}>
                  <Text style={styles.modelBtnText}>Download</Text>
                </TouchableOpacity>
              ) : (
                <>
                  {!m.active ? (
                    <TouchableOpacity style={styles.modelBtn} onPress={() => onSetActive(m.name)}>
                      <Text style={styles.modelBtnText}>Use</Text>
                    </TouchableOpacity>
                  ) : null}
                  <TouchableOpacity
                    style={[styles.modelBtn, styles.modelBtnDanger, {marginLeft: 6}]}
                    onPress={() => onDelete(m.name)}>
                    <Text style={[styles.modelBtnText, {color: '#EF4444'}]}>Delete</Text>
                  </TouchableOpacity>
                </>
              )}
              <TouchableOpacity
                style={[styles.modelBtn, {marginLeft: 6}]}
                onPress={() => onCancel(m.name)}>
                <Text style={styles.modelBtnText}>×</Text>
              </TouchableOpacity>
            </View>
          </View>
        ))}
        <ConsoleView lines={logs} />

        <Text style={styles.section}>Claude API key (for mode post-processing)</Text>
        <Text style={styles.hint}>
          Optional. If unset, transcribed text is injected raw. {claudeSaved ? 'Saved.' : 'Not set.'}
        </Text>
        <TextInput
          style={styles.input}
          placeholder="sk-ant-..."
          placeholderTextColor="#9CA3AF"
          value={claudeInput}
          onChangeText={setClaudeInput}
          autoCapitalize="none"
          autoCorrect={false}
          secureTextEntry
        />
        <View style={styles.row}>
          <TouchableOpacity
            style={[styles.btn, {opacity: claudeInput.trim() ? 1 : 0.4}]}
            onPress={onSaveClaude} disabled={!claudeInput.trim()}>
            <Text style={styles.btnText}>Save key</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.btn, styles.btnGhost, {marginLeft: 8, opacity: claudeSaved ? 1 : 0.4}]}
            onPress={onClearClaude} disabled={!claudeSaved}>
            <Text style={[styles.btnText, {color: '#7F77DD'}]}>Clear</Text>
          </TouchableOpacity>
        </View>
        <TouchableOpacity
          style={[styles.testBtn, {opacity: claudeInput.trim() && !claudeTesting ? 1 : 0.4}]}
          onPress={onTestClaude}
          disabled={!claudeInput.trim() || claudeTesting}>
          {claudeTesting ? <ActivityIndicator color="#7F77DD" /> : (
            <Text style={[styles.btnText, {color: '#7F77DD'}]}>Test Claude key (formal mode)</Text>
          )}
        </TouchableOpacity>
        {claudeTestResult ? <Text style={styles.testResult}>{claudeTestResult}</Text> : null}
      </ScrollView>
    </SafeAreaView>
  );
}

function Pill({
  label,
  active,
  onPress,
}: {
  label: string;
  active: boolean;
  onPress: () => void;
}) {
  return (
    <TouchableOpacity
      style={[styles.pill, active ? styles.pillActive : styles.pillInactive]}
      onPress={onPress}>
      <Text style={active ? styles.pillTextActive : styles.pillTextInactive}>{label}</Text>
    </TouchableOpacity>
  );
}

const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

const styles = StyleSheet.create({
  safe: {flex: 1, backgroundColor: '#F3F4F6'},
  container: {padding: 20, paddingBottom: 60},
  section: {fontSize: 16, fontWeight: '700', color: '#111827', marginTop: 18, marginBottom: 8},
  row: {flexDirection: 'row', flexWrap: 'wrap'},
  hint: {fontSize: 12, color: '#6B7280', marginTop: 4, marginBottom: 8, lineHeight: 16},
  pill: {
    paddingHorizontal: 14, paddingVertical: 8, borderRadius: 16,
    marginRight: 8, marginBottom: 8,
  },
  pillActive: {backgroundColor: '#7F77DD'},
  pillInactive: {borderWidth: 1, borderColor: '#9CA3AF'},
  pillTextActive: {color: '#FFFFFF', fontWeight: '600'},
  pillTextInactive: {color: '#374151', fontWeight: '500'},
  modeBlock: {
    padding: 12, backgroundColor: '#FFFFFF', borderRadius: 10,
    borderWidth: 1, borderColor: '#E5E7EB',
  },
  modeTitle: {fontWeight: '700', color: '#111827', marginBottom: 4},
  modeDesc: {color: '#374151', fontSize: 13, lineHeight: 18},
  input: {
    backgroundColor: '#FFFFFF', padding: 12, borderRadius: 10,
    borderWidth: 1, borderColor: '#E5E7EB', color: '#111827', marginBottom: 8,
  },
  btn: {backgroundColor: '#7F77DD', paddingVertical: 10, paddingHorizontal: 16, borderRadius: 8},
  btnGhost: {backgroundColor: 'transparent', borderWidth: 1, borderColor: '#7F77DD'},
  btnText: {color: '#FFFFFF', fontWeight: '600'},
  testBtn: {
    marginTop: 12, paddingVertical: 10, paddingHorizontal: 16,
    borderRadius: 8, borderWidth: 1, borderColor: '#7F77DD', alignItems: 'center',
  },
  testResult: {
    marginTop: 10, padding: 12, backgroundColor: '#FFFFFF', borderRadius: 8,
    color: '#111827', borderWidth: 1, borderColor: '#E5E7EB',
  },
  modelRow: {
    flexDirection: 'row', alignItems: 'center',
    padding: 12, backgroundColor: '#FFFFFF', borderRadius: 10,
    borderWidth: 1, borderColor: '#E5E7EB', marginBottom: 6,
  },
  modelTitle: {fontWeight: '700', color: '#111827', fontSize: 14},
  modelLabel: {color: '#6B7280', fontSize: 12, marginTop: 2},
  modelBtn: {
    paddingVertical: 6, paddingHorizontal: 10, borderRadius: 6,
    borderWidth: 1, borderColor: '#7F77DD',
  },
  modelBtnDanger: {borderColor: '#EF4444'},
  modelBtnText: {color: '#7F77DD', fontSize: 12, fontWeight: '600'},
});
