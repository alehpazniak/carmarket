import { useCallback, useEffect, useRef, useState } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import type { Message, SendMessagePayload } from '../types/chat';

// WebSocket goes directly to chat-service (not through the gateway).
const CHAT_WS_URL =
    import.meta.env.VITE_CHAT_WS_URL || 'ws://localhost:8085/ws';

interface UseChatResult {
    connected: boolean;
    /** Fires for every incoming message on the user's private queue. */
    onMessage: (handler: (msg: Message) => void) => void;
    sendMessage: (payload: SendMessagePayload) => void;
}

export function useChat(): UseChatResult {
    const [connected, setConnected] = useState(false);
    const clientRef = useRef<Client | null>(null);
    const handlerRef = useRef<((msg: Message) => void) | null>(null);

    useEffect(() => {
        const token = localStorage.getItem('access_token');
        if (!token) return;

        // Token is passed as a query param on the handshake URL — matches
        // the backend JwtHandshakeInterceptor which reads ?token=...
        const client = new Client({
            brokerURL: `${CHAT_WS_URL}?token=${token}`,
            reconnectDelay: 5000,          // auto-reconnect every 5s if dropped
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,
            onConnect: () => {
            setConnected(true);
            // Subscribe to our private queue. Spring routes
            // convertAndSendToUser(userId, "/queue/messages", ...) here.
            client.subscribe('/user/queue/messages', (frame: IMessage) => {
                const msg: Message = JSON.parse(frame.body);
                handlerRef.current?.(msg);
            });
        },
            onDisconnect: () => setConnected(false),
            onStompError: (frame) => {
            console.error('STOMP error:', frame.headers['message'], frame.body);
        },
            onWebSocketError: (evt) => {
            console.error('WebSocket error:', evt);
        },
    });

        client.activate();
        clientRef.current = client;

        return () => {
            client.deactivate();
            clientRef.current = null;
            setConnected(false);
        };
    }, []);

    const onMessage = useCallback((handler: (msg: Message) => void) => {
        handlerRef.current = handler;
    }, []);

    const sendMessage = useCallback((payload: SendMessagePayload) => {
        const client = clientRef.current;
        if (!client || !client.connected) {
            console.warn('Cannot send — WebSocket not connected');
            return;
        }
        client.publish({
            destination: '/app/chat.send',
            body: JSON.stringify(payload),
        });
    }, []);

    return { connected, onMessage, sendMessage };
}