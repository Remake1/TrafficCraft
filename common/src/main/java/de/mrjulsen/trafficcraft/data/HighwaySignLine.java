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
    private static final String NBT_ARROW_RIGHT = "ArrowRight";

    public enum ArrowSide {
        LEFT,
        RIGHT;
    }

    private String text;
    private float scale;
    private ETextAlignment alignment;
    private HighwayArrow arrow;
    private ArrowSide arrowSide;

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
        return new HighwaySignLine(text, scale, alignment, arrow, arrowSide);
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
    }

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString(NBT_TEXT, text);
        nbt.putFloat(NBT_SCALE, scale);
        nbt.putByte(NBT_ALIGN, (byte)alignment.getId());
        nbt.putByte(NBT_ARROW, (byte)arrow.getId());
        nbt.putBoolean(NBT_ARROW_RIGHT, arrowSide == ArrowSide.RIGHT);
        return nbt;
    }

    public static HighwaySignLine fromNbt(CompoundTag nbt) {
        return new HighwaySignLine(
            nbt.getString(NBT_TEXT),
            nbt.contains(NBT_SCALE) ? nbt.getFloat(NBT_SCALE) : 1.0f,
            ETextAlignment.getById(nbt.getByte(NBT_ALIGN)),
            HighwayArrow.getById(nbt.getByte(NBT_ARROW)),
            nbt.getBoolean(NBT_ARROW_RIGHT) ? ArrowSide.RIGHT : ArrowSide.LEFT
        );
    }
}
