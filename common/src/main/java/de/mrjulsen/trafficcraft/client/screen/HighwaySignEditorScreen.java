package de.mrjulsen.trafficcraft.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import com.mojang.blaze3d.systems.RenderSystem;

import de.mrjulsen.mcdragonlib.client.gui.builtin.DLColorPickerWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import de.mrjulsen.mcdragonlib.data.ETextAlignment;
import de.mrjulsen.mcdragonlib.network.NetworkDirection;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.trafficcraft.block.HighwaySignBlock.ResizeResult;
import de.mrjulsen.trafficcraft.block.entity.HighwaySignBlockEntity;
import de.mrjulsen.trafficcraft.client.HighwaySignClipboard;
import de.mrjulsen.trafficcraft.client.ber.HighwaySignBlockEntityRenderer;
import de.mrjulsen.trafficcraft.data.HighwayArrow;
import de.mrjulsen.trafficcraft.data.HighwaySignColorPresets;
import de.mrjulsen.trafficcraft.data.HighwaySignData;
import de.mrjulsen.trafficcraft.data.HighwaySignLayout;
import de.mrjulsen.trafficcraft.data.HighwaySignLayout.LineLayout;
import de.mrjulsen.trafficcraft.data.HighwaySignLine;
import de.mrjulsen.trafficcraft.data.HighwaySignLine.ArrowSide;
import de.mrjulsen.trafficcraft.network.packets.cts.HighwaySignPacket;
import de.mrjulsen.trafficcraft.registry.ModNetworkManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Editor for highway signs. Opened by right-clicking a highway sign with a wrench.
 * All changes are made on a local copy and only sent to the server when "Done" is pressed.
 */
public class HighwaySignEditorScreen extends Screen {

    private static final String KEY = "gui.trafficcraft.highway_sign.";

    private static final int CONTENT_WIDTH = 300;
    private static final int PREVIEW_TOP = 18;
    private static final int PREVIEW_HEIGHT = 50;
    private static final int ROW_HEIGHT = 20;
    private static final int WIDGET_HEIGHT = 18;
    private static final int SWATCH_SIZE = 18;
    private static final int LABEL_WIDTH = 64;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int STATUS_COLOR = 0xFFFF5555;
    private static final int SERVER_TIMEOUT_TICKS = 200;
    private static final float[] SCALES = { 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f };

    private static record Swatch(int x, int y, int color, boolean selected) {}
    private static record Label(Component text, int x, int y) {}

    private final BlockPos anchor;
    private final HighwaySignData data;

    private final List<Swatch> swatches = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private int scrollOffset = 0;
    private int visibleLines = 1;
    private boolean needsRebuild = false;
    private boolean waitingForServer = false;
    private int waitTicks = 0;
    private Component status = null;
    private boolean statusIsError = false;

