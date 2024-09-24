package io.github.defective4.rpi.badapple;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImageScanner {
    public static double calculateLuma(Color color) {
        return (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255;
    }

    public static boolean isBright(Color color, float threshold) {
        return calculateLuma(color) > threshold;
    }

    public static List<byte[][]> readScanData(DataInputStream in) throws IOException {
        List<byte[][]> list = new ArrayList<>();
        int count = in.readInt();
        for (int x = 0; x < count; x++) {
            byte[][] data = new byte[6][];
            for (int i = 0; i < data.length; i++) {
                byte[] segment = new byte[8];
                data[i] = segment;
                in.readFully(segment);
            }
            list.add(data);
        }
        return Collections.unmodifiableList(list);
    }

    public static BufferedImage render(byte[][] data) {
        BufferedImage image = new BufferedImage(15, 16, BufferedImage.TYPE_INT_ARGB);
        int white = Color.white.getRGB();
        int black = Color.black.getRGB();
        for (int i = 0; i < data.length; i++) {
            byte[] segment = data[i];
            int sx = i % 3;
            int sy = i / 3;
            for (int x = 0; x < 5; x++) for (int y = 0; y < 8; y++) {
                image.setRGB(x + sx * 5, y + sy * 8, (segment[y] & (byte) Math.pow(2, 4 - x)) > 0 ? white : black);
            }
        }
        return image;
    }

    public static byte[][] scan15x16Image(BufferedImage image, float threshold) {
        if (image.getWidth() != 15 || image.getHeight() != 16)
            throw new IllegalArgumentException("Provided image must be 15x16");
        byte[][] data = new byte[6][];
        for (int i = 0; i < data.length; i++) {
            byte[] segment = new byte[8];
            data[i] = segment;
            int sx = i % 3;
            int sy = i / 3;
            for (int x = 0; x < 5; x++) for (int y = 0; y < 8; y++) {
                if (isBright(new Color(image.getRGB(x + sx * 5, y + sy * 8)), threshold))
                    segment[y] |= (byte) Math.pow(2, 4 - x);
            }
        }
        return data;
    }
}
