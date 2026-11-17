package com.trustlayer.analyzer.qr;

/**
 * The safe, high-level destination class extracted from a QR code. It describes what was
 * encoded; it never causes the backend to navigate, call, message, or pay anything.
 */
public enum QrPayloadType {
    URL,
    UPI_PAYMENT,
    PAYMENT_URI,
    ACTION_URI,
    TEXT
}
