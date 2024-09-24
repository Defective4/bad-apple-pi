package io.github.defective4.rpi.badapple.hardware;

import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;

/**
 * Implementation of a LCDDisplay using GPIO with Pi4J
 * <p>
 * Works with the PCF8574T backpack, only.
 */
public class LcdDisplay extends I2CDevice {
    private static final int DEFAULT_DEVICE = 0x27;
    private static final byte En = (byte) 0b000_00100; // Enable bit
    private static final byte LCD_1LINE = (byte) 0x00;
    private static final byte LCD_2LINE = (byte) 0x08;
    private static final byte LCD_4BIT_MODE = (byte) 0x00;
    private static final byte LCD_5x10DOTS = (byte) 0x04;
    private static final byte LCD_5x8DOTS = (byte) 0x00;
    // flags for function set
    private static final byte LCD_8BIT_MODE = (byte) 0x10;
    // flags for backlight control
    private static final byte LCD_BACKLIGHT = (byte) 0x08;
    private static final byte LCD_BLINK_OFF = (byte) 0x00;
    private static final byte LCD_BLINK_ON = (byte) 0x01;
    /** Flags for display commands */
    private static final byte LCD_CLEAR_DISPLAY = (byte) 0x01;
    private static final byte LCD_CURSOR_MOVE = (byte) 0x00;
    private static final byte LCD_CURSOR_OFF = (byte) 0x00;
    private static final byte LCD_CURSOR_ON = (byte) 0x02;
    private static final byte LCD_CURSOR_SHIFT = (byte) 0x10;
    private static final byte LCD_DISPLAY_CONTROL = (byte) 0x08;
    // flags for display/cursor shift
    private static final byte LCD_DISPLAY_MOVE = (byte) 0x08;
    private static final byte LCD_DISPLAY_OFF = (byte) 0x00;
    // flags for display on/off control
    private static final byte LCD_DISPLAY_ON = (byte) 0x04;
    private static final byte LCD_ENTRY_LEFT = (byte) 0x02;
    private static final byte LCD_ENTRY_MODE_SET = (byte) 0x04;
    // flags for display entry mode
    private static final byte LCD_ENTRY_RIGHT = (byte) 0x00;
    private static final byte LCD_ENTRY_SHIFT_DECREMENT = (byte) 0x00;
    private static final byte LCD_ENTRY_SHIFT_INCREMENT = (byte) 0x01;
    private static final byte LCD_FUNCTION_SET = (byte) 0x20;
    private static final byte LCD_NO_BACKLIGHT = (byte) 0x00;
    private static final byte LCD_RETURN_HOME = (byte) 0x02;
    /**
     * Display row offsets. Offset for up to 4 rows.
     */
    private static final byte[] LCD_ROW_OFFSETS = {
            0x00, 0x40, 0x14, 0x54
    };
    private static final byte LCD_SCROLL_LEFT = (byte) 0x18;
    private static final byte LCD_SCROLL_RIGHT = (byte) 0x1E;
    private static final byte LCD_SET_CGRAM_ADDR = (byte) 0x40;
    private static final byte LCD_SET_DDRAM_ADDR = (byte) 0x80;

    private static final byte Rs = (byte) 0b000_00001; // Register select bit

    private static final byte Rw = (byte) 0b000_00010; // Read/Write bit

    /**
     * Is backlight is on or off
     */
    private boolean backlight;
    /**
     * Number of columns on the display
     */
    private final int columns;
    /**
     * Number of rows on the display
     */
    private final int rows;

    /**
     * Creates a new LCDDisplay component with default values
     *
     * @param pi4j Pi4J context
     */
    public LcdDisplay(Context pi4j) {
        this(pi4j, 2, 16, DEFAULT_DEVICE);
    }

    /**
     * Creates a new LCDDisplay component with custom rows and columns
     *
     * @param pi4j    Pi4J context
     * @param rows    amount of display lines
     * @param columns amount of chars on each line
     */
    public LcdDisplay(Context pi4j, int rows, int columns) {
        this(pi4j, rows, columns, DEFAULT_DEVICE);
    }

    /**
     * Creates a new LCDDisplay component with custom rows and columns
     *
     * @param pi4j    Pi4J context
     * @param rows    amount of display lines
     * @param columns amount of chars on each line
     * @param device  I2C device address
     */
    public LcdDisplay(Context pi4j, int rows, int columns, int device) {
        super(pi4j, device, "PCF8574AT backed LCD");
        this.rows = rows;
        this.columns = columns;
    }

