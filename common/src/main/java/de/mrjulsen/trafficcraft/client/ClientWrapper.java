package de.mrjulsen.trafficcraft.client;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import de.mrjulsen.mcdragonlib.block.DLWritableSignBlockEntity;
import de.mrjulsen.mcdragonlib.client.gui.builtin.WritableSignScreen;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import de.mrjulsen.trafficcraft.block.TownSignBlock;
import de.mrjulsen.trafficcraft.block.HighwaySignBlock;
import de.mrjulsen.trafficcraft.block.entity.HighwaySignBlockEntity;
import de.mrjulsen.trafficcraft.block.entity.TownSignBlockEntity;
import de.mrjulsen.trafficcraft.client.screen.HighwaySignEditorScreen;
import de.mrjulsen.trafficcraft.client.screen.TrafficLightConfigScreen;
import de.mrjulsen.trafficcraft.client.screen.TrafficSignPatternSelectionScreen;
import de.mrjulsen.trafficcraft.client.screen.TrafficSignWorkbenchGui;
import de.mrjulsen.trafficcraft.client.screen.PaintBrushScreen;
import de.mrjulsen.trafficcraft.client.screen.RoadConstructionToolScreen;
import de.mrjulsen.trafficcraft.client.screen.StreetLampScheduleScreen;
import de.mrjulsen.trafficcraft.client.screen.TownSignScreen;
import de.mrjulsen.trafficcraft.client.screen.TrafficLightControllerScreen;
import de.mrjulsen.trafficcraft.data.PaintColor;
import de.mrjulsen.trafficcraft.init.ClientInit;
import de.mrjulsen.trafficcraft.network.packets.stc.TrafficSignWorkbenchUpdateClientPacket;
import de.mrjulsen.trafficcraft.util.ETimeFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ClientWrapper {


    private static final Queue<Runnable> afterRenderTasks = new ConcurrentLinkedQueue<>();

    public static final void submitTaskAfterRenderFrame(Runnable task) {
        afterRenderTasks.add(task);
    }

    public static void runAllScheduledRenderTasks() {
        while (!afterRenderTasks.isEmpty()) {
            afterRenderTasks.poll().run();
        }
    }

    public static void showPaintBrushScreen(int pattern, int paint, PaintColor color) {
        DLWindow.openWindow(mgr -> new PaintBrushScreen(mgr, pattern, paint, color));
    }

    public static void showSignPatternSelectionScreen(ItemStack stack) {        
        DLWindow.openWindow(mgr -> new TrafficSignPatternSelectionScreen(mgr, stack));
    }

    public static void showStreetLampScheduleScreen(int turnOnTime, int turnOfftime, ETimeFormat format) {        
        DLWindow.openWindow(mgr -> new StreetLampScheduleScreen(mgr, turnOnTime, turnOfftime, format));
    }

    public static void showTrafficLightConfigScreen(Level level, BlockPos pos) {
        DLWindow.openWindow(mgr -> new TrafficLightConfigScreen(mgr, level, pos));
    }

    public static void showTrafficLightControllerScreen(BlockPos pos, Level level) {
        DLWindow.openWindow(mgr -> new TrafficLightControllerScreen(mgr, pos, level));
    }

    public static void showWritableSignScreen(DLWritableSignBlockEntity pSign) {
        Minecraft.getInstance().setScreen(new WritableSignScreen(pSign));
    }

    public static void showTownSignScreen(TownSignBlockEntity pSign, TownSignBlock.ETownSignSide side) {
        Minecraft.getInstance().setScreen(new TownSignScreen(pSign, side));
    }

    public static void showHighwaySignEditor(HighwaySignBlockEntity blockEntity) {
        Minecraft.getInstance().setScreen(new HighwaySignEditorScreen(blockEntity));
    }

    /** Called when the server answered an update request of the highway sign editor. May be called from any thread. */
    public static void handleHighwaySignResult(BlockPos anchor, HighwaySignBlock.ResizeResult result) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof HighwaySignEditorScreen screen && screen.getAnchor().equals(anchor)) {
                screen.onServerResult(result);
            }
        });
    }

    /** Rebuilds the chunk meshes of all cells of a highway sign, e.g. after its colors changed. */
    @SuppressWarnings("resource")
    public static void markHighwaySignDirty(Level level, BlockPos anchor, Direction facing, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer == null || mc.level == null || mc.level != level) {
            return;
        }
        BlockPos far = HighwaySignBlock.cellPos(anchor, facing, width - 1, height - 1);
        mc.levelRenderer.setBlocksDirty(
            Math.min(anchor.getX(), far.getX()), anchor.getY(), Math.min(anchor.getZ(), far.getZ()),
            Math.max(anchor.getX(), far.getX()), far.getY(), Math.max(anchor.getZ(), far.getZ())
        );
    }

    
    @SuppressWarnings("resource")
    public static void handleTrafficSignWorkbenchUpdateClientPacket(TrafficSignWorkbenchUpdateClientPacket packet) {
        if (Minecraft.getInstance().screen instanceof TrafficSignWorkbenchGui screen) {
            //screen.updatePreview();
        }
    }

    public static void showRoadConstructionToolScreen(ItemStack itemstack, int blocksCount, int slopesCount) {
        DLWindow.openWindow(mgr -> new RoadConstructionToolScreen(mgr, itemstack, blocksCount, slopesCount));
    }

    
	public static DynamicTexture getShapeTexture(int index) {
		return ClientInit.SHAPE_TEXTURES[index];
	}

    public static int getShapeTextureId(int index) {
        return getShapeTexture(index).getId();
    }
}