    public HighwaySignEditorScreen(HighwaySignBlockEntity blockEntity) {
        super(Component.translatable(KEY + "title"));
        this.anchor = blockEntity.getBlockPos();
        this.data = blockEntity.getData().copy();
        if (data.getLines().isEmpty()) {
            data.getLines().add(new HighwaySignLine());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int left() {
        return (width - CONTENT_WIDTH) / 2;
    }

    private int linesTop() {
        return PREVIEW_TOP + PREVIEW_HEIGHT + 4 + ROW_HEIGHT * 3 + 2;
    }

    private void requestRebuild() {
        needsRebuild = true;
    }

    @Override
    protected void init() {
        swatches.clear();
        labels.clear();

        final int left = left();
        int y = PREVIEW_TOP + PREVIEW_HEIGHT + 4;

        // --- Row 1: size and border ---
        labels.add(new Label(Component.translatable(KEY + "size"), left, y + 5));
        addSizePicker(left + LABEL_WIDTH, y, "W", data.getWidth(), HighwaySignData.MIN_WIDTH, HighwaySignData.MAX_WIDTH, w -> data.setSize(w, data.getHeight()));
        addSizePicker(left + LABEL_WIDTH + 70, y, "H", data.getHeight(), HighwaySignData.MIN_HEIGHT, HighwaySignData.MAX_HEIGHT, h -> data.setSize(data.getWidth(), h));
        addRenderableWidget(Button.builder(Component.translatable(KEY + "border").append(": ").append(CommonComponents.optionStatus(data.hasBorder())), b -> {
            data.setBorder(!data.hasBorder());
            requestRebuild();
        }).bounds(left + CONTENT_WIDTH - 90, y, 90, WIDGET_HEIGHT).build());
        y += ROW_HEIGHT;

        // --- Row 2 and 3: colors ---
        addColorRow(left, y, Component.translatable(KEY + "background"), data.getBackgroundColor(), data::setBackgroundColor);
        y += ROW_HEIGHT;
        addColorRow(left, y, Component.translatable(KEY + "text_color"), data.getTextColor(), data::setTextColor);

        // --- Lines ---
        final int linesTop = linesTop();
        visibleLines = Math.max(1, (height - 28 - linesTop) / ROW_HEIGHT);
        scrollOffset = Math.max(0, Math.min(scrollOffset, data.getLines().size() - visibleLines));
        for (int i = scrollOffset; i < Math.min(data.getLines().size(), scrollOffset + visibleLines); i++) {
            addLineRow(left, linesTop + (i - scrollOffset) * ROW_HEIGHT, i);
        }

        // --- Bottom bar ---
        final int bottom = height - 24;
        Button addLine = addRenderableWidget(Button.builder(Component.translatable(KEY + "add_line", data.getLines().size(), data.getMaxLines()), b -> {
            data.getLines().add(new HighwaySignLine());
            scrollOffset = Math.max(0, data.getLines().size() - visibleLines);
            requestRebuild();
        }).bounds(left, bottom, 70, 20).build());
        addLine.active = data.canAddLine();

        addRenderableWidget(Button.builder(Component.translatable(KEY + "copy"), b -> copy())
            .bounds(left + 74, bottom, 50, 20).tooltip(Tooltip.create(Component.translatable(KEY + "copy.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable(KEY + "paste"), b -> paste())
            .bounds(left + 126, bottom, 50, 20).tooltip(Tooltip.create(Component.translatable(KEY + "paste.tooltip"))).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(left + CONTENT_WIDTH - 122, bottom, 60, 20).build());
        Button done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save()).bounds(left + CONTENT_WIDTH - 60, bottom, 60, 20).build());
        done.active = !waitingForServer;
    }

    private void addSizePicker(int x, int y, String name, int value, int min, int max, IntConsumer setter) {
        labels.add(new Label(Component.literal(name + ": " + value), x + 20, y + 5));
        Button minus = addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            setter.accept(value - 1);
            requestRebuild();
        }).bounds(x, y, 18, WIDGET_HEIGHT).build());
        minus.active = value > min;
        Button plus = addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            setter.accept(value + 1);
            requestRebuild();
        }).bounds(x + 48, y, 18, WIDGET_HEIGHT).build());
        plus.active = value < max;
    }

    private void addColorRow(int left, int y, Component label, int current, IntConsumer setter) {
        labels.add(new Label(label, left, y + 5));
        int x = left + LABEL_WIDTH;
        for (int color : HighwaySignColorPresets.ALL) {
            final int c = color;
            addRenderableWidget(Button.builder(Component.empty(), b -> {
                setter.accept(c);
                requestRebuild();
            }).bounds(x, y, SWATCH_SIZE, WIDGET_HEIGHT).build());
            swatches.add(new Swatch(x, y, c, (current | 0xFF000000) == c));
            x += SWATCH_SIZE + 2;
        }

        boolean custom = true;
        for (int color : HighwaySignColorPresets.ALL) {
            if ((current | 0xFF000000) == color) custom = false;
        }
        Button customButton = addRenderableWidget(Button.builder(Component.literal("..."), b -> {
            DLWindow.openWindow(mgr -> new DLColorPickerWindow(mgr, false, DLColor.fromInt(current), picked -> setter.accept(picked.getAsARGB())));
        }).bounds(x, y, SWATCH_SIZE + 12, WIDGET_HEIGHT).tooltip(Tooltip.create(Component.translatable(KEY + "custom_color", String.format("#%06X", current & 0xFFFFFF)))).build());
        if (custom) {
            swatches.add(new Swatch(x + SWATCH_SIZE + 14, y, current, true));
        }
    }

    private void addLineRow(int left, int y, int index) {
        final HighwaySignLine line = data.getLines().get(index);
        int x = left;

        EditBox text = new EditBox(font, x + 1, y + 1, 88, WIDGET_HEIGHT - 2, Component.translatable(KEY + "line.text"));
        text.setMaxLength(HighwaySignLine.MAX_TEXT_LENGTH);
        text.setValue(line.getText());
        text.setResponder(line::setText);
        addRenderableWidget(text);
        x += 92;

        addRenderableWidget(Button.builder(Component.literal(alignmentSymbol(line.getAlignment())), b -> {
            line.setAlignment(ETextAlignment.values()[(line.getAlignment().ordinal() + 1) % ETextAlignment.values().length]);
            requestRebuild();
        }).bounds(x, y, 20, WIDGET_HEIGHT).tooltip(Tooltip.create(Component.translatable(KEY + "line.alignment", line.getAlignment().getValueTranslation()))).build());
        x += 22;

        addRenderableWidget(Button.builder(Component.literal(String.format("%.2fx", line.getScale())), b -> {
            line.setScale(nextScale(line.getScale(), hasShiftDown() ? -1 : 1));
            requestRebuild();
        }).bounds(x, y, 36, WIDGET_HEIGHT).tooltip(Tooltip.create(Component.translatable(KEY + "line.scale"))).build());
        x += 38;

        addLineColorButton(x, y, "A", "line.text_color", line.getCustomTextColor(), data.getTextColor(), line::setTextColor);
        x += SWATCH_SIZE + 2;
        addLineColorButton(x, y, "\u25A2", "line.highlight", line.getHighlightColor(), HighwaySignColorPresets.WHITE, line::setHighlightColor);
        x += SWATCH_SIZE + 2;

        addRenderableWidget(Button.builder(Component.literal(arrowSymbol(line.getArrow())), b -> {
            HighwayArrow[] values = HighwayArrow.values();
            int step = hasShiftDown() ? values.length - 1 : 1;
            line.setArrow(values[(line.getArrow().ordinal() + step) % values.length]);
            requestRebuild();
        }).bounds(x, y, 30, WIDGET_HEIGHT).tooltip(Tooltip.create(Component.translatable(KEY + "line.arrow", line.getArrow().getValueTranslation()))).build());
        x += 32;

        Button side = addRenderableWidget(Button.builder(Component.literal(arrowSideSymbol(line.getArrowSide())), b -> {
            ArrowSide[] values = ArrowSide.values();
            int step = hasShiftDown() ? values.length - 1 : 1;
            line.setArrowSide(values[(line.getArrowSide().ordinal() + step) % values.length]);
            requestRebuild();
        }).bounds(x, y, 30, WIDGET_HEIGHT).tooltip(Tooltip.create(Component.translatable(KEY + "line.arrow_side", Component.translatable(KEY + "arrow_side." + line.getArrowSide().name().toLowerCase())))).build());
        side.active = line.getArrow().isVisible();
        x += 32;

        addRenderableWidget(Button.builder(Component.literal("✕"), b -> {
            data.getLines().remove(index);
            requestRebuild();
        }).bounds(x, y, 20, WIDGET_HEIGHT).tooltip(Tooltip.create(Component.translatable(KEY + "line.remove"))).build());
        x += 22;

        Button up = addRenderableWidget(Button.builder(Component.literal("↑"), b -> {
            HighwaySignLine previous = data.getLines().get(index - 1);
            data.getLines().set(index - 1, line);
            data.getLines().set(index, previous);
            requestRebuild();
        }).bounds(x, y, 20, WIDGET_HEIGHT).tooltip(Tooltip.create(Component.translatable(KEY + "line.move_up"))).build());
        up.active = index > 0;
    }

    /**
     * A button to pick an optional color of a line. Click opens the color picker, shift-click resets the color.
     * @param unsetLabel Shown on the button while no color is set.
     * @param current The current color or {@link HighwaySignLine#NO_COLOR}.
     * @param initial The color the picker starts with if no color is set.
     */
    private void addLineColorButton(int x, int y, String unsetLabel, String tooltipKey, int current, int initial, IntConsumer setter) {
        final boolean isSet = current != HighwaySignLine.NO_COLOR;
        Component colorName = isSet ? Component.literal(String.format("#%06X", current & 0xFFFFFF)) : Component.translatable(KEY + tooltipKey + ".none");
        addRenderableWidget(Button.builder(isSet ? Component.empty() : Component.literal(unsetLabel), b -> {
            if (hasShiftDown()) {
                setter.accept(HighwaySignLine.NO_COLOR);
                requestRebuild();
                return;
            }
            DLWindow.openWindow(mgr -> new DLColorPickerWindow(mgr, false, DLColor.fromInt(isSet ? current : initial), picked -> setter.accept(picked.getAsARGB())));
        }).bounds(x, y, SWATCH_SIZE, WIDGET_HEIGHT).tooltip(Tooltip.create(Component.translatable(KEY + tooltipKey, colorName))).build());
        if (isSet) {
            swatches.add(new Swatch(x, y, current, false));
        }
    }

    private static float nextScale(float current, int direction) {
        int index = 2;
        for (int i = 0; i < SCALES.length; i++) {
            if (Math.abs(SCALES[i] - current) < 0.01f) {
                index = i;
                break;
            }
        }
        return SCALES[Math.floorMod(index + direction, SCALES.length)];
    }

    private static String alignmentSymbol(ETextAlignment alignment) {
        return switch (alignment) {
            case LEFT -> "⇤";
            case RIGHT -> "⇥";
            default -> "↔";
        };
    }

    private static String arrowSideSymbol(ArrowSide side) {
        return switch (side) {
            case RIGHT -> "\u25A1\u25B6";
            case LEFT_OF_TEXT -> "\u25C0T";
            case RIGHT_OF_TEXT -> "T\u25B6";
            default -> "\u25C0\u25A1";
        };
    }

    private static String arrowSymbol(HighwayArrow arrow) {
        return switch (arrow) {
            case UP -> "↑";
            case UP_LEFT -> "↖";
            case UP_RIGHT -> "↗";
            case LEFT -> "←";
            case RIGHT -> "→";
            case DOWN -> "↓";
            case DOWN_LEFT -> "↙";
            case DOWN_RIGHT -> "↘";
            case EXIT_LEFT -> "↰";
            case EXIT_RIGHT -> "↱";
            default -> "-";
        };
    }

    /** Copies content and styles of the edited sign (including unsaved changes) to the clipboard. */
    private void copy() {
        HighwaySignClipboard.copy(data);
        setStatus(Component.translatable(KEY + "copied"), false);
    }

    /** Replaces everything in the editor with the copied sign. Applied to the world when "Done" is pressed. */
    private void paste() {
        HighwaySignClipboard.paste().ifPresentOrElse(pasted -> {
            data.set(pasted);
            if (data.getLines().isEmpty()) {
                data.getLines().add(new HighwaySignLine());
            }
            scrollOffset = 0;
            setStatus(Component.translatable(KEY + "pasted"), false);
        }, () -> setStatus(Component.translatable(KEY + "paste_invalid"), true));
    }

    private void setStatus(Component text, boolean error) {
        status = text;
        statusIsError = error;
        requestRebuild();
    }

    private void save() {
        if (waitingForServer) {
            return;
        }
        waitingForServer = true;
        waitTicks = 0;
        setStatus(Component.translatable(KEY + "saving"), false);
        ModNetworkManager.UPDATE_HIGHWAY_SIGN.send(NetworkDirection.toServer(), new HighwaySignPacket(anchor, data.copy()));
    }

    public BlockPos getAnchor() {
        return anchor;
    }

    /** Called on the client thread when the server answered the update request. */
    public void onServerResult(ResizeResult result) {
        if (!waitingForServer) {
            return;
        }
        waitingForServer = false;
        if (result == ResizeResult.OK) {
            onClose();
        } else {
            setStatus(Component.translatable(KEY + "error." + result.name().toLowerCase()), true);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (waitingForServer && ++waitTicks > SERVER_TIMEOUT_TICKS) {
            waitingForServer = false;
            setStatus(Component.translatable(KEY + "error.timeout"), true);
        }
        if (minecraft != null && minecraft.level != null && !(minecraft.level.getBlockEntity(anchor) instanceof HighwaySignBlockEntity)) {
            onClose();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= linesTop() && data.getLines().size() > visibleLines) {
            scrollOffset = Math.max(0, Math.min(data.getLines().size() - visibleLines, scrollOffset - (int)Math.signum(delta)));
            requestRebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (needsRebuild) {
            needsRebuild = false;
            rebuildWidgets();
        }

        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 5, TEXT_COLOR);
        renderPreview(graphics);

        for (Label label : labels) {
            graphics.drawString(font, label.text(), label.x(), label.y(), TEXT_COLOR);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        for (Swatch swatch : swatches) {
            graphics.fill(swatch.x() + 3, swatch.y() + 3, swatch.x() + SWATCH_SIZE - 3, swatch.y() + WIDGET_HEIGHT - 3, swatch.color());
            if (swatch.selected()) {
                graphics.renderOutline(swatch.x() + 1, swatch.y() + 1, SWATCH_SIZE - 2, WIDGET_HEIGHT - 2, 0xFFFFFFFF);
            }
        }

        if (data.getLines().size() > visibleLines) {
            Component scroll = Component.literal((scrollOffset + 1) + "-" + Math.min(data.getLines().size(), scrollOffset + visibleLines) + " / " + data.getLines().size());
            graphics.drawString(font, scroll, left() + CONTENT_WIDTH - font.width(scroll), 5, 0xFFA0A0A0);
        }

        if (status != null) {
            graphics.drawCenteredString(font, status, width / 2, height - 36, statusIsError ? STATUS_COLOR : TEXT_COLOR);
        }
    }

    /** Draws the sign using the same layout as the in-world renderer. */
    private void renderPreview(GuiGraphics graphics) {
        final float pixelsPerBlock = Math.min(48.0f, Math.min((float)CONTENT_WIDTH / data.getWidth(), (float)PREVIEW_HEIGHT / data.getHeight()));
        final int w = Math.round(data.getWidth() * pixelsPerBlock);
        final int h = Math.round(data.getHeight() * pixelsPerBlock);
        final int x = (width - w) / 2;
        final int y = PREVIEW_TOP + (PREVIEW_HEIGHT - h) / 2;
        final int textColor = data.getTextColor();

        graphics.fill(x, y, x + w, y + h, data.getBackgroundColor());
        if (data.hasBorder()) {
            int b = Math.max(1, Math.round(pixelsPerBlock / 16.0f));
            graphics.fill(x, y, x + w, y + b, textColor);
            graphics.fill(x, y + h - b, x + w, y + h, textColor);
            graphics.fill(x, y, x + b, y + h, textColor);
            graphics.fill(x + w - b, y, x + w, y + h, textColor);
        }

        HighwaySignLayout layout = HighwaySignLayout.compute(data, font::width, font.lineHeight);
        for (LineLayout line : layout.getLines()) {
            final int lineColor = line.color();
            if (line.hasHighlight()) {
                graphics.fill(
                    Math.round(x + line.boxX0() * pixelsPerBlock), Math.round(y + line.boxY0() * pixelsPerBlock),
                    Math.round(x + line.boxX1() * pixelsPerBlock), Math.round(y + line.boxY1() * pixelsPerBlock),
                    line.highlightColor()
                );
            }

            if (line.hasText()) {
                graphics.pose().pushPose();
                graphics.pose().translate(x + line.textX() * pixelsPerBlock, y + line.textY() * pixelsPerBlock, 0);
                float s = line.textScale() * pixelsPerBlock;
                graphics.pose().scale(s, s, 1);
                graphics.drawString(font, line.text(), 0, 0, lineColor, false);
                graphics.pose().popPose();
            }

            if (line.hasArrow()) {
                final int cell = 32;
                final int atlasWidth = cell * HighwayArrow.ATLAS_COLUMNS;
                final int atlasHeight = cell * HighwayArrow.ATLAS_ROWS;
                graphics.pose().pushPose();
                graphics.pose().translate(x + line.arrowX() * pixelsPerBlock, y + line.arrowY() * pixelsPerBlock, 0);
                float s = line.arrowSize() * pixelsPerBlock / cell;
                graphics.pose().scale(s, s, 1);
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(((textColor >> 16) & 0xFF) / 255.0f, ((textColor >> 8) & 0xFF) / 255.0f, (textColor & 0xFF) / 255.0f, 1.0f);
                graphics.blit(HighwaySignBlockEntityRenderer.ARROWS_TEXTURE, 0, 0, cell, cell, line.arrow().getU0() * atlasWidth, line.arrow().getV0() * atlasHeight, cell, cell, atlasWidth, atlasHeight);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                RenderSystem.disableBlend();
                graphics.pose().popPose();
            }
        }
    }
}