    /**
     * Center specified text in specified line
     *
     * @param text Text to be displayed
     * @param line linenumber of display, range: 0 .. rows-1
     */
    public void centerTextInLine(String text, int line) {
        displayLineOfText(text, line, (int) ((columns - text.length()) * 0.5));
    }

    /**
     * Clear the LCD and set cursor to home
     */
    public void clearDisplay() {
        moveCursorHome();
        sendLcdTwoPartsCommand(LCD_CLEAR_DISPLAY);
    }

    /**
     * Clears a line of the display
     *
     * @param line line number of line to be cleared
     */
    public void clearLine(int line) {
        if (line > rows || line < 1) {
            throw new IllegalArgumentException("Wrong line id. Only " + rows + " lines possible");
        }
        displayLine(" ".repeat(columns), LCD_ROW_OFFSETS[line - 1]);
    }

    /**
     * Create a custom character by providing the single digit states of each pixel.
     * Simply pass an Array of bytes which will be translated to a character.
     *
     * @param location  Set the memory location of the character. 1 - 7 is possible.
     * @param character Byte array representing the pixels of a character
     */
    public void createCharacter(int location, byte[] character) {
        if (character.length != 8) {
            throw new IllegalArgumentException(
                    "Array has invalid length. Character is only 5x8 Digits. Only a array with length"
                            + " 8 is allowed");
        }

        if (location > 7 || location < 1) {
            throw new IllegalArgumentException("Invalid memory location. Range 1-7 allowed. Value: " + location);
        }
        sendLcdTwoPartsCommand((byte) (LCD_SET_CGRAM_ADDR | location << 3));

        for (int i = 0; i < 8; i++) {
            sendLcdTwoPartsCommand(character[i], (byte) 1);
        }
    }

    /**
     * Write a line of text on the LCD
     *
     * @param text Text to be displayed
     * @param line linenumber of display, range: 0 .. rows-1
     */
    public void displayLineOfText(String text, int line) {
        displayLineOfText(text, line, 0);
    }

    /**
     * Write a line of text on the LCD
     *
     * @param text     text to be displayed
     * @param line     line number of display, range: 0..rows-1
     * @param position start position, range: 0..columns-1
     */
    public void displayLineOfText(String text, int line, int position) {
        if (text.length() + position > columns) {
            logInfo("Text '%s' too long, cut to %d characters", text, columns - position);
            text = text.substring(0, columns - position);
        }

        if (line > rows || line < 0) {
            logError("Wrong line id '%d'. Only %d lines possible", line, rows);
        } else {
            setCursorToPosition(line, 0);
            for (int i = 0; i < position; i++) {
                writeCharacter(' ');
            }
            for (char character : text.toCharArray()) {
                writeCharacter(character);
            }
            for (int i = 0; i < columns - text.length(); i++) {
                writeCharacter(' ');
            }
        }
    }

    /**
     * Write text on the LCD starting in home position
     *
     * @param text Text to display
     */
    public void displayText(String text) {
        logDebug("Display in LCD: '%s'", text);
        var currentLine = 0;

        StringBuilder[] texts = new StringBuilder[rows];
        for (int j = 0; j < rows; j++) {
            texts[j] = new StringBuilder(rows);
        }

        for (int i = 0; i < text.length(); i++) {
            if (currentLine > rows - 1) {
                logInfo("Text too long, remaining '%s' will not be displayed", text.substring(i));
                break;
            }
            if (text.charAt(i) == '\n') {
                currentLine++;
                continue;
            }
            if (texts[currentLine].length() >= columns) {
                currentLine++;
                if (text.charAt(i) == ' ') {
                    i++;
                }
            }
            // append character to line
            if (currentLine < rows) {
                texts[currentLine].append(text.charAt(i));
            }
        }

        // display the created texts
        for (int j = 0; j < rows; j++) {
            displayLineOfText(texts[j].toString(), j);
        }
    }

    /**
     * Returns the Cursor to Home Position (First line, first character)
     */
    public void moveCursorHome() {
        sendLcdTwoPartsCommand(LCD_RETURN_HOME);
    }

    /**
     * Shuts the display off
     */
    public void off() {
        sendCommand(LCD_DISPLAY_OFF);
    }

