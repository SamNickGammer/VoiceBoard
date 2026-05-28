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
  const [overlayRunning, setOverlayRunning] = useState(false);

  const refresh = useCallback(async () => {
    const [op, a, m, g, r] = await Promise.all([
      hasOverlayPermission(),
      isAccessibilityEnabled(),
      hasMicPermission(),
      hasGroqApiKey(),
      isOverlayRunning(),
    ]);
    setOverlayPerm(op);
    setA11y(a);
    setMic(m);
    setGroqKey(g);
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
          Floating mic pill that appears whenever a keyboard is open. Tap to dictate, tap again
          to inject the transcribed text into the focused field.
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
              Grant the permissions below and add a Groq key before starting.
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
              ? 'Enabled. The pill appears only when a keyboard is up, and transcripts are injected into the focused field.'
              : 'Required so the pill knows when a keyboard is open and can write back into the field you are typing in.'
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
          subtitle={mic ? 'Granted.' : 'Required to record what you say.'}
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
          title="Groq API key (Whisper + LLM)"
          subtitle={
            groqKey
              ? 'Saved. Used for both Whisper transcription and the mode LLM (llama-3.3-70b).'
              : 'One free key from console.groq.com powers both transcription and mode post-processing.'
          }
          ok={groqKey}
          cta={{label: 'Open settings', onPress: () => navigation.navigate('Settings')}}
        />

        <View style={styles.helpBlock}>
          <Text style={styles.helpTitle}>How it works</Text>
          <Text style={styles.helpStep}>1. Grant the three permissions above + add a Groq key.</Text>
          <Text style={styles.helpStep}>2. Tap "Start overlay" — nothing happens visually yet.</Text>
          <Text style={styles.helpStep}>
            3. Open any app, tap a text field — the pill slides in.
          </Text>
          <Text style={styles.helpStep}>
            4. Tap pill → speak → tap again. Mode (default/formal/generate) lives in the
            notification drawer.
          </Text>
          <Text style={styles.helpStep}>
            5. Hindi on a Hindi keyboard stays Devanagari; Hindi on an English keyboard comes out
            as Hinglish.
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
