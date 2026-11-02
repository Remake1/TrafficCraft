package de.mrjulsen.trafficcraft.client;

import java.util.Optional;

import de.mrjulsen.trafficcraft.data.HighwaySignData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

/**
 * Copies highway sign content to the system clipboard (as tagged SNBT text), so it survives restarts and can
 * be pasted into other signs, worlds or shared with other players.
 */
public final class HighwaySignClipboard {

    private static final String PREFIX = "TrafficCraft:HighwaySign:";
    /** Upper bound for clipboard text we try to parse. A full sign is far below this. */
    private static final int MAX_LENGTH = 32768;

    private HighwaySignClipboard() {}

    public static void copy(HighwaySignData data) {
        Minecraft.getInstance().keyboardHandler.setClipboard(PREFIX + data.toNbt().toString());
    }

    /** @return The sign content in the clipboard, or empty if the clipboard does not contain a highway sign. */
    public static Optional<HighwaySignData> paste() {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (text == null) {
            return Optional.empty();
        }
        text = text.trim();
        if (!text.startsWith(PREFIX) || text.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        try {
            CompoundTag nbt = TagParser.parseTag(text.substring(PREFIX.length()));
            return Optional.of(HighwaySignData.fromNbt(nbt));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
