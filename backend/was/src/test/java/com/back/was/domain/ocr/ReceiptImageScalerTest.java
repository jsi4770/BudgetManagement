package com.back.was.domain.ocr;

import com.back.was.global.exception.BusinessException;
import com.back.was.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptImageScalerTest {

    @Test
    @DisplayName("작고 회전값도 없으면 다시 인코딩하지 않고 원본 그대로")
    void smallImagePassesThrough() {
        ReceiptImage image = new ReceiptImage(TestImages.jpeg(800, 600), "image/jpeg");

        assertThat(ReceiptImageScaler.fitForAi(image)).isSameAs(image);
    }

    @Test
    @DisplayName("작은 PNG 도 원본 그대로 (타입도 png 유지)")
    void smallPngPassesThrough() {
        ReceiptImage image = new ReceiptImage(TestImages.transparentPng(100, 100), "image/png");

        assertThat(ReceiptImageScaler.fitForAi(image)).isSameAs(image);
    }

    @Test
    @DisplayName("긴 변이 1568px 를 넘으면 비율을 지켜 1568px 로 줄인 JPEG")
    void largeImageIsScaledDown() throws IOException {
        ReceiptImage image = new ReceiptImage(TestImages.jpeg(4000, 3000), "image/jpeg");

        ReceiptImage fitted = ReceiptImageScaler.fitForAi(image);

        BufferedImage out = decode(fitted);
        assertThat(fitted.contentType()).isEqualTo("image/jpeg");
        assertThat(out.getWidth()).isEqualTo(1568);
        assertThat(out.getHeight()).isEqualTo(1176);
    }

    @Test
    @DisplayName("원본이 1568 의 정수배여도 줄인다 (건너뛰며 읽은 크기가 아니라 원본 크기로 판정)")
    void exactMultipleIsStillReencoded() throws IOException {
        ReceiptImage image = new ReceiptImage(TestImages.jpeg(3136, 2000), "image/jpeg");

        ReceiptImage fitted = ReceiptImageScaler.fitForAi(image);

        assertThat(fitted).isNotSameAs(image);
        assertThat(decode(fitted).getWidth()).isEqualTo(1568);
    }

    @Test
    @DisplayName("세로로 긴 이미지도 긴 변(높이) 기준으로 줄인다")
    void tallImageIsScaledByHeight() throws IOException {
        BufferedImage out = decode(ReceiptImageScaler.fitForAi(
                new ReceiptImage(TestImages.jpeg(1000, 5000), "image/jpeg")));

        assertThat(out.getHeight()).isEqualTo(1568);
        assertThat(out.getWidth()).isEqualTo(314);
    }

    @Test
    @DisplayName("큰 PNG 의 투명 배경은 검정이 아니라 흰색 JPEG 가 된다")
    void transparentBecomesWhite() throws IOException {
        ReceiptImage fitted = ReceiptImageScaler.fitForAi(
                new ReceiptImage(TestImages.transparentPng(2000, 2000), "image/png"));

        assertThat(fitted.contentType()).isEqualTo("image/jpeg");
        int rgb = decode(fitted).getRGB(100, 100);
        assertThat(channel(rgb, 16)).isGreaterThan(240);
        assertThat(channel(rgb, 8)).isGreaterThan(240);
        assertThat(channel(rgb, 0)).isGreaterThan(240);
    }

    /**
     * 40×20 사분면 이미지(왼쪽 위 빨강)에 회전값을 달았을 때, 빨강이 가야 할 사분면.
     * EXIF 정의대로 계산한 값이다 — 원본 점 (x, y), 크기 w×h 에 대해
     * 2 (w−x, y) · 3 (w−x, h−y) · 4 (x, h−y) · 5 (y, x) · 6 (h−y, x) · 7 (h−y, w−x) · 8 (y, w−x).
     */
    @ParameterizedTest(name = "orientation {0} → {1}×{2}, 빨강은 {3}")
    @CsvSource({
            "2, 40, 20, TOP_RIGHT",
            "3, 40, 20, BOTTOM_RIGHT",
            "4, 40, 20, BOTTOM_LEFT",
            "5, 20, 40, TOP_LEFT",
            "6, 20, 40, TOP_RIGHT",
            "7, 20, 40, BOTTOM_RIGHT",
            "8, 20, 40, BOTTOM_LEFT",
    })
    @DisplayName("EXIF 회전값을 픽셀에 적용한다 (다시 인코딩하면 메타데이터가 사라지므로)")
    void appliesExifOrientation(int orientation, int width, int height, Quadrant red) throws IOException {
        byte[] bytes = TestImages.withExifOrientation(TestImages.quadrantsJpeg(40, 20), orientation, false);

        ReceiptImage fitted = ReceiptImageScaler.fitForAi(new ReceiptImage(bytes, "image/jpeg"));

        BufferedImage out = decode(fitted);
        assertThat(out.getWidth()).isEqualTo(width);
        assertThat(out.getHeight()).isEqualTo(height);
        assertThat(isRed(red.sample(out))).isTrue();
    }

    @Test
    @DisplayName("리틀 엔디언(II) EXIF 도 읽는다")
    void readsLittleEndianExif() throws IOException {
        byte[] bytes = TestImages.withExifOrientation(TestImages.quadrantsJpeg(40, 20), 6, true);

        BufferedImage out = decode(ReceiptImageScaler.fitForAi(new ReceiptImage(bytes, "image/jpeg")));

        assertThat(out.getWidth()).isEqualTo(20);
        assertThat(isRed(Quadrant.TOP_RIGHT.sample(out))).isTrue();
    }

    @Test
    @DisplayName("회전값 1 이면 EXIF 가 있어도 원본 그대로")
    void orientationOneIsNoOp() {
        byte[] bytes = TestImages.withExifOrientation(TestImages.quadrantsJpeg(40, 20), 1, false);
        ReceiptImage image = new ReceiptImage(bytes, "image/jpeg");

        assertThat(ReceiptImageScaler.fitForAi(image)).isSameAs(image);
    }

    @Test
    @DisplayName("읽을 수 없는 파일은 415 UNSUPPORTED_FILE_TYPE")
    void unreadableIs415() {
        ReceiptImage broken = new ReceiptImage("fake-jpeg".getBytes(), "image/jpeg");

        assertThatThrownBy(() -> ReceiptImageScaler.fitForAi(broken))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE));
    }

    @Test
    @DisplayName("JPEG 머리만 있고 내용이 잘린 파일도 415")
    void truncatedHeaderIs415() {
        byte[] jpeg = TestImages.jpeg(100, 100);
        ReceiptImage truncated = new ReceiptImage(Arrays.copyOf(jpeg, 20), "image/jpeg");

        assertThatThrownBy(() -> ReceiptImageScaler.fitForAi(truncated))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE));
    }

    @Test
    @DisplayName("구조가 이상한 EXIF 는 무시하고(회전 없음) 요청은 실패시키지 않는다")
    void malformedExifIsIgnored() {
        byte[] bytes = TestImages.withExifOrientation(TestImages.jpeg(100, 100), 6, false);
        assertThat(ReceiptImageScaler.ExifOrientation.read(bytes)).isEqualTo(6);

        bytes[indexOfExifHeader(bytes) + 6] = 'X';   // TIFF 바이트 순서 표시('MM')를 깨뜨린다

        assertThat(ReceiptImageScaler.ExifOrientation.read(bytes)).isEqualTo(1);
        assertThat(ReceiptImageScaler.fitForAi(new ReceiptImage(bytes, "image/jpeg")).bytes()).isSameAs(bytes);
    }

    private static int indexOfExifHeader(byte[] bytes) {
        for (int i = 0; i + 4 <= bytes.length; i++) {
            if (bytes[i] == 'E' && bytes[i + 1] == 'x' && bytes[i + 2] == 'i' && bytes[i + 3] == 'f') {
                return i;
            }
        }
        throw new AssertionError("Exif 헤더 없음");
    }

    enum Quadrant {
        TOP_LEFT(0.25, 0.25), TOP_RIGHT(0.75, 0.25), BOTTOM_LEFT(0.25, 0.75), BOTTOM_RIGHT(0.75, 0.75);

        private final double fx;
        private final double fy;

        Quadrant(double fx, double fy) {
            this.fx = fx;
            this.fy = fy;
        }

        int sample(BufferedImage image) {
            return image.getRGB((int) (image.getWidth() * fx), (int) (image.getHeight() * fy));
        }
    }

    private static boolean isRed(int rgb) {
        return channel(rgb, 16) > 200 && channel(rgb, 8) < 80 && channel(rgb, 0) < 80;
    }

    private static int channel(int rgb, int shift) {
        return (rgb >> shift) & 0xFF;
    }

    private static BufferedImage decode(ReceiptImage image) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(image.bytes()));
    }
}
