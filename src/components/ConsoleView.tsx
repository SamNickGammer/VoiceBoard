import React, {useEffect, useRef} from 'react';
import {ScrollView, StyleSheet, Text, View} from 'react-native';

export type ConsoleLine = {
  ts: number;
  level: 'step' | 'info' | 'progress' | 'done' | 'error';
  text: string;
};

export default function ConsoleView({lines}: {lines: ConsoleLine[]}) {
  const ref = useRef<ScrollView>(null);

  useEffect(() => {
    requestAnimationFrame(() => ref.current?.scrollToEnd({animated: true}));
  }, [lines]);

  return (
    <View style={styles.wrap}>
      <ScrollView ref={ref} style={styles.scroll}>
        {lines.length === 0 ? (
          <Text style={styles.placeholder}>
            $ waiting for download events...
          </Text>
        ) : (
          lines.map((l, i) => (
            <Text key={i} style={[styles.line, levelStyle(l.level)]}>
              {prefix(l.level)} {l.text}
            </Text>
          ))
        )}
      </ScrollView>
    </View>
  );
}

const prefix = (l: ConsoleLine['level']) =>
  l === 'step' ? '›'
  : l === 'info' ? 'i'
  : l === 'progress' ? '⋯'
  : l === 'done' ? '✓'
  : '✗';

const levelStyle = (l: ConsoleLine['level']) =>
  l === 'error'
    ? styles.error
    : l === 'done'
    ? styles.done
    : l === 'progress'
    ? styles.progress
    : styles.info;

const styles = StyleSheet.create({
  wrap: {
    backgroundColor: '#0F172A',
    borderRadius: 10,
    padding: 10,
    height: 160,
    marginTop: 10,
  },
  scroll: {flex: 1},
  placeholder: {color: '#94A3B8', fontFamily: 'monospace', fontSize: 12},
  line: {fontFamily: 'monospace', fontSize: 12, marginBottom: 2},
  info: {color: '#E2E8F0'},
  progress: {color: '#A5B4FC'},
  done: {color: '#86EFAC'},
  error: {color: '#FCA5A5'},
});
