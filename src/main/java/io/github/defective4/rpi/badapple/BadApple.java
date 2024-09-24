package io.github.defective4.rpi.badapple;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.library.pigpio.PiGpio;
import com.pi4j.plugin.linuxfs.provider.i2c.LinuxFsI2CProvider;
import com.pi4j.plugin.pigpio.provider.gpio.digital.PiGpioDigitalInputProvider;
import com.pi4j.plugin.pigpio.provider.gpio.digital.PiGpioDigitalOutputProvider;
import com.pi4j.plugin.pigpio.provider.pwm.PiGpioPwmProvider;
import com.pi4j.plugin.pigpio.provider.serial.PiGpioSerialProvider;
import com.pi4j.plugin.pigpio.provider.spi.PiGpioSpiProvider;

import io.github.defective4.rpi.badapple.hardware.LcdDisplay;

public class BadApple {

    private static LcdDisplay display;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            String name = BadApple.class.getProtectionDomain().getCodeSource().getLocation().getFile();
            if (name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
            System.err.println("Usage: " + name + " <scan | play> <file/directory> <args>");
            return;
        }

        File target = new File(args[1]);
        switch (args[0].toLowerCase()) {
            case "play" -> {
                int fps;
                try {
                    fps = Integer.parseInt(args[2]);
                } catch (Exception e) {
                    System.err.println("Invalid FPS: " + args[2]);
                    return;
                }
                List<byte[][]> scan;
                try (DataInputStream is = new DataInputStream(new FileInputStream(target))) {
                    scan = ImageScanner.readScanData(is);
                }

                PiGpio piGpio = PiGpio.newNativeInstance();
                Context pi4j = Pi4J
                        .newContextBuilder()
                        .noAutoDetect()
                        .add(PiGpioDigitalInputProvider.newInstance(piGpio),
                                PiGpioDigitalOutputProvider.newInstance(piGpio), PiGpioPwmProvider.newInstance(piGpio),
                                PiGpioSerialProvider.newInstance(piGpio), PiGpioSpiProvider.newInstance(piGpio),
                                LinuxFsI2CProvider.newInstance())
                        .build();

                display = new LcdDisplay(pi4j, 2, 16);

                display.clearDisplay();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> display.clearDisplay()));
                display.centerTextInLine("\1\2\3", 0);
                display.centerTextInLine("\4\5\6", 1);

                new Timer(false).scheduleAtFixedRate(new TimerTask() {

                    private int index = 0;

                    @Override
                    public void run() {
                        if (index < scan.size()) {
                            renderData(scan.get(index++));
                        } else {
                            cancel();
                        }
                    }
                }, 0, 1000 / fps);
            }
            case "render" -> {
                File output = new File(args[2]);
                if (output.isFile()) {
                    System.err.println("Output dir already exists!");
                    return;
                }
                output.mkdirs();
                List<byte[][]> scan;
                try (DataInputStream is = new DataInputStream(new FileInputStream(target))) {
                    scan = ImageScanner.readScanData(is);
                }
                for (int x = 0; x < scan.size(); x++) {
                    ImageIO.write(ImageScanner.render(scan.get(x)), "png", new File(output, "frame" + x + ".png"));
                    System.out.println("Rendered frame " + x);
                }
            }
            case "scan" -> {
                if (!target.isDirectory()) {
                    System.err.println("Target must be a directory!");
                    return;
                }
                File output = new File(args[2]);
                List<String> images = Arrays.stream(target.list()).filter(f -> f.endsWith(".png")).sorted().toList();
                try (DataOutputStream os = new DataOutputStream(new FileOutputStream(output))) {
                    os.writeInt(images.size());
                    for (String imgName : images) {
                        File imgFile = new File(target, imgName);
                        BufferedImage image = ImageIO.read(imgFile);
                        byte[][] scan = ImageScanner.scan15x16Image(image, 0.5f);
                        for (byte[] ar : scan) os.write(ar);
                        System.out.println("Processed " + imgName);
                    }
                }
            }
            default -> {}
        }
    }

    private static void renderData(byte[][] bs) {
        for (int x = 0; x < bs.length; x++) {
            byte[] charData = bs[x];
            display.createCharacter(x + 1, charData);
        }
    }

}
