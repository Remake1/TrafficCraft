package de.mrjulsen.trafficcraft.data;

import de.mrjulsen.mcdragonlib.data.ETextAlignment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * A single line of a highway sign: text plus an optional direction arrow.
 */
public class HighwaySignLine {

    public static final int MAX_TEXT_LENGTH = 48;
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.0f;
    public static final float SCALE_STEP = 0.25f;

    private static final String NBT_TEXT = "Text";
    private static final String NBT_SCALE = "Scale";
    private static final String NBT_ALIGN = "Align";
    private static final String NBT_ARROW = "Arrow";
    private static final String NBT_ARROW_SIDE = "ArrowSide";
    private static final String NBT_TEXT_COLOR = "TextColor";
    private static final String NBT_HIGHLIGHT = "Highlight";
    /** Legacy (before {@link #NBT_ARROW_SIDE}): only left or right edge. */
    private static final String NBT_ARROW_RIGHT = "ArrowRight";

    public enum ArrowSide {
        /** At the left edge of the sign. */
        LEFT,
        /** At the right edge of the sign. */
        RIGHT,
        /** Directly left of the text. */
        LEFT_OF_TEXT,
        /** Directly right of the text. */
        RIGHT_OF_TEXT;

        public boolean isNextToText() {
            return this == LEFT_OF_TEXT || this == RIGHT_OF_TEXT;
        }

        public static ArrowSide getByIndex(int index) {
            return index >= 0 && index < values().length ? values()[index] : LEFT;
        }
    }

    private String text;
    private float scale;
    private ETextAlignment alignment;
    private HighwayArrow arrow;
    private ArrowSide arrowSide;
    /** Text color of this line or {@link #NO_COLOR} to use the text color of the sign. */
    private int textColor = NO_COLOR;
    /** Background color of the box around the text of this line or {@link #NO_COLOR} for no box. */
    private int highlightColor = NO_COLOR;

    /** Valid colors are always opaque, so 0 can be used as "not set". */
    public static final int NO_COLOR = 0;

    public HighwaySignLine(String text, float scale, ETextAlignment alignment, HighwayArrow arrow, ArrowSide arrowSide) {
        this.text = text == null ? "" : text;
        this.scale = scale;
        this.alignment = alignment == null ? ETextAlignment.CENTER : alignment;
        this.arrow = arrow == null ? HighwayArrow.NONE : arrow;
        this.arrowSide = arrowSide == null ? ArrowSide.LEFT : arrowSide;
    }

    public HighwaySignLine() {
        this("", 1.0f, ETextAlignment.CENTER, HighwayArrow.NONE, ArrowSide.LEFT);
    }

    public HighwaySignLine copy() {
        HighwaySignLine line = new HighwaySignLine(text, scale, alignment, arrow, arrowSide);
        line.textColor = textColor;
        line.highlightColor = highlightColor;
        return line;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public ETextAlignment getAlignment() {
        return alignment;
    }

    public void setAlignment(ETextAlignment alignment) {
        this.alignment = alignment;
    }

    public HighwayArrow getArrow() {
        return arrow;
    }

    public void setArrow(HighwayArrow arrow) {
        this.arrow = arrow;
    }

    public ArrowSide getArrowSide() {
        return arrowSide;
    }

    public void setArrowSide(ArrowSide arrowSide) {
        this.arrowSide = arrowSide;
    }

    public boolean hasCustomTextColor() {
        return textColor != NO_COLOR;
    }

    /** @return The custom text color of this line or {@link #NO_COLOR}. */
    public int getCustomTextColor() {
        return textColor;
    }

    /** @return The text color of this line, or {@code signTextColor} if the line has no own text color. */
    public int getTextColor(int signTextColor) {
        return hasCustomTextColor() ? textColor : signTextColor;
    }

    /** @param color The text color or {@link #NO_COLOR} to use the text color of the sign. */
    public void setTextColor(int color) {
        this.textColor = color == NO_COLOR ? NO_COLOR : color | 0xFF000000;
    }

    public boolean hasHighlight() {
        return highlightColor != NO_COLOR;
    }

    /** @return The background color of the box around the text or {@link #NO_COLOR}. */
    public int getHighlightColor() {
        return highlightColor;
    }

    /** @param color The background color of the box around the text or {@link #NO_COLOR} for no box. */
    public void setHighlightColor(int color) {
        this.highlightColor = color == NO_COLOR ? NO_COLOR : color | 0xFF000000;
    }

    /** Clamps all values into their valid ranges. Must be called on data received from clients. */
    public void sanitize() {
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }
        // Strip formatting codes, they are not supported on highway signs.
        text = text.replace("§", "");
        if (Float.isNaN(scale)) {
            scale = 1.0f;
        }
        scale = Mth.clamp(scale, MIN_SCALE, MAX_SCALE);
        setTextColor(textColor);
        setHighlightColor(highlightColor);
    }

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString(NBT_TEXT, text);
        nbt.putFloat(NBT_SCALE, scale);
        nbt.putByte(NBT_ALIGN, (byte)alignment.getId());
        nbt.putByte(NBT_ARROW, (byte)arrow.getId());
        nbt.putByte(NBT_ARROW_SIDE, (byte)arrowSide.ordinal());
        if (hasCustomTextColor()) nbt.putInt(NBT_TEXT_COLOR, textColor);
        if (hasHighlight()) nbt.putInt(NBT_HIGHLIGHT, highlightColor);
        return nbt;
    }

    public static HighwaySignLine fromNbt(CompoundTag nbt) {
        HighwaySignLine line = new HighwaySignLine(
            nbt.getString(NBT_TEXT),
            nbt.contains(NBT_SCALE) ? nbt.getFloat(NBT_SCALE) : 1.0f,
            ETextAlignment.getById(nbt.getByte(NBT_ALIGN)),
            HighwayArrow.getById(nbt.getByte(NBT_ARROW)),
            nbt.contains(NBT_ARROW_SIDE) ? ArrowSide.getByIndex(nbt.getByte(NBT_ARROW_SIDE)) : (nbt.getBoolean(NBT_ARROW_RIGHT) ? ArrowSide.RIGHT : ArrowSide.LEFT)
        );
        line.setTextColor(nbt.getInt(NBT_TEXT_COLOR));
        line.setHighlightColor(nbt.getInt(NBT_HIGHLIGHT));
        return line;
    }
}
