import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.fitwave.personal',
  appName: 'FitWave 奶霜丝带',
  webDir: 'dist',
  android: { backgroundColor: '#fff8fb' },
  ios: { contentInset: 'automatic' },
};

export default config;
