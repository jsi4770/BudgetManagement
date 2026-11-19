package com.back.was.domain.ocr;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 테스트용 실제 이미지 바이트. AI 대체 경로는 이미지를 디코딩하므로 {@code "fake-jpeg".getBytes()} 는
 * 415 가 된다 — 그 경로를 지나는 테스트는 여기서 만든 이미지를 보내야 한다.
 */
public final class TestImages {

    private TestImages() {
    }

    /** 단색 JPEG. */
    public static byte[] jpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        fill(image, Color.WHITE, 0, 0, width, height);
        return write(image, "jpeg");
    }

    /** 왼쪽 위 빨강, 오른쪽 위 초록, 왼쪽 아래 파랑, 오른쪽 아래 노랑 — 회전·반전 방향을 구분할 수 있다. */
    public static byte[] quadrantsJpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int hw = width / 2;
        int hh = height / 2;
        fill(image, Color.RED, 0, 0, hw, hh);
        fill(image, Color.GREEN, hw, 0, width - hw, hh);
        fill(image, Color.BLUE, 0, hh, hw, height - hh);
        fill(image, Color.YELLOW, hw, hh, width - hw, height - hh);
        return write(image, "jpeg");
    }

    /** 완전히 투명한 PNG. */
    public static byte[] transparentPng(int width, int height) {
        return write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png");
    }

    /**
     * JPEG 의 APP0 뒤에 Orientation 하나만 든 APP1(Exif) 세그먼트를 끼워 넣는다.
     * 휴대폰 사진처럼 "픽셀은 그대로, 보여 줄 때 돌려라"인 파일을 흉내 낸다.
     */
    public static byte[] withExifOrientation(byte[] jpeg, int orientation, boolean littleEndian) {
        byte[] tiff = littleEndian
                ? new byte[]{'I', 'I', 42, 0, 8, 0, 0, 0,
                        1, 0,                                   // 항목 1개
                        0x12, 0x01, 3, 0, 1, 0, 0, 0,           // tag 0x0112, SHORT, count 1
                        (byte) orientation, 0, 0, 0,
                        0, 0, 0, 0}                             // 다음 IFD 없음
                : new byte[]{'M', 'M', 0, 42, 0, 0, 0, 8,
                        0, 1,
                        0x01, 0x12, 0, 3, 0, 0, 0, 1,
                        0, (byte) orientation, 0, 0,
                        0, 0, 0, 0};
        byte[] exifHeader = {'E', 'x', 'i', 'f', 0, 0};
        int length = 2 + exifHeader.length + tiff.length;

        // SOI(2) + APP0(마커 2 + 길이) 뒤에 넣는다 — ImageIO 는 APP0 가 SOI 바로 뒤에 오기를 기대한다
        int app0End = 4 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, app0End);
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(exifHeader);
        out.writeBytes(tiff);
        out.write(jpeg, app0End, jpeg.length - app0End);
        return out.toByteArray();
    }

    private static void fill(BufferedImage image, Color color, int x, int y, int w, int h) {
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(color);
            g.fillRect(x, y, w, h);
        } finally {
            g.dispose();
        }
    }

    private static byte[] write(BufferedImage image, String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
