package de.mrjulsen.trafficcraft.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;

import de.mrjulsen.trafficcraft.data.HighwaySignLine.ArrowSide;

/**
 * Pre-computed positions and sizes of all elements of a highway sign. Shared by the in-world renderer and the
 * editor preview, so both look identical.
 * <p>
 * All values are in block units. The origin is the top left corner of the front face of the sign, x points to
 * the right and y points down.
 * </p>
 * This class has no client dependencies: text measurement is passed in as a function.
 */
public final class HighwaySignLayout {

    /** Distance between the sign border and its content. */
    public static final float PADDING = 2.0f / 16.0f;
    /** Size of one font pixel in blocks at line scale 1.0. */
    public static final float FONT_PIXEL = 1.0f / 32.0f;
    /** Vertical space of a line relative to the font line height. */
    public static final float LINE_SPACING = 1.15f;
    /** Size of an arrow relative to the font line height. */
    public static final float ARROW_SIZE = 1.0f;
    /** Gap between arrow and text relative to the arrow size. */
    public static final float ARROW_GAP = 0.25f;
    /** Height of the visible glyphs in font pixels. */
    public static final float GLYPH_HEIGHT = 7.0f;

    public static record LineLayout(
        String text,
        /** Left position of the text. */
        float textX,
        /** Top position of the glyphs. */
        float textY,
        /** Size of a font pixel in blocks. */
        float textScale,
        HighwayArrow arrow,
        float arrowX,
        float arrowY,
        float arrowSize
    ) {
        public boolean hasText() {
            return !text.isEmpty();
        }

        public boolean hasArrow() {
            return arrow.isVisible();
        }
    }

    public static final HighwaySignLayout EMPTY = new HighwaySignLayout(1, 1, List.of());

    private final float width;
    private final float height;
    private final List<LineLayout> lines;

    private HighwaySignLayout(float width, float height, List<LineLayout> lines) {
        this.width = width;
        this.height = height;
        this.lines = Collections.unmodifiableList(lines);
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public List<LineLayout> getLines() {
        return lines;
    }

    public boolean isEmpty() {
        return lines.stream().noneMatch(x -> x.hasText() || x.hasArrow());
    }

    /**
     * @param data The sign content.
     * @param textWidth Returns the width of a text in font pixels.
     * @param fontLineHeight The height of a font line in font pixels (usually 9).
     */
    public static HighwaySignLayout compute(HighwaySignData data, ToIntFunction<String> textWidth, int fontLineHeight) {
        final float w = data.getWidth();
        final float h = data.getHeight();
        final List<HighwaySignLine> source = data.getLines();
        if (source.isEmpty()) {
            return new HighwaySignLayout(w, h, List.of());
        }

        final float availableHeight = h - 2 * PADDING;

        // Vertical layout: lines keep their relative sizes, all of them shrink if they don't fit.
        float totalHeight = 0;
        for (HighwaySignLine line : source) {
            totalHeight += fontLineHeight * FONT_PIXEL * line.getScale() * LINE_SPACING;
        }
        final float fit = totalHeight > availableHeight ? availableHeight / totalHeight : 1.0f;
        float y = PADDING + (availableHeight - totalHeight * fit) / 2.0f;

        List<LineLayout> result = new ArrayList<>(source.size());
        for (HighwaySignLine line : source) {
            final float pixel = FONT_PIXEL * line.getScale() * fit;
            final float slotHeight = fontLineHeight * pixel * LINE_SPACING;

            float areaLeft = PADDING;
            float areaRight = w - PADDING;

            final HighwayArrow arrow = line.getArrow();
            final ArrowSide side = line.getArrowSide();
            final float arrowSize = fontLineHeight * pixel * ARROW_SIZE;
            final float arrowSpace = arrow.isVisible() ? arrowSize * (1 + ARROW_GAP) : 0;
            final float arrowY = y + (slotHeight - arrowSize) / 2.0f;
            float arrowX = 0;

            // Arrows at the sign edges reserve their space first, the text is aligned in the remaining area.
            if (arrow.isVisible() && side == ArrowSide.LEFT) {
                arrowX = areaLeft;
                areaLeft += arrowSpace;
            } else if (arrow.isVisible() && side == ArrowSide.RIGHT) {
                arrowX = areaRight - arrowSize;
                areaRight -= arrowSpace;
            }
            final boolean arrowNextToText = arrow.isVisible() && side.isNextToText();

            // Horizontal fit: long lines are scaled down to fit into the available area.
            final String text = line.getText();
            final float areaWidth = Math.max(0, areaRight - areaLeft);
            final float textAreaWidth = Math.max(0, areaWidth - (arrowNextToText ? arrowSpace : 0));
            final int rawWidth = text.isEmpty() ? 0 : textWidth.applyAsInt(text);
            float textScale = pixel;
            if (rawWidth * textScale > textAreaWidth && rawWidth > 0) {
                textScale = textAreaWidth / rawWidth;
            }
            final float scaledWidth = rawWidth * textScale;

            // Arrows next to the text form a group with the text, which is aligned as a whole.
            final float groupWidth = arrowNextToText ? (rawWidth > 0 ? scaledWidth + arrowSpace : arrowSize) : scaledWidth;
            final float groupX = switch (line.getAlignment()) {
                case LEFT -> areaLeft;
                case RIGHT -> areaRight - groupWidth;
                default -> areaLeft + (areaWidth - groupWidth) / 2.0f;
            };
            float textX = groupX;
            if (arrowNextToText) {
                if (side == ArrowSide.LEFT_OF_TEXT) {
                    arrowX = groupX;
                    textX = groupX + arrowSpace;
                } else {
                    arrowX = groupX + groupWidth - arrowSize;
                }
            }
            final float textY = y + (slotHeight - GLYPH_HEIGHT * textScale) / 2.0f;

            result.add(new LineLayout(text, textX, textY, textScale, arrow, arrowX, arrowY, arrowSize));
            y += slotHeight;
        }
        return new HighwaySignLayout(w, h, result);
    }
}
