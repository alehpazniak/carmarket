package com.carmarket.notification.entity;

public enum NotificationStatus {
    /** Waiting for send_at. */
    PENDING,
    /** Email handed to the SMTP server. */
    SENT,
    /** Recipient acted (e.g. read the chat) before send_at, so no email was needed. */
    CANCELLED,
    /** Was SENT, and the recipient has since acted; a new notification may be created. */
    ACKNOWLEDGED,
    /** Gave up after max attempts, or no email address known for the recipient. */
    FAILED
}
