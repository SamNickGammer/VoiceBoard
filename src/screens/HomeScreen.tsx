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
  hasApiKey,
  hasMicPermission,
  isKeyboardEnabled,
  openImeSettings,
  requestMicPermission,
  showImePicker,
} from '../utils/storage';
import type {RootStackParamList} from '../../App';

type Props = NativeStackScreenProps<RootStackParamList, 'Home'>;

export default function HomeScreen({navigation}: Props) {
  const [enabled, setEnabled] = useState(false);
  const [mic, setMic] = useState(false);
  const [apiKeySet, setApiKeySet] = useState(false);

  const refresh = useCallback(async () => {
    const [e, m, a] = await Promise.all([
      isKeyboardEnabled(),
      hasMicPermission(),
      hasApiKey(),
    ]);
    setEnabled(e);
    setMic(m);
    setApiKeySet(a);
  }, []);

  useEffect(() => {
    refresh();
    const sub = AppState.addEventListener('change', state => {
      if (state === 'active') refresh();
    });
    return () => sub.remove();
  }, [refresh]);

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>VoiceBoard</Text>
        <Text style={styles.subtitle}>
          AI voice keyboard. Speak; we transcribe and write.
        </Text>

        <StatusCard
          title="Keyboard enabled"
          subtitle={
            enabled
              ? 'VoiceBoard is in your input methods list.'
              : 'Enable VoiceBoard in Android settings, then switch to it from any text field.'
          }
          ok={enabled}
          cta={{label: 'Open keyboard settings', onPress: openImeSettings}}
        />

        <StatusCard
          title="Microphone permission"
          subtitle={
            mic
              ? 'Granted. Mic taps in the IME can record audio.'
              : 'Not granted. The IME cannot ask for this itself — grant it from this app.'
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
          title="Claude API key"
          subtitle={
            apiKeySet
              ? 'Saved. Speech runs through Claude with your selected mode.'
              : 'Not set. The IME will fall back to raw transcription text.'
          }
          ok={apiKeySet}
          cta={{label: 'Open settings', onPress: () => navigation.navigate('Settings')}}
        />

        <TouchableOpacity style={styles.pickerLink} onPress={showImePicker}>
          <Text style={styles.pickerLinkText}>Switch input method</Text>
        </TouchableOpacity>

        <View style={styles.helpBlock}>
          <Text style={styles.helpTitle}>How to try it</Text>
          <Text style={styles.helpStep}>1. Enable VoiceBoard in keyboard settings.</Text>
          <Text style={styles.helpStep}>2. Grant the mic permission above.</Text>
          <Text style={styles.helpStep}>
            3. Open any app with a text field, switch to VoiceBoard, tap the purple mic.
          </Text>
          <Text style={styles.helpStep}>
            4. Speak, tap mic again to stop — text gets injected into the field.
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
  subtitle: {fontSize: 14, color: '#6B7280', marginTop: 4, marginBottom: 20},
  pickerLink: {alignSelf: 'flex-start', paddingVertical: 8},
  pickerLinkText: {color: '#7F77DD', fontWeight: '600'},
  helpBlock: {
    marginTop: 16,
    padding: 16,
    backgroundColor: '#EEF2FF',
    borderRadius: 12,
  },
  helpTitle: {fontWeight: '700', color: '#3730A3', marginBottom: 8},
  helpStep: {color: '#3730A3', fontSize: 13, marginBottom: 4},
});
