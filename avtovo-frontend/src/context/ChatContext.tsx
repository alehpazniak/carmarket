import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import { useAuth } from './AuthContext';
import type { Message, SendMessagePayload } from '../types/chat';

// WebSocket goes directly to chat-service (not through the gateway).
const CHAT_WS_URL =
    import.meta.env.VITE_CHAT_WS_URL || 'ws://localhost:8085/ws';

type MessageHandler = (msg: Message) => void;

interface ChatContextType {
    connected: boolean;
    /** Registers a handler for every incoming message on the user's private queue; returns the unsubscribe. */
    subscribe: (handler: MessageHandler) => () => void;
    sendMessage: (payload: SendMessagePayload) => void;
}

const ChatContext = createContext<ChatContextType | null>(null);

/** One STOMP connection per logged-in user, shared by the navbar badge and every chat window. */
export function ChatProvider({ children }: { children: ReactNode }) {
    const { user } = useAuth();
    const userId = user?.id;
    const [connected, setConnected] = useState(false);
    const clientRef = useRef<Client | null>(null);
    const handlersRef = useRef(new Set<MessageHandler>());

    useEffect(() => {
        const token = localStorage.getItem('access_token');
        if (!userId || !token) return;

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
                    handlersRef.current.forEach((handler) => handler(msg));
                });
            },
            onDisconnect: () => setConnected(false),
            onWebSocketClose: () => setConnected(false),
            onStompError: (frame) => {
                console.error('STOMP error:', frame.headers['message'], frame.body);
            },
            onWebSocketError: (evt) => {
                console.error('WebSocket error:', evt);
            },
        });

        client.activate();
        clientRef.current = client;

        // Runs on logout (userId → undefined) and on unmount
        return () => {
            client.deactivate();
            clientRef.current = null;
            setConnected(false);
        };
    }, [userId]);

    const subscribe = useCallback((handler: MessageHandler) => {
        handlersRef.current.add(handler);
        return () => {
            handlersRef.current.delete(handler);
        };
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

    return (
        <ChatContext.Provider value={{ connected, subscribe, sendMessage }}>
            {children}
        </ChatContext.Provider>
    );
}

// eslint-disable-next-line react-refresh/only-export-components -- provider and its hook belong together
export const useChat = () => {
    const ctx = useContext(ChatContext);
    if (!ctx) throw new Error('useChat must be used within ChatProvider');
    return ctx;
};
