package de.mrjulsen.trafficcraft.network.packets.cts;

import de.mrjulsen.mcdragonlib.data.DLStatus;
import de.mrjulsen.mcdragonlib.network.NetworkDirection;
import de.mrjulsen.mcdragonlib.network.NetworkPacketContext;
import de.mrjulsen.mcdragonlib.network.NetworkPacketData;
import de.mrjulsen.mcdragonlib.util.NbtUtils;
import de.mrjulsen.trafficcraft.block.HighwaySignBlock;
import de.mrjulsen.trafficcraft.block.HighwaySignBlock.ResizeResult;
import de.mrjulsen.trafficcraft.block.entity.HighwaySignBlockEntity;
import de.mrjulsen.trafficcraft.data.HighwaySignData;
import de.mrjulsen.trafficcraft.network.packets.stc.HighwaySignResultPacket;
import de.mrjulsen.trafficcraft.registry.ModNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Sent by the highway sign editor to apply new content (and size) to a highway sign.
 * The server answers with a {@link HighwaySignResultPacket}.
 * <p>
 * This is deliberately not a request/response packet: the world has to be modified on the server thread, and
 * blocking the network handler while waiting for the server thread can stall the server (and time out).
 * </p>
 */
public class HighwaySignPacket extends NetworkPacketData {

    /** Max distance between the player and the sign anchor. */
    private static final double MAX_DISTANCE = 12.0D;

    private static final String NBT_POS = "Pos";
    private static final String NBT_DATA = "Data";

    private BlockPos pos;
    private HighwaySignData data;

    public HighwaySignPacket(DLStatus status) {
        super(status);
    }

    public HighwaySignPacket(BlockPos anchor, HighwaySignData data) {
        super(DLStatus.OK);
        this.pos = anchor;
        this.data = data;
    }

    @Override
    protected void write(CompoundTag nbt) {
        NbtUtils.putNbtPos(nbt, NBT_POS, pos);
        nbt.put(NBT_DATA, data.toNbt());
    }

    @Override
    protected void read(CompoundTag nbt) {
        this.pos = NbtUtils.getNbtBlockPos(nbt, NBT_POS);
        this.data = HighwaySignData.fromNbt(nbt.getCompound(NBT_DATA));
    }

    public static void handle(HighwaySignPacket packet, NetworkPacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer sender) || packet.pos == null || packet.data == null) {
            return;
        }
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return;
        }

        // Runs on the server thread (immediately, if we are already on it). Never blocks the calling thread.
        server.execute(() -> {
            ResizeResult result = apply(sender, packet.pos, packet.data);
            ModNetworkManager.HIGHWAY_SIGN_RESULT.send(NetworkDirection.toPlayer(sender), new HighwaySignResultPacket(packet.pos, result));
        });
    }

    private static ResizeResult apply(ServerPlayer sender, BlockPos pos, HighwaySignData data) {
        if (!(sender.level() instanceof ServerLevel level) || !level.isLoaded(pos) || sender.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE * MAX_DISTANCE) {
            return ResizeResult.INVALID;
        }

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HighwaySignBlock block) || !HighwaySignBlock.isAnchor(state) || !(level.getBlockEntity(pos) instanceof HighwaySignBlockEntity blockEntity)) {
            return ResizeResult.INVALID;
        }

        data.sanitize();
        HighwaySignData current = blockEntity.getData();
        if (current.getWidth() != data.getWidth() || current.getHeight() != data.getHeight() || current.hasBorder() != data.hasBorder()) {
            ResizeResult result = block.applyLayout(level, pos, data.getWidth(), data.getHeight(), data.hasBorder());
            if (result != ResizeResult.OK) {
                return result;
            }
        }
        blockEntity.setData(data);
        return ResizeResult.OK;
    }
}
