package al.pcmania.service;

import al.pcmania.domain.Enums.TradeMediaType;

import java.io.IOException;
import java.io.InputStream;

/**
 * Recognises trade-in proof by its first bytes rather than its name or the browser's content type:
 * pictures saved from the web are often WebP named .jpg, and anything can be renamed .mp4.
 */
public record TradeMedia(TradeMediaType type, String contentType, String extension) {

    /** A phone clip of FurMark at 720p fits; the database (500 MB on Supabase's free plan) does not take full-length 4K. */
    public static final long MAX_VIDEO_BYTES = 40L * 1024 * 1024;
    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    /** All trade-in media together, so a run of uploads cannot fill the database. */
    public static final long MAX_TOTAL_BYTES = 150L * 1024 * 1024;

    /** Null when the bytes are not MP4/MOV video or a JPEG/PNG/WebP image. */
    public static TradeMedia detect(InputStream in) throws IOException {
        byte[] h = in.readNBytes(12);
        if (h.length < 12) return null;
        if (h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') {
            boolean quicktime = h[8] == 'q' && h[9] == 't';
            return quicktime ? new TradeMedia(TradeMediaType.VIDEO, "video/quicktime", "mov")
                    : new TradeMedia(TradeMediaType.VIDEO, "video/mp4", "mp4");
        }
        if ((h[0] & 0xff) == 0xff && (h[1] & 0xff) == 0xd8 && (h[2] & 0xff) == 0xff) {
            return new TradeMedia(TradeMediaType.IMAGE, "image/jpeg", "jpg");
        }
        if ((h[0] & 0xff) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
            return new TradeMedia(TradeMediaType.IMAGE, "image/png", "png");
        }
        if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return new TradeMedia(TradeMediaType.IMAGE, "image/webp", "webp");
        }
        return null;
    }

    public long maxBytes() {
        return type == TradeMediaType.VIDEO ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
    }
}