    @Override
    public void reset() {
        clearDisplay();
        off();
    }

    /**
     * Scroll whole display to the left by one column.
     */
    public void scrollLeft() {
        sendLcdTwoPartsCommand(LCD_SCROLL_LEFT);
    }

    /**
     * Scroll whole display to the right by one column.
     */
    public void scrollRight() {
        sendLcdTwoPartsCommand(LCD_SCROLL_RIGHT);
    }

    /**
     * Sets the cursor to a target destination
     *
     * @param line Selects the line of the display. Range: 0 - ROWS-1
     * @param pos  Selects the character of the line. Range: 0 - Columns-1
     */
    public void setCursorToPosition(int line, int pos) {
        if (line > rows - 1 || line < 0 || pos < 0 || pos > columns - 1) {
            throw new IllegalArgumentException(
                    "Line out of range. Display has only " + rows + "x" + columns + " Characters!");
        }
        sendLcdTwoPartsCommand((byte) (LCD_SET_DDRAM_ADDR | pos + LCD_ROW_OFFSETS[line]));
    }

    /**
     * Turns the backlight on or off
     */
    public void setDisplayBacklight(boolean backlightEnabled) {
        backlight = backlightEnabled;
        sendCommand(backlight ? LCD_BACKLIGHT : LCD_NO_BACKLIGHT);
    }

    /**
     * write a character to LCD at current cursor position
     *
     * @param character char that is written
     */
    public void writeCharacter(char character) {
        sendLcdTwoPartsCommand((byte) character, Rs);
    }

    /**
     * write a character to lcd at a specific position
     *
     * @param character char that is written
     * @param line      row-position, Range 0 .. rows-1
     * @param pos       col-position, Range 0 .. columns-1
     */
    public void writeCharacter(char character, int line, int pos) {
        setCursorToPosition(line, pos);
        sendLcdTwoPartsCommand((byte) character, Rs);
    }

    /**
     * Initializes the LCD with the backlight off
     */
    @Override
    protected void init(I2C i2c) {
        sendLcdTwoPartsCommand((byte) 0x03);
        sendLcdTwoPartsCommand((byte) 0x03);
        sendLcdTwoPartsCommand((byte) 0x03);
        sendLcdTwoPartsCommand((byte) 0x02);

        // Initialize display settings
        sendLcdTwoPartsCommand((byte) (LCD_FUNCTION_SET | LCD_2LINE | LCD_5x8DOTS | LCD_4BIT_MODE));
        sendLcdTwoPartsCommand((byte) (LCD_DISPLAY_CONTROL | LCD_DISPLAY_ON | LCD_CURSOR_OFF | LCD_BLINK_OFF));
        sendLcdTwoPartsCommand((byte) (LCD_ENTRY_MODE_SET | LCD_ENTRY_LEFT | LCD_ENTRY_SHIFT_DECREMENT));

        clearDisplay();

        // Enable backlight
        setDisplayBacklight(true);
    }

    /**
     * displays a line on a specific position
     *
     * @param text to display
     * @param pos  for the start of the text
     */
    private void displayLine(String text, int pos) {
        sendLcdTwoPartsCommand((byte) (0x80 + pos));

        for (int i = 0; i < text.length(); i++) {
            writeCharacter(text.charAt(i));
        }
    }

    /**
     * Write a command to the LCD
     */
    private void sendLcdTwoPartsCommand(byte cmd) {
        sendLcdTwoPartsCommand(cmd, (byte) 0);
    }

    /**
     * Write a command in 2 parts to the LCD
     */
    private void sendLcdTwoPartsCommand(byte cmd, byte mode) {
        // bitwise AND with 11110000 to remove last 4 bits
        writeFourBits((byte) (mode | cmd & 0xF0));
        // bitshift and bitwise AND to remove first 4 bits
        writeFourBits((byte) (mode | cmd << 4 & 0xF0));
    }

    /**
     * Write the four bits of a byte to the LCD
     *
     * @param data the byte that is sent
     */
    private void writeFourBits(byte data) {
        byte backlightStatus = backlight ? LCD_BACKLIGHT : LCD_NO_BACKLIGHT;

        write((byte) (data | En | backlightStatus));
        write((byte) (data & ~En | backlightStatus));

//        delay(Duration.ofNanos(50_000));
    }

}
