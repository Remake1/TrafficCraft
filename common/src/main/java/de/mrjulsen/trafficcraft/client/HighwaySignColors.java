package de.mrjulsen.trafficcraft.client;

import de.mrjulsen.trafficcraft.block.HighwaySignBlock;
import de.mrjulsen.trafficcraft.block.entity.HighwaySignBlockEntity;
import de.mrjulsen.trafficcraft.data.HighwaySignColorPresets;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tints the baked highway sign model. Tint index 0 is the background, tint index 1 the border (text color).
 * Every cell reads the colors from the block entity of its anchor.
 */
public class HighwaySignColors {

    public static final int TINT_BACKGROUND = 0;
    public static final int TINT_BORDER = 1;

    public static class Block implements BlockColor {
        @Override
        public int getColor(BlockState pState, BlockAndTintGetter pLevel, BlockPos pPos, int pTintIndex) {
            if (pLevel != null && pPos != null && pState.getBlock() instanceof HighwaySignBlock
                && pLevel.getBlockEntity(HighwaySignBlock.anchorOf(pState, pPos)) instanceof HighwaySignBlockEntity blockEntity) {
                return pTintIndex == TINT_BORDER ? blockEntity.getData().getTextColor() : blockEntity.getData().getBackgroundColor();
            }
            return defaultColor(pTintIndex);
        }
    }

    public static class Item implements ItemColor {
        @Override
        public int getColor(ItemStack pStack, int pTintIndex) {
            return defaultColor(pTintIndex);
        }
    }

    private static int defaultColor(int tintIndex) {
        return tintIndex == TINT_BORDER ? HighwaySignColorPresets.WHITE : HighwaySignColorPresets.GREEN;
    }
}
