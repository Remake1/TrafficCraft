package de.mrjulsen.trafficcraft.block;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import de.mrjulsen.trafficcraft.block.data.ITrafficPostLike;
import de.mrjulsen.trafficcraft.block.entity.HighwaySignBlockEntity;
import de.mrjulsen.trafficcraft.client.ClientWrapper;
import de.mrjulsen.trafficcraft.data.HighwaySignData;
import de.mrjulsen.trafficcraft.item.BrushItem;
import de.mrjulsen.trafficcraft.registry.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A multi-block highway sign panel (1x1 up to 4x3 blocks) which is mounted on traffic sign posts.
 * <p>
 * Every cell of a sign is an instance of this block. The cell at the bottom left (seen from the front) is the
 * <i>anchor</i> ({@code part_x=0, part_y=0}) and is the only cell holding a block entity, which stores the
 * content of the whole sign. All other cells find the anchor through their {@link #PART_X} and {@link #PART_Y}
 * properties.
 * </p>
 * {@link #FACING} is the direction the front of the sign faces. The post is located behind the sign.
 */
public class HighwaySignBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, HighwaySignData.MAX_WIDTH - 1);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, HighwaySignData.MAX_HEIGHT - 1);
    public static final BooleanProperty EDGE_TOP = BooleanProperty.create("edge_top");
    public static final BooleanProperty EDGE_BOTTOM = BooleanProperty.create("edge_bottom");
    public static final BooleanProperty EDGE_LEFT = BooleanProperty.create("edge_left");
    public static final BooleanProperty EDGE_RIGHT = BooleanProperty.create("edge_right");
    public static final BooleanProperty MOUNTED = BooleanProperty.create("mounted");

    /** Thickness of the panel in pixels. */
    public static final double PANEL_THICKNESS = 1.5D;

    private static final VoxelShape SHAPE_NORTH = Block.box(0, 0, 16 - PANEL_THICKNESS, 16, 16, 16);
    private static final VoxelShape SHAPE_SOUTH = Block.box(0, 0, 0, 16, 16, PANEL_THICKNESS);
    private static final VoxelShape SHAPE_EAST = Block.box(0, 0, 0, PANEL_THICKNESS, 16, 16);
    private static final VoxelShape SHAPE_WEST = Block.box(16 - PANEL_THICKNESS, 0, 0, 16, 16, 16);

    public enum ResizeResult {
        OK,
        NO_SPACE,
        NOT_MOUNTED,
        INVALID;
    }

    /** Suppresses the "remove the whole sign" logic while the sign itself changes its cells. Server thread only. */
    private static int structureChangeDepth = 0;

    public HighwaySignBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()
            .pushReaction(PushReaction.BLOCK)
            .sound(SoundType.LANTERN)
        );

        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(WATERLOGGED, false)
            .setValue(PART_X, 0)
            .setValue(PART_Y, 0)
            .setValue(EDGE_TOP, true)
            .setValue(EDGE_BOTTOM, true)
            .setValue(EDGE_LEFT, true)
            .setValue(EDGE_RIGHT, true)
            .setValue(MOUNTED, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(FACING, WATERLOGGED, PART_X, PART_Y, EDGE_TOP, EDGE_BOTTOM, EDGE_LEFT, EDGE_RIGHT, MOUNTED);
    }

    /* GEOMETRY HELPERS */

    /** @return The horizontal direction pointing to the right side of the sign, seen from the front. */
    public static Direction rightOf(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** @return The position of the cell at {@code (x, y)} of the sign with the given anchor. */
    public static BlockPos cellPos(BlockPos anchor, Direction facing, int x, int y) {
        return anchor.relative(rightOf(facing), x).above(y);
    }

    /** @return The position of the anchor of the sign the given cell belongs to. */
    public static BlockPos anchorOf(BlockState state, BlockPos pos) {
        return pos.relative(rightOf(state.getValue(FACING)), -state.getValue(PART_X)).below(state.getValue(PART_Y));
    }

    public static boolean isAnchor(BlockState state) {
        return state.getValue(PART_X) == 0 && state.getValue(PART_Y) == 0;
    }

    /** @return {@code true} if a post-like block that accepts panels is located directly behind the given position. */
    public static boolean isMountedAt(BlockGetter level, BlockPos pos, Direction facing) {
        BlockPos behind = pos.relative(facing.getOpposite());
        BlockState behindState = level.getBlockState(behind);
        return behindState.getBlock() instanceof ITrafficPostLike postLike && postLike.canMountPanel(behindState, facing);
    }

    /** @return {@code true} if the block at {@code pos} is a cell of the sign with the given anchor and facing. */
    private boolean isCellOf(BlockGetter level, BlockPos pos, BlockPos anchor, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.is(this) && state.getValue(FACING) == facing && anchorOf(state, pos).equals(anchor);
    }

    private static int[] getSize(BlockGetter level, BlockPos anchor) {
        if (level.getBlockEntity(anchor) instanceof HighwaySignBlockEntity be) {
            return new int[] { be.getData().getWidth(), be.getData().getHeight() };
        }
        return new int[] { 1, 1 };
    }

    private static BlockState emptyStateFor(BlockState state) {
        return state.hasProperty(WATERLOGGED) && state.getValue(WATERLOGGED) ? Fluids.WATER.defaultFluidState().createLegacyBlock() : Blocks.AIR.defaultBlockState();
    }

    /* SHAPE */

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return switch (pState.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean isPathfindable(BlockState pState, BlockGetter pLevel, BlockPos pPos, PathComputationType pType) {
        return false;
    }

    /* PLACEMENT */

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        Level level = pContext.getLevel();
        BlockPos pos = pContext.getClickedPos();
        boolean water = level.getFluidState(pos).getType() == Fluids.WATER;

        List<Direction> candidates = new ArrayList<>();
        if (pContext.getClickedFace().getAxis().isHorizontal()) {
            candidates.add(pContext.getClickedFace());
        }
        for (Direction direction : pContext.getNearestLookingDirections()) {
            if (direction.getAxis().isHorizontal() && !candidates.contains(direction.getOpposite())) {
                candidates.add(direction.getOpposite());
            }
        }

        for (Direction facing : candidates) {
            if (isMountedAt(level, pos, facing)) {
                return defaultBlockState()
                    .setValue(FACING, facing)
                    .setValue(WATERLOGGED, water)
                    .setValue(MOUNTED, true);
            }
        }
        return null;
    }

    @Override
    public BlockState rotate(BlockState pState, Rotation pRotation) {
        return pState.setValue(FACING, pRotation.rotate(pState.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState pState, Mirror pMirror) {
        return pState.rotate(pMirror.getRotation(pState.getValue(FACING)));
    }

    /* SURVIVAL */

    @Override
    public BlockState updateShape(BlockState pState, Direction pDirection, BlockState pNeighborState, LevelAccessor pLevel, BlockPos pPos, BlockPos pNeighborPos) {
        if (pState.getValue(WATERLOGGED)) {
            pLevel.scheduleTick(pPos, Fluids.WATER, Fluids.WATER.getTickDelay(pLevel));
        }

        Direction facing = pState.getValue(FACING);
        if (pDirection == facing.getOpposite()) {
            boolean mounted = isMountedAt(pLevel, pPos, facing);
            if (mounted != pState.getValue(MOUNTED)) {
                pState = pState.setValue(MOUNTED, mounted);
                if (!mounted) {
                    // Let the anchor check whether the sign is still held by at least one post.
                    pLevel.scheduleTick(anchorOf(pState, pPos), this, 1);
                }
            }
        }
        return pState;
    }

    @Override
    public void tick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRandom) {
        if (!isAnchor(pState)) {
            return;
        }

        Direction facing = pState.getValue(FACING);
        int[] size = getSize(pLevel, pPos);
        for (int x = 0; x < size[0]; x++) {
            for (int y = 0; y < size[1]; y++) {
                BlockPos cell = cellPos(pPos, facing, x, y);
                if (isCellOf(pLevel, cell, pPos, facing) && pLevel.getBlockState(cell).getValue(MOUNTED)) {
                    return;
                }
            }
        }
        pLevel.destroyBlock(pPos, true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public FluidState getFluidState(BlockState pState) {
        return pState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(pState);
    }

    /* BREAKING */

    @Override
    public void playerWillDestroy(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer) {
        if (!pLevel.isClientSide && !isAnchor(pState)) {
            BlockPos anchor = anchorOf(pState, pPos);
            if (isCellOf(pLevel, anchor, anchor, pState.getValue(FACING))) {
                // The anchor holds the loot table entry, so break it instead (it removes all other cells).
                boolean drop = !pPlayer.isCreative() && pPlayer.hasCorrectToolForDrops(pState);
                pLevel.destroyBlock(anchor, drop, pPlayer);
            }
        }
        super.playerWillDestroy(pLevel, pPos, pState, pPlayer);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pMovedByPiston) {
        if (!pLevel.isClientSide && !pState.is(pNewState.getBlock()) && structureChangeDepth <= 0) {
            Direction facing = pState.getValue(FACING);
            if (isAnchor(pState)) {
                // Remove all other cells without drops.
                int[] size = getSize(pLevel, pPos);
                structureChangeDepth++;
                try {
                    for (int x = 0; x < size[0]; x++) {
                        for (int y = 0; y < size[1]; y++) {
                            BlockPos cell = cellPos(pPos, facing, x, y);
                            if ((x != 0 || y != 0) && isCellOf(pLevel, cell, pPos, facing)) {
                                pLevel.setBlock(cell, emptyStateFor(pLevel.getBlockState(cell)), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                            }
                        }
                    }
                } finally {
                    structureChangeDepth--;
                }
            } else {
                // A cell was removed by something other than a player (explosion, command, ...). Remove the whole sign.
                BlockPos anchor = anchorOf(pState, pPos);
                if (isCellOf(pLevel, anchor, anchor, facing)) {
                    pLevel.destroyBlock(anchor, true);
                }
            }
        }
        super.onRemove(pState, pLevel, pPos, pNewState, pMovedByPiston);
    }

    /* RESIZE */

    /**
     * Changes the size and border of the sign with the given anchor. Must be called on the server thread.
     * The block entity data is not modified by this method.
     */
    public ResizeResult applyLayout(ServerLevel level, BlockPos anchor, int newWidth, int newHeight, boolean border) {
        BlockState anchorState = level.getBlockState(anchor);
        if (!anchorState.is(this) || !isAnchor(anchorState) || !(level.getBlockEntity(anchor) instanceof HighwaySignBlockEntity be)) {
            return ResizeResult.INVALID;
        }

        Direction facing = anchorState.getValue(FACING);
        int oldWidth = be.getData().getWidth();
        int oldHeight = be.getData().getHeight();

        // 1. Check space and support of the new configuration.
        boolean mounted = false;
        for (int x = 0; x < newWidth; x++) {
            for (int y = 0; y < newHeight; y++) {
                BlockPos cell = cellPos(anchor, facing, x, y);
                boolean existing = x < oldWidth && y < oldHeight && isCellOf(level, cell, anchor, facing);
                if (!existing && (level.isOutsideBuildHeight(cell) || !level.getBlockState(cell).canBeReplaced())) {
                    return ResizeResult.NO_SPACE;
                }
                mounted |= isMountedAt(level, cell, facing);
            }
        }
        if (!mounted) {
            return ResizeResult.NOT_MOUNTED;
        }

        structureChangeDepth++;
        try {
            // 2. Remove cells outside of the new size.
            for (int x = 0; x < oldWidth; x++) {
                for (int y = 0; y < oldHeight; y++) {
                    if (x < newWidth && y < newHeight) continue;
                    BlockPos cell = cellPos(anchor, facing, x, y);
                    if (isCellOf(level, cell, anchor, facing)) {
                        level.setBlock(cell, emptyStateFor(level.getBlockState(cell)), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }

            // 3. Place new cells and update edges of all cells.
            for (int x = 0; x < newWidth; x++) {
                for (int y = 0; y < newHeight; y++) {
                    BlockPos cell = cellPos(anchor, facing, x, y);
                    BlockState current = level.getBlockState(cell);
                    boolean water = current.is(this) ? current.getValue(WATERLOGGED) : level.getFluidState(cell).getType() == Fluids.WATER;
                    BlockState newState = defaultBlockState()
                        .setValue(FACING, facing)
                        .setValue(WATERLOGGED, water)
                        .setValue(PART_X, x)
                        .setValue(PART_Y, y)
                        .setValue(EDGE_LEFT, border && x == 0)
                        .setValue(EDGE_RIGHT, border && x == newWidth - 1)
                        .setValue(EDGE_BOTTOM, border && y == 0)
                        .setValue(EDGE_TOP, border && y == newHeight - 1)
                        .setValue(MOUNTED, isMountedAt(level, cell, facing));
                    if (current != newState) {
                        level.setBlock(cell, newState, Block.UPDATE_ALL);
                    }
                }
            }
        } finally {
            structureChangeDepth--;
        }
        return ResizeResult.OK;
    }

    /* INTERACTION */

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        ItemStack stack = pPlayer.getItemInHand(pHand);
        if (stack.getItem() instanceof BrushItem || !stack.is(ModItemTags.WRENCHES) || pPlayer.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (pLevel.isClientSide && pLevel.getBlockEntity(anchorOf(pState, pPos)) instanceof HighwaySignBlockEntity blockEntity) {
            ClientWrapper.showHighwaySignEditor(blockEntity);
        }
        return InteractionResult.sidedSuccess(pLevel.isClientSide);
    }

    /* BLOCK ENTITY */

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return isAnchor(pState) ? new HighwaySignBlockEntity(pPos, pState) : null;
    }
}
