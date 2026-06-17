import React, { useEffect, useState, useRef } from 'react';
import {
  SafeAreaView,
  Text,
  NativeModules,
  NativeEventEmitter,
  Button,
  View,
  StyleSheet,
  PermissionsAndroid,
  ScrollView,
  ToastAndroid,
} from 'react-native';
import { initLlama } from 'llama.rn';

const { OverlayManager } = NativeModules;
const overlayEmitter = new NativeEventEmitter(OverlayManager);

const App = () => {
  const [modelPath, setModelPath] = useState('/storage/emulated/0/Download/gemma.gguf');
  const [modelLoaded, setModelLoaded] = useState(false);
  const llamaContextRef = useRef<any>(null);
  const isInferencingRef = useRef<boolean>(false);
  const latestMessageRef = useRef<string>('');

  const addLog = (msg: string) => {
    console.log(`[AI Assistant] ${msg}`);
    // Show the log as a popup alert on the phone screen
    ToastAndroid.show(msg, ToastAndroid.SHORT);
  };

  useEffect(() => {
    addLog("🚀 App started. Requesting permissions...");
    const requestPermissions = async () => {
      try {
        // Request legacy storage permission for old devices
        await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.READ_EXTERNAL_STORAGE
        );
        // Request "All Files Access" for Android 11+
        OverlayManager.requestAllFilesAccess();
        addLog("✅ Storage permission requested.");
        // Try to show trigger button immediately if permission exists
        OverlayManager.showTriggerButton();
      } catch (err: any) {
        addLog(`❌ Permission error: ${err.message}`);
      }
    };
    requestPermissions();

    const setupLlama = async (path: string) => {
      try {
        addLog(`⏳ Initializing Llama context from: ${path}`);
        if (llamaContextRef.current) {
           await llamaContextRef.current.release();
        }
        const context = await initLlama({ 
            model: path,
            n_ctx: 2048 
        });
        llamaContextRef.current = context;
        setModelLoaded(true);
        addLog("✅ Llama context initialized perfectly!");
      } catch (e: any) {
        addLog(`❌ Failed to init Llama: ${e.message}`);
        setModelLoaded(false);
      }
    };
    setupLlama(modelPath);

    const generateReply = async (isRetry = false) => {
      if (isInferencingRef.current) {
        addLog(`⏳ Skipped: Llama is already busy thinking.`);
        return;
      }
      
      isInferencingRef.current = true;
      try {
        if (llamaContextRef.current) {
            addLog(isRetry ? "🧠 Regenerating alternative reply..." : "🧠 Running local AI inference on chat history...");
            
            const systemPrompt = `You are a secure, on-device AI assistant. Your sole purpose is to read the provided chat history and draft a natural, contextually appropriate reply for the user. Do NOT repeat or output your own rules. ONLY output the conversational reply.

INPUT FORMAT:
You will receive the recent chat history. You are drafting a reply on behalf of the device owner to the most recent message.`;

            // Strip common Discord UI buttons and layout garbage from the accessibility logs
            const cleanChatHistory = (text: string) => {
                const garbageList = [
                    "Back", "mentions", "Member List", "Start Voice Call", "Start Video Call", "Search",
                    "Message Attachments", "Toggle media keyboard", "Toggle apps keyboard", "Send a gift",
                    "Toggle emoji keyboard", "Record Voice Message", "Offline", "Online"
                ];
                return text.split('\n')
                    .filter(line => {
                        const t = line.trim();
                        if (garbageList.some(garbage => t.includes(garbage))) return false;
                        if (t.startsWith("Message @")) return false; // Ignore "Message @User" text box hint
                        if (/^\d+$/.test(t)) return false; // Ignore notification bubbles like "99"
                        return t.length > 0;
                    })
                    .join('\n');
            };

            const cleanedHistory = cleanChatHistory(latestMessageRef.current);

            // Take only the last 800 characters to drastically speed up CPU inference time
            const truncatedHistory = cleanedHistory.slice(-800);
            
            console.log("\n===== CHAT HISTORY FED TO AI =====");
            console.log(truncatedHistory);
            console.log("==================================\n");
            
            const retryInstruction = isRetry ? "The user rejected your previous draft. You MUST provide a completely different, alternative reply." : "Write a short, casual reply to the last message.";

            // Format precisely for Gemma
            const formattedPrompt = `<start_of_turn>user
${systemPrompt}

Chat History:
${truncatedHistory}

${retryInstruction}<end_of_turn>
<start_of_turn>model
`;

            const response = await llamaContextRef.current.completion({ 
                prompt: formattedPrompt, 
                n_predict: 100, // Reduced predict length to speed up generation
                temperature: isRetry ? 0.95 : 0.7, // Higher temp if retrying to force entirely different wording
                repeat_penalty: 1.18,
                stop: ["<end_of_turn>", "<|im_end|>", "</s>", "user", "model", "Chat History:"]
            });
            
            console.log("Raw Model Response:", response);
            
            let generatedText = response.text.replace(/<end_of_turn>|<\|im_end\|>|<\/s>|###/g, '').trim();
            
            if (!generatedText) {
                addLog("⚠️ Model returned empty text.");
            } else {
                addLog(`✅ Draft ready: "${generatedText}"`);
                OverlayManager.showOverlay(generatedText);
            }
        } else {
            addLog("⚠️ Ignored message: Llama context not ready yet.");
        }
      } catch (e: any) {
        addLog(`❌ Inference failed: ${e.message}`);
      } finally {
        isInferencingRef.current = false;
      }
    };

    const messageListener = overlayEmitter.addListener('onMessageDetected', async (event) => {
      if (!event.text || event.text.length < 3) return;
      // Silently update the latest context, but do NOT trigger generation
      latestMessageRef.current = event.text;
    });

    const triggerListener = overlayEmitter.addListener('onManualTriggerClicked', () => {
      addLog("🤖 Manual trigger activated!");
      generateReply(false);
    });

    const approveListener = overlayEmitter.addListener('onApproveClicked', (event) => {
      addLog(`📤 Approved reply. Injecting: "${event.text}"`);
      OverlayManager.injectText(event.text);
    });

    const rejectListener = overlayEmitter.addListener('onRejectClicked', () => {
      addLog(`❌ User rejected draft. Triggering regeneration...`);
      generateReply(true);
    });

    return () => {
      messageListener.remove();
      triggerListener.remove();
      approveListener.remove();
      rejectListener.remove();
    };
  }, []);

  const handlePickModel = async () => {
    try {
      const path = await OverlayManager.pickModelFile();
      if (path && path.length > 0 && !path.startsWith("content://")) {
        addLog(`📂 Selected model: ${path}`);
        setModelPath(path);
        
        addLog(`⏳ Initializing Llama context from: ${path}`);
        if (llamaContextRef.current) {
           await llamaContextRef.current.release();
        }
        const context = await initLlama({ 
            model: path,
            n_ctx: 2048 
        });
        llamaContextRef.current = context;
        setModelLoaded(true);
        addLog("✅ Llama context initialized perfectly!");
      } else {
        addLog(`❌ Invalid path: ${path}. Must be raw file path.`);
      }
    } catch (e: any) {
      addLog(`❌ Picker cancelled or failed: ${e.message}`);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.header}>
        <Text style={styles.title}>On-Device AI Assistant</Text>
        <Text style={styles.info}>Accessibility Service & Overlay Active</Text>
        
        <Text style={{marginVertical: 10, textAlign: 'center', fontSize: 12}}>
          Model: {modelPath}
        </Text>
        <Text style={{marginBottom: 20, color: modelLoaded ? 'green' : 'red', fontWeight: 'bold'}}>
          {modelLoaded ? '✅ Model Loaded & Ready' : '❌ Model Not Loaded'}
        </Text>
        
        <Button title="📂 Choose Model File (.gguf)" onPress={handlePickModel} color="#8a2be2" />
        <View style={{height: 20}} />
        <Button 
          title="Test Overlay Manually" 
          onPress={() => OverlayManager.showOverlay("This is a manual test message!")} 
        />
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f0f0f0', justifyContent: 'center' },
  header: { padding: 20, alignItems: 'center' },
  title: { fontSize: 24, fontWeight: 'bold', marginBottom: 15 },
  info: { fontSize: 16, marginBottom: 25, color: '#666' }
});

export default App;
