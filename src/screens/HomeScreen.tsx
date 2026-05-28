import React, {useCallback, useEffect, useState} from 'react';
import {
  AppState,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import type {NativeStackScreenProps} from '@react-navigation/native-stack';
import StatusCard from '../components/StatusCard';
import {
  hasClaudeApiKey,
  hasGroqApiKey,
  hasMicPermission,
  hasOverlayPermission,
  isAccessibilityEnabled,
  isOverlayRunning,
  openAccessibilitySettings,
  requestMicPermission,
  requestOverlayPermission,
  startOverlay,
  stopOverlay,
} from '../utils/storage';
import type {RootStackParamList} from '../../App';

type Props = NativeStackScreenProps<RootStackParamList, 'Home'>;

export default function HomeScreen({navigation}: Props) {
  const [overlayPerm, setOverlayPerm] = useState(false);
  const [a11y, setA11y] = useState(false);
  const [mic, setMic] = useState(false);
  const [groqKey, setGroqKey] = useState(false);
  const [claudeKey, setClaudeKey] = useState(false);
  const [overlayRunning, setOverlayRunning] = useState(false);

  const refresh = useCallback(async () => {
    const [op, a, m, g, c, r] = await Promise.all([
      hasOverlayPermission(),
      isAccessibilityEnabled(),
      hasMicPermission(),
      hasGroqApiKey(),
      hasClaudeApiKey(),
      isOverlayRunning(),
    ]);
    setOverlayPerm(op);
    setA11y(a);
    setMic(m);
    setGroqKey(g);
    setClaudeKey(c);
    setOverlayRunning(r);
  }, []);

  useEffect(() => {
    refresh();
    const sub = AppState.addEventListener('change', state => {
      if (state === 'active') refresh();
    });
    return () => sub.remove();
  }, [refresh]);

  const allReady = overlayPerm && a11y && mic && groqKey;

  const toggleOverlay = async () => {
    if (overlayRunning) await stopOverlay();
    else await startOverlay();
    setTimeout(refresh, 300);
  };

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>VoiceBoard</Text>
        <Text style={styles.subtitle}>
          Floating mic pill that lives above any keyboard. Tap, speak, tap again — the
          transcribed text is injected into whatever you're typing in.
        </Text>

        <View style={styles.bigCta}>
          <TouchableOpacity
            style={[
              styles.bigButton,
              !allReady && styles.bigButtonDisabled,
              overlayRunning && styles.bigButtonStop,
            ]}
            onPress={toggleOverlay}
            disabled={!allReady}>
            <Text style={styles.bigButtonText}>
              {overlayRunning ? 'Stop overlay' : 'Start overlay'}
            </Text>
          </TouchableOpacity>
          {!allReady ? (
            <Text style={styles.bigCtaHint}>
              Grant the permissions below before starting.
            </Text>
          ) : null}
        </View>

        <StatusCard
          title="Display over other apps"
          subtitle={
            overlayPerm
              ? 'Granted. VoiceBoard can draw the floating pill.'
              : 'Required to show the floating mic over your keyboard.'
          }
          ok={overlayPerm}
          cta={
            overlayPerm
              ? undefined
              : {label: 'Open overlay settings', onPress: requestOverlayPermission}
          }
        />

        <StatusCard
          title="Accessibility service"
          subtitle={
            a11y
              ? 'Enabled. Transcriptions can be injected into the focused field.'
              : 'Lets VoiceBoard set text on the field you are typing in. Without this, transcribed text is copied to clipboard instead.'
          }
          ok={a11y}
          cta={
            a11y
              ? undefined
              : {label: 'Open accessibility settings', onPress: openAccessibilitySettings}
          }
        />

        <StatusCard
          title="Microphone"
          subtitle={
            mic ? 'Granted.' : 'Required to record what you say.'
          }
          ok={mic}
          cta={
            mic
              ? undefined
              : {
                  label: 'Grant microphone',
                  onPress: async () => {
                    await requestMicPermission();
                    refresh();
                  },
                }
          }
        />

        <StatusCard
          title="Transcription engine"
          subtitle={
            groqKey
              ? 'Groq API key saved. Free Whisper-large-v3-turbo will be used by default.'
              : 'Add a Groq API key for free remote transcription, or download a local Whisper model in Settings.'
          }
          ok={groqKey}
          cta={{label: 'Open settings', onPress: () => navigation.navigate('Settings')}}
        />

        <StatusCard
          title="Claude post-processing"
          subtitle={
            claudeKey
              ? 'Claude key saved. Transcripts get cleaned per the selected mode.'
              : 'Optional. Without it, the raw transcription is injected as-is.'
          }
          ok={claudeKey}
          cta={{label: 'Open settings', onPress: () => navigation.navigate('Settings')}}
        />

        <View style={styles.helpBlock}>
          <Text style={styles.helpTitle}>How it works</Text>
          <Text style={styles.helpStep}>1. Grant the three permissions above.</Text>
          <Text style={styles.helpStep}>2. Add a Groq key (or download a local model).</Text>
          <Text style={styles.helpStep}>3. Tap "Start overlay" — a small pill appears.</Text>
          <Text style={styles.helpStep}>
            4. Switch to any app, tap into a text field, tap the pill, speak, tap again.
          </Text>
          <Text style={styles.helpStep}>
            5. Long-press the pill to switch Claude mode. Drag to reposition.
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {flex: 1, backgroundColor: '#F3F4F6'},
  container: {padding: 20, paddingBottom: 40},
  title: {fontSize: 28, fontWeight: '700', color: '#111827'},
  subtitle: {fontSize: 14, color: '#6B7280', marginTop: 4, marginBottom: 20, lineHeight: 20},
  bigCta: {marginBottom: 20},
  bigButton: {
    backgroundColor: '#7F77DD',
    paddingVertical: 16,
    borderRadius: 14,
    alignItems: 'center',
  },
  bigButtonDisabled: {opacity: 0.5},
  bigButtonStop: {backgroundColor: '#EF4444'},
  bigButtonText: {color: '#FFFFFF', fontWeight: '700', fontSize: 16},
  bigCtaHint: {color: '#6B7280', fontSize: 12, marginTop: 8, textAlign: 'center'},
  helpBlock: {
    marginTop: 16,
    padding: 16,
    backgroundColor: '#EEF2FF',
    borderRadius: 12,
  },
  helpTitle: {fontWeight: '700', color: '#3730A3', marginBottom: 8},
  helpStep: {color: '#3730A3', fontSize: 13, marginBottom: 4},
});
