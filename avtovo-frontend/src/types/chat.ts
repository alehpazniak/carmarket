export interface Conversation {
    id: string;
    carId: string;
    buyerId: string;
    sellerId: string;
    lastMessageAt: string;
}

export interface Message {
    id: string;
    conversationId: string;
    senderId: string;
    content: string;
    createdAt: string;
    readAt: string | null;
}

// Payload sent over STOMP to /app/chat.send
export interface SendMessagePayload {
    carId: string;
    sellerId: string;
    content: string;
}

// Spring Data Page shape returned by the history endpoint
export interface Page<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}