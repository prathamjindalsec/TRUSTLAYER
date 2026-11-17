package com.trustlayer.analyzer.qr;

/**
 * Transient result of local image decoding. The payload is deliberately used only in memory by
 * {@link QrPayloadAnalyzer}; callers must persist its redacted analysis instead of this record.
 */
public record DecodedQrPayload(
        String payload,
        String imageFormat,
        int imageWidth,
        int imageHeight
) {
}
