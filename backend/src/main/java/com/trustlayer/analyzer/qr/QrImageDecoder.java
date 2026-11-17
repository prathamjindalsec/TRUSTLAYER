package com.trustlayer.analyzer.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.trustlayer.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Strict local-only QR image decoder. It accepts only PNG and JPEG byte signatures, limits
 * dimensions before pixel decoding, and never writes the upload to disk or uses its filename or
 * claimed content type as a security decision.
 */
@Component
public class QrImageDecoder {

    private static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 4_096;
    private static final long MAX_IMAGE_PIXELS = 12_000_000L;
    private static final int MAX_PAYLOAD_CHARACTERS = 4_096;

    public DecodedQrPayload decode(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QR_IMAGE_REQUIRED", "A non-empty QR image is required.");
        }
        if (upload.getSize() > MAX_UPLOAD_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "QR_IMAGE_TOO_LARGE",
                    "QR images must not exceed 5 MB.");
        }

        byte[] bytes;
        try {
            bytes = upload.getBytes();
        } catch (IOException exception) {
            throw invalidImage();
        }
        if (bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QR_IMAGE_REQUIRED", "A non-empty QR image is required.");
        }
        if (bytes.length > MAX_UPLOAD_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "QR_IMAGE_TOO_LARGE",
                    "QR images must not exceed 5 MB.");
        }

        String expectedFormat = detectedFormat(bytes);
        DecodedImage decodedImage = readValidatedImage(bytes, expectedFormat);
        String payload = decodeQrPayload(decodedImage.image());
        if (payload == null || payload.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "QR_CODE_NOT_FOUND",
                    "No readable QR code was found in the image.");
        }
        if (payload.length() > MAX_PAYLOAD_CHARACTERS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "QR_PAYLOAD_TOO_LARGE",
                    "The decoded QR payload exceeds the supported safety limit.");
        }

        return new DecodedQrPayload(payload, expectedFormat, decodedImage.width(), decodedImage.height());
    }

    private String detectedFormat(byte[] bytes) {
        if (isPng(bytes)) {
            return "PNG";
        }
        if (isJpeg(bytes)) {
            return "JPEG";
        }
        throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_QR_IMAGE",
                "Only PNG and JPEG QR images are supported.");
    }

    private DecodedImage readValidatedImage(byte[] bytes, String expectedFormat) {
        try (ImageInputStream imageStream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (imageStream == null) {
                throw invalidImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
            if (!readers.hasNext()) {
                throw invalidImage();
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageStream, true, true);
                if (!matchesExpectedFormat(reader.getFormatName(), expectedFormat)) {
                    throw invalidImage();
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                        || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "QR_IMAGE_DIMENSIONS_UNSUPPORTED",
                            "QR image dimensions exceed the supported safety limit.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalidImage();
                }
                return new DecodedImage(image, width, height);
            } finally {
                reader.dispose();
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalidImage();
        }
    }

    private String decodeQrPayload(BufferedImage image) {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        try {
            Result result = new MultiFormatReader().decode(
                    new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))),
                    hints
            );
            return result.getText();
        } catch (ReaderException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "QR_CODE_NOT_FOUND",
                    "No readable QR code was found in the image.");
        }
    }

    private boolean matchesExpectedFormat(String actualFormat, String expectedFormat) {
        return "PNG".equals(expectedFormat)
                ? "png".equalsIgnoreCase(actualFormat)
                : "jpeg".equalsIgnoreCase(actualFormat) || "jpg".equalsIgnoreCase(actualFormat);
    }

    private boolean isPng(byte[] bytes) {
        return bytes.length >= 8
                && bytes[0] == (byte) 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a;
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && bytes[0] == (byte) 0xff
                && bytes[1] == (byte) 0xd8
                && bytes[2] == (byte) 0xff;
    }

    private ApiException invalidImage() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_QR_IMAGE",
                "The uploaded file is not a valid supported QR image.");
    }

    private record DecodedImage(BufferedImage image, int width, int height) {
    }
}
