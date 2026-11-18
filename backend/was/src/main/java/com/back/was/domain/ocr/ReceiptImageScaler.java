package com.back.was.domain.ocr;

import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * AI 가 이미지를 직접 읽는 대체 경로({@link GeminiReceiptOcrClient})에 넘기기 전에 영수증을 줄인다.
 * Django 경로는 원본을 그대로 넘기므로 여기를 거치지 않는다.
 *
 * <p>줄이는 이유: 업로드 상한은 10MB(RCPT-01)인데 Claude API 는 이미지 한 장에 5MB 까지 받고, 긴 변이
 * {@value #MAX_LONG_EDGE}px 를 넘으면 어차피 안에서 줄여 읽는다. 큰 원본은 인식률은 그대로인 채 업로드
 * 시간만 늘려 이미지 호출 상한(30초)을 갉아먹는다.
 *
 * <ul>
 *   <li><b>읽을 수 없는 파일은 415 {@code UNSUPPORTED_FILE_TYPE}</b> — 형식 검사(content type)는 통과했지만
 *       실제로는 깨진 파일인 경우. 빈 파일을 415 로 막는 것과 같은 판단이다.</li>
 *   <li>EXIF 회전값을 픽셀에 적용한다. 다시 인코딩하면 메타데이터가 사라져 휴대폰 사진이 옆으로 누운 채
 *       넘어가기 때문이다.</li>
 *   <li>투명 배경은 흰색으로 깐다 — JPEG 에는 알파가 없어 그대로 두면 검게 나온다.</li>
 *   <li>이미 작고 회전값도 없으면 다시 인코딩하지 않고 원본을 돌려준다.</li>
 * </ul>
 *
 * <p>디코딩은 정수 배율로 건너뛰며 읽는다(subsampling). 10MB 안에 들어가는 고해상도 사진이나 크기만 부풀린
 * PNG 를 원본 해상도로 펼치면 수백 MB 의 비트맵이 되기 때문이다. 메모리에서만 다루고 저장하지 않는다(SNFR-008).
 */
final class ReceiptImageScaler {

    /** Claude 가 줄이지 않고 읽는 긴 변의 상한. */
    static final int MAX_LONG_EDGE = 1568;

    private static final float JPEG_QUALITY = 0.85f;

    private ReceiptImageScaler() {
    }

    static ReceiptImage fitForAi(ReceiptImage image) {
        int orientation = ExifOrientation.read(image.bytes());
        Decoded decoded = decode(image.bytes());

        // 판정은 원본 크기로 한다 — 건너뛰며 읽은 결과는 원본이 1568 의 정수배일 때 정확히 1568 이 된다
        if (orientation == 1 && decoded.sourceLongEdge() <= MAX_LONG_EDGE) {
            return image;
        }

        BufferedImage fitted = orient(scaleOntoWhite(decoded.image()), orientation);
        return new ReceiptImage(encodeJpeg(fitted), "image/jpeg");
    }

    private record Decoded(BufferedImage image, int sourceLongEdge) {
    }

    /** 긴 변이 {@link #MAX_LONG_EDGE} 이상으로 남는 가장 큰 정수 배율로 건너뛰며 읽는다. */
    private static Decoded decode(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw unreadable();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int longEdge = Math.max(reader.getWidth(0), reader.getHeight(0));
                int step = Math.max(1, longEdge / MAX_LONG_EDGE);

                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                return new Decoded(reader.read(0, param), longEdge);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            // ImageIO 는 깨진 입력에 IOException 말고도 IllegalArgumentException 등을 던진다
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw unreadable();
        }
    }

    private static BusinessException unreadable() {
        return new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    /** 긴 변을 {@link #MAX_LONG_EDGE} 이하로 줄이면서 흰 바탕의 RGB 이미지로 옮긴다. */
    private static BufferedImage scaleOntoWhite(BufferedImage src) {
        int longEdge = Math.max(src.getWidth(), src.getHeight());
        double ratio = Math.min(1.0, (double) MAX_LONG_EDGE / longEdge);
        int width = Math.max(1, (int) Math.round(src.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(src.getHeight() * ratio));

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(src, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * EXIF 회전값(1~8)대로 픽셀을 옮긴다. 원본 크기 w×h, 점 (x, y) 기준:
     * 2 좌우 반전, 3 180°, 4 상하 반전, 5 전치 (y, x), 6 시계 90°, 7 반전치, 8 반시계 90°.
     */
    private static BufferedImage orient(BufferedImage src, int orientation) {
        if (orientation == 1) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swaps = orientation >= 5;

        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.translate(0, h); t.scale(1, -1); }
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, w); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            default -> { return src; }
        }

        BufferedImage out = new BufferedImage(swaps ? h : w, swaps ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(src, t, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            // 메모리 스트림이라 실제로는 일어나지 않는다. 일어나면 서버 문제이므로 500 으로 둔다
            throw new IllegalStateException("영수증 이미지 JPEG 인코딩 실패", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    /**
     * JPEG 의 APP1(Exif) 세그먼트에서 IFD0 의 Orientation(0x0112)만 읽는다. ImageIO 는 이 값을 노출하지 않는다.
     * 값이 없거나 구조가 이상하면 1(회전 없음) — 회전값은 보정용이라 이것 때문에 요청을 실패시키지 않는다.
     */
    static final class ExifOrientation {

        private static final int TAG_ORIENTATION = 0x0112;
        private static final int TYPE_SHORT = 3;

        private ExifOrientation() {
        }

        static int read(byte[] b) {
            if (b.length < 4 || u8(b, 0) != 0xFF || u8(b, 1) != 0xD8) {
                return 1;   // JPEG 가 아니다 (PNG 등)
            }
            int i = 2;
            while (i + 4 <= b.length) {
                if (u8(b, i) != 0xFF) {
                    return 1;
                }
                int marker = u8(b, i + 1);
                if (marker == 0xFF) {   // 채움 바이트
                    i++;
                    continue;
                }
                if (marker == 0xDA || marker == 0xD9) {   // 영상 데이터 시작·끝 — 그 뒤엔 메타데이터가 없다
                    return 1;
                }
                int length = (u8(b, i + 2) << 8) | u8(b, i + 3);
                if (length < 2) {
                    return 1;
                }
                if (marker == 0xE1 && isExif(b, i + 4, length - 2)) {
                    return orientation(b, i + 10, i + 2 + length);
                }
                i += 2 + length;
            }
            return 1;
        }

        private static boolean isExif(byte[] b, int start, int length) {
            byte[] header = {'E', 'x', 'i', 'f', 0, 0};
            if (length < header.length || start + header.length > b.length) {
                return false;
            }
            for (int k = 0; k < header.length; k++) {
                if (b[start + k] != header[k]) {
                    return false;
                }
            }
            return true;
        }

        /** {@code tiff} 는 TIFF 헤더 시작, {@code end} 는 세그먼트 끝(배타). 오프셋은 모두 TIFF 헤더 기준이다. */
        private static int orientation(byte[] b, int tiff, int end) {
            end = Math.min(end, b.length);
            if (tiff + 8 > end) {
                return 1;
            }
            boolean little;
            if (u8(b, tiff) == 'I' && u8(b, tiff + 1) == 'I') {
                little = true;
            } else if (u8(b, tiff) == 'M' && u8(b, tiff + 1) == 'M') {
                little = false;
            } else {
                return 1;
            }
            if (u16(b, tiff + 2, little) != 42) {
                return 1;
            }
            long ifd = tiff + u32(b, tiff + 4, little);
            if (ifd + 2 > end) {
                return 1;
            }
            int count = u16(b, (int) ifd, little);
            for (int n = 0; n < count; n++) {
                long entry = ifd + 2 + 12L * n;
                if (entry + 12 > end) {
                    return 1;
                }
                int e = (int) entry;
                if (u16(b, e, little) == TAG_ORIENTATION) {
                    if (u16(b, e + 2, little) != TYPE_SHORT) {
                        return 1;
                    }
                    int value = u16(b, e + 8, little);
                    return value >= 1 && value <= 8 ? value : 1;
                }
            }
            return 1;
        }

        private static int u8(byte[] b, int i) {
            return b[i] & 0xFF;
        }

        private static int u16(byte[] b, int i, boolean little) {
            return little ? u8(b, i) | (u8(b, i + 1) << 8) : (u8(b, i) << 8) | u8(b, i + 1);
        }

        private static long u32(byte[] b, int i, boolean little) {
            return little
                    ? (u16(b, i, true) & 0xFFFFL) | ((u16(b, i + 2, true) & 0xFFFFL) << 16)
                    : ((u16(b, i, false) & 0xFFFFL) << 16) | (u16(b, i + 2, false) & 0xFFFFL);
        }
    }
}
