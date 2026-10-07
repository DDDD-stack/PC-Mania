package al.pcmania.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImageStorageTest {

    @TempDir
    Path dir;

    @Test
    void appliesExifRotation() throws IOException {
        Path file = dir.resolve("rotated.jpg");
        try (InputStream in = getClass().getResourceAsStream("/exif-rotate-90.jpg")) {
            Files.copy(in, file);
        }
        BufferedImage img = ImageStorage.decode(file);

        assertEquals(100, img.getWidth());
        assertEquals(200, img.getHeight());
        assertTrue(isRed(img.getRGB(50, 20)), "top should be red");
        assertFalse(isRed(img.getRGB(50, 180)), "bottom should be blue");
    }

    @Test
    void appliesExifRotationWhenExifComesBeforeJfif() throws IOException {
        BufferedImage img = ImageStorage.decode(fixture("/exif-before-jfif.jpg"));
        assertEquals(100, img.getWidth());
        assertEquals(200, img.getHeight());
        assertTrue(isRed(img.getRGB(50, 20)), "top should be red");
        assertFalse(isRed(img.getRGB(50, 180)), "bottom should be blue");
    }

    @Test
    void appliesExifRotationToWebp() throws IOException {
        BufferedImage img = ImageStorage.decode(fixture("/exif-rotate-90.webp"));
        assertEquals(100, img.getWidth());
        assertEquals(200, img.getHeight());
    }

    @Test
    void photosWithoutExifAreLeftAsTheyAre() throws IOException {
        assertNull(ImageStorage.exifOrientation(jpeg(300, 200)));
        assertNull(ImageStorage.exifOrientation(fixture("/lossless.webp")));
    }

    @Test
    void subsamplesVeryLargePhotosButNotOrdinaryOnes() throws IOException {
        assertEquals(2500, longestSide(ImageStorage.decode(jpeg(5000, 3000))));
        assertEquals(4000, longestSide(ImageStorage.decode(jpeg(4000, 3000))));
        assertEquals(800, longestSide(ImageStorage.decode(jpeg(800, 600))));
    }

    @Test
    void flattensTransparencyOntoWhite() throws IOException {
        BufferedImage png = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Path file = dir.resolve("clear.png");
        ImageIO.write(png, "png", file.toFile());
        BufferedImage img = ImageStorage.decode(file);
        assertFalse(img.getColorModel().hasAlpha());
        assertEquals(0xffffff, img.getRGB(5, 5) & 0xffffff);
    }

    @Test
    void readsWebpEvenWhenNamedJpg() throws IOException {
        BufferedImage img = ImageStorage.decode(fixture("/saved-from-web-really-webp.jpg"));
        assertEquals(120, img.getWidth());
        assertEquals(80, img.getHeight());
        assertTrue(isRed(img.getRGB(20, 40)), "left half should be red");
        assertEquals(0xffffff, img.getRGB(100, 40) & 0xffffff, "transparent half should be white");

        BufferedImage lossless = ImageStorage.decode(fixture("/lossless.webp"));
        assertEquals(90, lossless.getWidth());
        assertEquals(60, lossless.getHeight());
    }

    @Test
    void rejectsFilesThatAreNotImages() throws IOException {
        Path file = dir.resolve("notes.txt");
        Files.writeString(file, "not a photo");
        assertThrows(IllegalArgumentException.class, () -> ImageStorage.decode(file));
    }

    private Path fixture(String resource) throws IOException {
        Path file = dir.resolve(resource.substring(1));
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            Files.copy(in, file);
        }
        return file;
    }

    private Path jpeg(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(40, 44, 52));
        g.fillRect(0, 0, w, h);
        g.dispose();
        Path file = dir.resolve(w + "x" + h + ".jpg");
        ImageIO.write(img, "jpg", file.toFile());
        return file;
    }

    private static int longestSide(BufferedImage img) {
        return Math.max(img.getWidth(), img.getHeight());
    }

    private static boolean isRed(int rgb) {
        Color c = new Color(rgb);
        return c.getRed() > 150 && c.getBlue() < 100;
    }
}
