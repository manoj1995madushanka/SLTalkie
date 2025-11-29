import React, { useEffect, useState } from 'react';
import {
  SafeAreaView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  PermissionsAndroid,
  Platform,
  Alert,
  NativeModules,
  StatusBar,
  DeviceEventEmitter,
  FlatList,
  Linking,
} from 'react-native';

interface Message {
  id: string;
  endpointId: string;
  latitude: number;
  longitude: number;
  filePath: string;
  timestamp: number;
}

const { FloodCommsModule } = NativeModules;

const App = () => {
  const [isRecording, setIsRecording] = useState(false);
  const [status, setStatus] = useState('Initializing...');
  const [messages, setMessages] = useState<Message[]>([]);

  useEffect(() => {
    requestPermissions();

    const subscription = DeviceEventEmitter.addListener('onLocationReceived', (event) => {
      const newMessage: Message = {
        id: Date.now().toString(),
        endpointId: event.endpointId,
        latitude: event.latitude,
        longitude: event.longitude,
        filePath: event.filePath,
        timestamp: Date.now(),
      };
      setMessages(prev => [newMessage, ...prev]);
    });
    return () => subscription.remove();
  }, []);

  const requestPermissions = async () => {
    if (Platform.OS === 'android') {
      try {
        const granted = await PermissionsAndroid.requestMultiple([
          PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
          PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_ADVERTISE,
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
          PermissionsAndroid.PERMISSIONS.NEARBY_WIFI_DEVICES,
        ]);

        const allGranted = Object.values(granted).every(
          (result) => result === PermissionsAndroid.RESULTS.GRANTED
        );

        if (allGranted) {
          startService();
        } else {
          Alert.alert('Permissions Required', 'Please grant all permissions to use this app.');
          setStatus('Permissions missing');
        }
      } catch (err) {
        console.warn(err);
      }
    }
  };

  const startService = async () => {
    try {
      setStatus('Starting P2P...');
      const nickName = "User-" + Math.floor(Math.random() * 1000);
      await FloodCommsModule.startAdvertising(nickName);
      await FloodCommsModule.startDiscovery();
      setStatus('Ready (Broadcasting)');
    } catch (e) {
      console.error(e);
      setStatus('Error starting service');
    }
  };

  const handlePressIn = () => {
    setIsRecording(true);
    FloodCommsModule.startRecording();
  };

  const handlePressOut = () => {
    setIsRecording(false);
    FloodCommsModule.stopRecording();
  };

  const playMessage = async (filePath: string) => {
    try {
      await FloodCommsModule.playAudioFile(filePath);
    } catch (e) {
      Alert.alert("Error", "Could not play audio file");
    }
  };

  const openMaps = (lat: number, lon: number) => {
    const url = `https://www.google.com/maps/search/?api=1&query=${lat},${lon}`;
    Linking.openURL(url).catch(err => console.error('An error occurred', err));
  };

  const renderMessage = ({ item }: { item: Message }) => (
    <View style={styles.messageCard}>
      <View style={styles.messageHeader}>
        <Text style={styles.senderText}>{item.endpointId}</Text>
        <Text style={styles.timeText}>{new Date(item.timestamp).toLocaleTimeString()}</Text>
      </View>

      <View style={styles.actionButtons}>
        <TouchableOpacity
          style={styles.actionButton}
          onPress={() => playMessage(item.filePath)}
        >
          <Text style={styles.actionButtonText}>▶ Play Audio</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.actionButton, styles.mapButton]}
          onPress={() => openMaps(item.latitude, item.longitude)}
        >
          <Text style={styles.actionButtonText}>📍 Map</Text>
        </TouchableOpacity>
      </View>
    </View>
  );

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#1a1a1a" />

      <View style={styles.header}>
        <Text style={styles.title}>FloodComms</Text>
        <Text style={styles.status}>{status}</Text>
      </View>

      <View style={styles.content}>
        <View style={styles.circleContainer}>
          <TouchableOpacity
            activeOpacity={0.7}
            onPressIn={handlePressIn}
            onPressOut={handlePressOut}
            style={[
              styles.pttButton,
              isRecording ? styles.pttButtonActive : styles.pttButtonInactive
            ]}
          >
            <Text style={styles.pttText}>
              {isRecording ? 'TALKING' : 'HOLD TO TALK'}
            </Text>
          </TouchableOpacity>
        </View>

        <Text style={styles.instruction}>
          {isRecording ? 'Release to send' : 'Press and hold to broadcast'}
        </Text>

        <FlatList
          data={messages}
          renderItem={renderMessage}
          keyExtractor={item => item.id}
          style={styles.list}
          contentContainerStyle={styles.listContent}
          ListEmptyComponent={
            <Text style={styles.emptyText}>No messages received yet</Text>
          }
        />
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#121212',
  },
  header: {
    padding: 20,
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: '#333',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#ffffff',
  },
  status: {
    marginTop: 5,
    fontSize: 14,
    color: '#aaaaaa',
  },
  content: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  circleContainer: {
    width: 250,
    height: 250,
    borderRadius: 125,
    borderWidth: 2,
    borderColor: '#333',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 30,
  },
  pttButton: {
    width: 220,
    height: 220,
    borderRadius: 110,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 10,
  },
  pttButtonInactive: {
    backgroundColor: '#007AFF',
  },
  pttButtonActive: {
    backgroundColor: '#FF3B30',
  },
  pttText: {
    color: '#ffffff',
    fontSize: 24,
    fontWeight: 'bold',
  },
  instruction: {
    color: '#888888',
    fontSize: 16,
    marginBottom: 20,
  },
  list: {
    width: '100%',
    flex: 1,
  },
  listContent: {
    paddingHorizontal: 20,
    paddingBottom: 20,
  },
  emptyText: {
    color: '#555',
    textAlign: 'center',
    marginTop: 20,
  },
  messageCard: {
    backgroundColor: '#222',
    borderRadius: 12,
    padding: 15,
    marginBottom: 10,
  },
  messageHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 10,
  },
  senderText: {
    color: '#fff',
    fontWeight: 'bold',
    fontSize: 16,
  },
  timeText: {
    color: '#888',
    fontSize: 12,
  },
  actionButtons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  actionButton: {
    backgroundColor: '#333',
    paddingVertical: 8,
    paddingHorizontal: 15,
    borderRadius: 8,
    flex: 0.48,
    alignItems: 'center',
  },
  mapButton: {
    backgroundColor: '#004400',
  },
  actionButtonText: {
    color: '#fff',
    fontWeight: '600',
  },
});

export default App;
