const express = require('express');
const { v4: uuidv4 } = require('uuid');

const app = express();
const PORT = 3000;

// Twilio sends data in urlencoded format, not standard JSON
app.use(express.urlencoded({ extended: true }));
app.use(express.json());

// In-memory mock database to store auth sessions
const authSessions = {};

// ==========================================
// Endpoint 1: Called by the Android app to start the process
// ==========================================
app.get('/generate-hash', (req, res) => {
    const hashCode = uuidv4();

    authSessions[hashCode] = {
        status: 'pending',
        phoneNumber: null
    };

    console.log(`[Server] New session generated: ${hashCode}`);
    res.json({ hashCode: hashCode });
});

// ==========================================
// Endpoint 2: Called by Twilio Webhook when it receives an SMS
// ==========================================
app.post('/webhook-sms', (req, res) => {
    const senderNumber = req.body.From;
    const messageBody = req.body.Body;

    console.log(`[Twilio Webhook] Received message from ${senderNumber}: ${messageBody}`);

    // Extract the Hash Code from the incoming message
    // Assuming the Android app sends the text in this format: AuthRequest:abc-123
    const receivedHash = messageBody ? messageBody.replace("AuthRequest:", "").trim() : "";

    if (authSessions[receivedHash]) {
        authSessions[receivedHash].status = 'verified';
        authSessions[receivedHash].phoneNumber = senderNumber;
        console.log(`[Server] ✅ Successfully linked number ${senderNumber} to hash ${receivedHash}`);
    } else {
        console.log(`[Server] ❌ Invalid or expired hash code: ${receivedHash}`);
    }

    // Respond to Twilio with 200 OK to confirm receipt
    res.status(200).send('<Response></Response>');
});

// ==========================================
// Endpoint 3: Android app polls this to check authentication status
// ==========================================
app.get('/check-status/:hashCode', (req, res) => {
    const session = authSessions[req.params.hashCode];

    if (session) {
        res.json(session);
    } else {
        res.status(404).json({ error: 'Session not found' });
    }
});

app.listen(PORT, () => {
    console.log(`Server is running on port ${PORT}`);
});