import api from './client';
import type {Conversation, Message, Page} from '../types/chat';

/** All conversations where the current user is buyer or seller. */
export async function getConversations(): Promise<Conversation[]> {
    const res = await api.get('/api/chat/conversations');
    return res.data;
}

/** Paged message history for a conversation (oldest → newest). */
export async function getMessages(
    conversationId: string,
    page = 0,
    size = 50
): Promise<Page<Message>> {
    const res = await api.get(`/api/chat/conversations/${conversationId}/messages`, {
        params: {page, size},
    });
    return res.data;
}

export const UNREAD_CHANGED_EVENT = 'chat:unread-changed';

export async function getUnreadCount(): Promise<number> {
    const res = await api.get('/api/chat/unread-count');
    return res.data;
}

/** Marks the other party's messages in a conversation as read. */
export async function markConversationRead(conversationId: string): Promise<void> {
    await api.post(`/api/chat/conversations/${conversationId}/read`);
    window.dispatchEvent(new Event(UNREAD_CHANGED_EVENT));
}
