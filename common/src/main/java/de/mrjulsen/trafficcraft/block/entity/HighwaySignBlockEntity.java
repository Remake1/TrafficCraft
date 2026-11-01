package de.mrjulsen.trafficcraft.block.entity;

import java.util.function.ToIntFunction;

import de.mrjulsen.mcdragonlib.block.DLSyncedBlockEntity;
import de.mrjulsen.trafficcraft.block.HighwaySignBlock;
import de.mrjulsen.trafficcraft.client.ClientWrapper;
import de.mrjulsen.trafficcraft.data.HighwaySignData;
import de.mrjulsen.trafficcraft.data.HighwaySignLayout;
import de.mrjulsen.trafficcraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Stores the content of a highway sign. Only exists on the anchor cell of a sign.
 */
public class HighwaySignBlockEntity extends DLSyncedBlockEntity {

    private static final String NBT_DATA = "HighwaySign";

    private HighwaySignData data = new HighwaySignData();

    /** Client-side render cache. Reset whenever the data changes. */
    private HighwaySignLayout layout;

    public HighwaySignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HIGHWAY_SIGN_BLOCK_ENTITY.get(), pos, state);
    }

    public HighwaySignData getData() {
        return data;
    }

    /** Replaces the content of this sign and syncs it to all clients. Server side only. */
    public void setData(HighwaySignData data) {
        this.data = data;
        this.layout = null;
        notifyUpdate();
    }

    /** @return The cached layout of the sign content. Recomputed only after the data has changed. */
    public HighwaySignLayout getLayout(ToIntFunction<String> textWidth, int lineHeight) {
        if (layout == null) {
            layout = HighwaySignLayout.compute(data, textWidth, lineHeight);
        }
        return layout;
    }

    @Override
    public void load(CompoundTag compound) {
        super.load(compound);
        if (compound.contains(NBT_DATA)) {
            data = HighwaySignData.fromNbt(compound.getCompound(NBT_DATA));
        }
        layout = null;

        if (level != null && level.isClientSide) {
            // The background color is part of the baked block model (tinted), so the chunk has to be rebuilt.
            ClientWrapper.markHighwaySignDirty(level, getBlockPos(), getFacing(), data.getWidth(), data.getHeight());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        tag.put(NBT_DATA, data.toNbt());
        super.saveAdditional(tag);
    }

    public Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(HighwaySignBlock.FACING) ? state.getValue(HighwaySignBlock.FACING) : Direction.NORTH;
    }

    /** @return A box containing all cells of this sign. */
    public AABB getSignBounds() {
        BlockPos far = HighwaySignBlock.cellPos(getBlockPos(), getFacing(), data.getWidth() - 1, data.getHeight() - 1);
        return new AABB(getBlockPos()).minmax(new AABB(far));
    }

    /**
     * Used by Forge ({@code IForgeBlockEntity#getRenderBoundingBox}) to frustum-cull the block entity renderer.
     * Not annotated with {@code @Override}, because the method does not exist in vanilla.
     */
    public AABB getRenderBoundingBox() {
        return getSignBounds();
    }
}
