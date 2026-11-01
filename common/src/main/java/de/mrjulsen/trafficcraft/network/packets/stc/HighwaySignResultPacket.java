package de.mrjulsen.trafficcraft.network.packets.stc;

import de.mrjulsen.mcdragonlib.data.DLStatus;
import de.mrjulsen.mcdragonlib.network.NetworkPacketContext;
import de.mrjulsen.mcdragonlib.network.NetworkPacketData;
import de.mrjulsen.mcdragonlib.util.NbtUtils;
import de.mrjulsen.trafficcraft.block.HighwaySignBlock.ResizeResult;
import de.mrjulsen.trafficcraft.client.ClientWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Tells the client whether an update of a highway sign was applied.
 */
public class HighwaySignResultPacket extends NetworkPacketData {

    private static final String NBT_POS = "Pos";
    private static final String NBT_RESULT = "Result";

    private BlockPos pos;
    private ResizeResult result = ResizeResult.INVALID;

    public HighwaySignResultPacket(DLStatus status) {
        super(status);
    }

    public HighwaySignResultPacket(BlockPos pos, ResizeResult result) {
        super(DLStatus.OK);
        this.pos = pos;
        this.result = result;
    }

    @Override
    protected void write(CompoundTag nbt) {
        NbtUtils.putNbtPos(nbt, NBT_POS, pos);
        nbt.putByte(NBT_RESULT, (byte)result.ordinal());
    }

    @Override
    protected void read(CompoundTag nbt) {
        this.pos = NbtUtils.getNbtBlockPos(nbt, NBT_POS);
        int index = nbt.getByte(NBT_RESULT);
        this.result = index >= 0 && index < ResizeResult.values().length ? ResizeResult.values()[index] : ResizeResult.INVALID;
    }

    public static void handle(HighwaySignResultPacket packet, NetworkPacketContext context) {
        ClientWrapper.handleHighwaySignResult(packet.pos, packet.result);
    }
}
