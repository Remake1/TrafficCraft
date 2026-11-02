package de.mrjulsen.trafficcraft.data;

import java.util.Arrays;

import de.mrjulsen.mcdragonlib.data.ITranslatableEnum;
import de.mrjulsen.trafficcraft.TrafficCraft;

/**
 * Direction arrows that can be shown next to a line of a highway sign.
 * Each arrow (except {@link #NONE}) references one cell of the arrow atlas
 * {@code textures/block/highway_sign/arrows.png} ({@value #ATLAS_COLUMNS} x {@value #ATLAS_ROWS} cells).
 */
public enum HighwayArrow implements ITranslatableEnum {
    NONE(0, "none", -1),
    UP(1, "up", 0),
    UP_LEFT(2, "up_left", 1),
    UP_RIGHT(3, "up_right", 2),
    LEFT(4, "left", 3),
    RIGHT(5, "right", 4),
    DOWN(6, "down", 5),
    DOWN_LEFT(7, "down_left", 6),
    DOWN_RIGHT(8, "down_right", 7),
    EXIT_LEFT(9, "exit_left", 8),
    EXIT_RIGHT(10, "exit_right", 9);

    public static final int ATLAS_COLUMNS = 4;
    public static final int ATLAS_ROWS = 3;
    /** Index of a fully white atlas cell, used to draw solid colored boxes in the same render batch as arrows. */
    public static final int WHITE_CELL_INDEX = ATLAS_COLUMNS * ATLAS_ROWS - 1;
    /** Texture coordinates of the center of the white atlas cell. */
    public static final float WHITE_U = ((WHITE_CELL_INDEX % ATLAS_COLUMNS) + 0.5f) / ATLAS_COLUMNS;
    public static final float WHITE_V = ((WHITE_CELL_INDEX / ATLAS_COLUMNS) + 0.5f) / ATLAS_ROWS;

    private final int id;
    private final String name;
    private final int atlasIndex;

    private HighwayArrow(int id, String name, int atlasIndex) {
        this.id = id;
        this.name = name;
        this.atlasIndex = atlasIndex;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isVisible() {
        return atlasIndex >= 0;
    }

    /** @return The left texture coordinate (0..1) of this arrow in the atlas. */
    public float getU0() {
        return (float)(atlasIndex % ATLAS_COLUMNS) / ATLAS_COLUMNS;
    }

    /** @return The top texture coordinate (0..1) of this arrow in the atlas. */
    public float getV0() {
        return (float)(atlasIndex / ATLAS_COLUMNS) / ATLAS_ROWS;
    }

    public float getU1() {
        return getU0() + 1.0f / ATLAS_COLUMNS;
    }

    public float getV1() {
        return getV0() + 1.0f / ATLAS_ROWS;
    }

    public HighwayArrow next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static HighwayArrow getById(int id) {
        return Arrays.stream(values()).filter(x -> x.getId() == id).findFirst().orElse(NONE);
    }

    @Override
    public Data getTranslationData() {
        return new Data(TrafficCraft.MOD_ID, "highway_arrow", name);
    }
}
