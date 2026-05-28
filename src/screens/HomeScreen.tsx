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

type Readiness = {
  overlay: boolean;
  a11y: boolean;
  mic: boolean;
  key: boolean;
};

export default function HomeScreen({navigation}: Props) {
  const [r, setR] = useState<Readiness>({overlay: false, a11y: false, mic: false, key: false});
  const [running, setRunning] = useState(false);

  const refresh = useCallback(async () => {
    const [overlay, a11y, mic, key, run] = await Promise.all([
      hasOverlayPermission(),
      isAccessibilityEnabled(),
      hasMicPermission(),
      hasGroqApiKey(),
      isOverlayRunning(),
    ]);
    setR({overlay, a11y, mic, key});
    setRunning(run);
  }, []);

  useEffect(() => {
    refresh();
    const sub = AppState.addEventListener('change', s => {
      if (s === 'active') refresh();
    });
    return () => sub.remove();
  }, [refresh]);

  const ready = r.overlay && r.a11y && r.mic && r.key;
  const readyCount = Number(r.overlay) + Number(r.a11y) + Number(r.mic) + Number(r.key);

  const toggle = async () => {
    if (running) await stopOverlay();
    else await startOverlay();
    setTimeout(refresh, 300);
  };

  return (
    <SafeAreaView style={s.safe}>
      <ScrollView contentContainerStyle={s.scroll}>
        {/* Hero */}
        <View style={s.hero}>
          <View style={s.logoCircle}>
            <Text style={s.logoMic}>🎙</Text>
          </View>
          <Text style={s.brand}>VoiceBoard</Text>
          <Text style={s.tagline}>
            A floating mic that lives over any keyboard.{'\n'}
            Speak, and your message lands in the field — in your script.
          </Text>
        </View>

        {/* Action card */}
        <View style={[s.actionCard, running && s.actionCardRunning]}>
          <View style={s.actionCardRow}>
            <View style={{flex: 1}}>
              <Text style={s.actionTitle}>
                {running ? 'Overlay is on' : ready ? 'Ready to go' : 'Almost there'}
              </Text>
              <Text style={s.actionSub}>
                {running
                  ? 'The pill appears when a keyboard opens. Tap it to dictate.'
                  : ready
                  ? 'Start the overlay and open any text field.'
                  : `Finish setup: ${readyCount}/4 ready.`}
              </Text>
            </View>
            <View style={[s.statusDot, {backgroundColor: running ? '#22C55E' : ready ? '#7F77DD' : '#F59E0B'}]} />
          </View>
          <TouchableOpacity
            style={[
              s.action,
              !ready && s.actionDisabled,
              running && s.actionStop,
            ]}
            onPress={toggle}
            disabled={!ready}>
            <Text style={s.actionText}>
              {running ? 'Stop overlay' : 'Start overlay'}
            </Text>
          </TouchableOpacity>
        </View>

        {/* Readiness grid */}
        <Text style={s.sectionLabel}>Setup</Text>
        <View style={s.grid}>
          <ReadyTile
            label="Overlay"
            sub="Display over other apps"
            ok={r.overlay}
            onFix={requestOverlayPermission}
          />
          <ReadyTile
            label="Accessibility"
            sub="Inject text into focused field"
            ok={r.a11y}
            onFix={openAccessibilitySettings}
          />
          <ReadyTile
            label="Microphone"
            sub="Record what you say"
            ok={r.mic}
            onFix={async () => {
              await requestMicPermission();
              refresh();
            }}
          />
          <ReadyTile
            label="Groq key"
            sub="Whisper + LLM"
            ok={r.key}
            onFix={() => navigation.navigate('Settings')}
          />
        </View>

        <TouchableOpacity style={s.linkRow} onPress={() => navigation.navigate('Settings')}>
          <Text style={s.linkText}>Open Settings →</Text>
        </TouchableOpacity>

        {/* Tips */}
        <View style={s.tips}>
          <Text style={s.tipsTitle}>Tips</Text>
          <Tip text="The pill only appears when a keyboard is open. Walk away and it disappears." />
          <Tip text="Pull down the notification shade to switch mode (Default / Formal / Generate)." />
          <Tip text="Hindi keyboard → output in Devanagari. English keyboard → Hindi speech becomes Hinglish." />
          <Tip text='Dictation is intent-aware: say "let&apos;s meet at 4 no no 5" and you get "Let&apos;s meet at 5."' />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function ReadyTile({
  label,
  sub,
  ok,
  onFix,
}: {
  label: string;
  sub: string;
  ok: boolean;
  onFix: () => void;
}) {
  return (
    <TouchableOpacity
      style={[s.tile, ok && s.tileOk]}
      onPress={ok ? undefined : onFix}
      activeOpacity={ok ? 1 : 0.6}>
      <View style={s.tileHeader}>
        <View style={[s.dot, {backgroundColor: ok ? '#22C55E' : '#F59E0B'}]} />
        <Text style={s.tileLabel}>{label}</Text>
      </View>
      <Text style={s.tileSub}>{sub}</Text>
      {!ok ? <Text style={s.tileFix}>Tap to fix</Text> : null}
    </TouchableOpacity>
  );
}

function Tip({text}: {text: string}) {
  return (
    <View style={s.tipRow}>
      <Text style={s.tipBullet}>•</Text>
      <Text style={s.tipText}>{text}</Text>
    </View>
  );
}

const s = StyleSheet.create({
  safe: {flex: 1, backgroundColor: '#F8F8FB'},
  scroll: {paddingBottom: 40},

  hero: {
    paddingTop: 32,
    paddingBottom: 28,
    paddingHorizontal: 24,
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderBottomLeftRadius: 28,
    borderBottomRightRadius: 28,
    marginBottom: 16,
    shadowColor: '#7F77DD',
    shadowOpacity: 0.08,
    shadowRadius: 16,
    shadowOffset: {width: 0, height: 4},
    elevation: 2,
  },
  logoCircle: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: '#7F77DD',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 12,
    shadowColor: '#7F77DD',
    shadowOpacity: 0.4,
    shadowRadius: 12,
    shadowOffset: {width: 0, height: 6},
    elevation: 8,
  },
  logoMic: {fontSize: 32},
  brand: {
    fontSize: 28,
    fontWeight: '800',
    color: '#111827',
    letterSpacing: -0.5,
  },
  tagline: {
    marginTop: 8,
    fontSize: 13,
    lineHeight: 19,
    color: '#6B7280',
    textAlign: 'center',
  },

  actionCard: {
    marginHorizontal: 20,
    padding: 18,
    backgroundColor: '#FFFFFF',
    borderRadius: 18,
    borderWidth: 1,
    borderColor: '#EEEFF5',
  },
  actionCardRunning: {borderColor: '#22C55E33', backgroundColor: '#F0FDF4'},
  actionCardRow: {flexDirection: 'row', alignItems: 'flex-start', marginBottom: 14},
  actionTitle: {fontSize: 16, fontWeight: '700', color: '#111827'},
  actionSub: {fontSize: 12, color: '#6B7280', marginTop: 4, lineHeight: 17},
  statusDot: {width: 10, height: 10, borderRadius: 5, marginLeft: 8, marginTop: 6},
  action: {
    backgroundColor: '#7F77DD',
    paddingVertical: 14,
    borderRadius: 12,
    alignItems: 'center',
  },
  actionDisabled: {opacity: 0.45},
  actionStop: {backgroundColor: '#EF4444'},
  actionText: {color: '#FFFFFF', fontWeight: '700', fontSize: 15},

  sectionLabel: {
    fontSize: 11,
    fontWeight: '700',
    color: '#9CA3AF',
    letterSpacing: 1.2,
    marginTop: 22,
    marginHorizontal: 20,
    marginBottom: 10,
    textTransform: 'uppercase',
  },

  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    paddingHorizontal: 14,
  },
  tile: {
    flexBasis: '47%',
    flexGrow: 1,
    margin: 6,
    padding: 14,
    backgroundColor: '#FFFFFF',
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#EEEFF5',
  },
  tileOk: {borderColor: '#22C55E33'},
  tileHeader: {flexDirection: 'row', alignItems: 'center', marginBottom: 4},
  dot: {width: 8, height: 8, borderRadius: 4, marginRight: 8},
  tileLabel: {fontWeight: '700', color: '#111827', fontSize: 14},
  tileSub: {fontSize: 11, color: '#6B7280', lineHeight: 15},
  tileFix: {marginTop: 6, fontSize: 11, color: '#7F77DD', fontWeight: '600'},

  linkRow: {alignItems: 'center', marginTop: 18, marginBottom: 8},
  linkText: {color: '#7F77DD', fontWeight: '700', fontSize: 14},

  tips: {
    marginTop: 14,
    marginHorizontal: 20,
    padding: 16,
    backgroundColor: '#EEF2FF',
    borderRadius: 14,
  },
  tipsTitle: {
    color: '#3730A3',
    fontWeight: '700',
    marginBottom: 8,
    fontSize: 13,
  },
  tipRow: {flexDirection: 'row', marginBottom: 6, alignItems: 'flex-start'},
  tipBullet: {color: '#3730A3', marginRight: 8, marginTop: 1, fontSize: 14, fontWeight: '700'},
  tipText: {color: '#3730A3', fontSize: 12, lineHeight: 17, flex: 1},
});
