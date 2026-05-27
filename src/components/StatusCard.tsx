import React from 'react';
import {StyleSheet, Text, TouchableOpacity, View} from 'react-native';

type Props = {
  title: string;
  subtitle: string;
  ok: boolean;
  cta?: {label: string; onPress: () => void};
};

export default function StatusCard({title, subtitle, ok, cta}: Props) {
  return (
    <View style={styles.card}>
      <View style={styles.row}>
        <View style={[styles.dot, {backgroundColor: ok ? '#22C55E' : '#F59E0B'}]} />
        <Text style={styles.title}>{title}</Text>
      </View>
      <Text style={styles.subtitle}>{subtitle}</Text>
      {cta ? (
        <TouchableOpacity style={styles.button} onPress={cta.onPress}>
          <Text style={styles.buttonText}>{cta.label}</Text>
        </TouchableOpacity>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    padding: 16,
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#E5E7EB',
  },
  row: {flexDirection: 'row', alignItems: 'center', marginBottom: 6},
  dot: {width: 10, height: 10, borderRadius: 5, marginRight: 8},
  title: {fontSize: 16, fontWeight: '600', color: '#111827'},
  subtitle: {fontSize: 13, color: '#6B7280', lineHeight: 18},
  button: {
    marginTop: 12,
    paddingVertical: 10,
    paddingHorizontal: 14,
    backgroundColor: '#7F77DD',
    borderRadius: 8,
    alignSelf: 'flex-start',
  },
  buttonText: {color: '#FFFFFF', fontWeight: '600'},
});
