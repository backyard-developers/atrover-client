const express = require('express');
const { WebSocketServer } = require('ws');
const http = require('http');
const path = require('path');

const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ server });

// Serve static files
app.use(express.static(path.join(__dirname, 'public')));

// Store connected clients
const clients = new Map();

wss.on('connection', (ws) => {
    const clientId = Date.now().toString();
    clients.set(clientId, { ws, type: null });

    console.log(`Client connected: ${clientId}`);

    // Send client ID
    ws.send(JSON.stringify({ type: 'id', id: clientId }));

    ws.on('message', (data) => {
        try {
            const message = JSON.parse(data.toString());
            console.log(`Message from ${clientId}:`, message.type);

            switch (message.type) {
                case 'register':
                    // Register client type (android or web)
                    clients.get(clientId).type = message.clientType;
                    console.log(`Client ${clientId} registered as ${message.clientType}`);

                    // Notify others about new client
                    broadcastClientList();
                    break;

                case 'offer':
                case 'answer':
                case 'ice-candidate':
                    // Forward WebRTC signaling to target
                    if (message.target) {
                        const target = clients.get(message.target);
                        if (target && target.ws.readyState === 1) {
                            target.ws.send(JSON.stringify({
                                ...message,
                                from: clientId
                            }));
                        }
                    } else {
                        // Broadcast to all other clients
                        clients.forEach((client, id) => {
                            if (id !== clientId && client.ws.readyState === 1) {
                                client.ws.send(JSON.stringify({
                                    ...message,
                                    from: clientId
                                }));
                            }
                        });
                    }
                    break;

                default:
                    console.log('Unknown message type:', message.type);
            }
        } catch (e) {
            console.error('Error parsing message:', e);
        }
    });

    ws.on('close', () => {
        console.log(`Client disconnected: ${clientId}`);
        clients.delete(clientId);
        broadcastClientList();
    });

    ws.on('error', (error) => {
        console.error(`WebSocket error for ${clientId}:`, error);
    });
});

function broadcastClientList() {
    const clientList = [];
    clients.forEach((client, id) => {
        clientList.push({ id, type: client.type });
    });

    const message = JSON.stringify({ type: 'client-list', clients: clientList });
    clients.forEach((client) => {
        if (client.ws.readyState === 1) {
            client.ws.send(message);
        }
    });
}

const PORT = process.env.PORT || 8080;
server.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running on http://0.0.0.0:${PORT}`);
    console.log(`WebSocket server ready`);
});
