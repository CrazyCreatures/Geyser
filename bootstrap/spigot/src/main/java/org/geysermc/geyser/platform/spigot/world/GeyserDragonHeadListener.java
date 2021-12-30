/*
 * Copyright (c) 2019-2021 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.platform.spigot.world;

import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.protocol.bedrock.packet.BlockEntityDataPacket;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.GeyserSession;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GeyserDragonHeadListener implements Listener {
    private static final Map<BlockFace, Float> BLOCK_FACE_TO_ROTATION;
    static {
        BLOCK_FACE_TO_ROTATION = new EnumMap<>(BlockFace.class);
        BLOCK_FACE_TO_ROTATION.put(BlockFace.SOUTH, 0f);
        BLOCK_FACE_TO_ROTATION.put(BlockFace.SOUTH_SOUTH_WEST, 22.5f);
        BLOCK_FACE_TO_ROTATION.put(BlockFace.SOUTH_WEST, 2 * 22.5f);
        BLOCK_FACE_TO_ROTATION.put(BlockFace.WEST_SOUTH_WEST, 3 * 22.5f);
        BLOCK_FACE_TO_ROTATION.put(BlockFace.WEST, 4 * 22.5f);
        BLOCK_FACE_TO_ROTATION.put(BlockFace.WEST_NORTH_WEST, 5 * 22.5f);
        BLOCK_FACE_TO_ROTATION.put(BlockFace.NORTH_WEST, 6 * 22.5f);
        BLOCK_FACE_TO_ROTATION.put(BlockFace.NORTH_NORTH_WEST, 7 * 22.5f);
        BLOCK_FACE_TO_ROTATION.put(BlockFace.NORTH, 8 * 22.5f);
        BLOCK_FACE_TO_ROTATION.put(BlockFace.NORTH_NORTH_EAST, 9 * 22.5f);
        BLOCK_FACE_TO_ROTATION.put(BlockFace.NORTH_EAST, 10 * 22.5f);
        BLOCK_FACE_TO_ROTATION.put(BlockFace.EAST_NORTH_EAST, 11 * 22.5f);
        BLOCK_FACE_TO_ROTATION.put(BlockFace.EAST, 12 * 22.5f);
        BLOCK_FACE_TO_ROTATION.put(BlockFace.EAST_SOUTH_EAST, 13 * 22.5f);
        BLOCK_FACE_TO_ROTATION.put(BlockFace.SOUTH_EAST, 14 * 22.5f);
        BLOCK_FACE_TO_ROTATION.put(BlockFace.SOUTH_SOUTH_EAST, 15 * 22.5f);
    }

    @Getter
    private final Map<Location, DragonHeadInformation> dragonHeads = new ConcurrentHashMap<>();
    private final GeyserImpl geyser;

    public GeyserDragonHeadListener(Plugin plugin, GeyserImpl geyser) {
        this.geyser = geyser;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::onTick, 0, 1);
        // Catch chunks already loaded
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                cacheDragonHeads(chunk);
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        cacheDragonHeads(event.getChunk());
    }

    private void cacheDragonHeads(Chunk chunk) {
        for (BlockState blockState : chunk.getTileEntities(false)) {
            Block block = blockState.getBlock();
            Material material = block.getType();
            if (material != Material.DRAGON_HEAD && material != Material.DRAGON_WALL_HEAD) {
                continue;
            }
            this.dragonHeads.put(blockState.getLocation(), new DragonHeadInformation(block, block.isBlockIndirectlyPowered()));
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        World world = event.getWorld();
        Chunk chunk = event.getChunk();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        Iterator<Map.Entry<Location, DragonHeadInformation>> it = this.dragonHeads.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Location, DragonHeadInformation> entry = it.next();
            Location location = entry.getKey();
            if (location.getWorld() == world && location.getBlockX() >> 4 == chunkX && location.getBlockZ() >> 4 == chunkZ) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled() || !event.canBuild()) {
            return;
        }
        Block block = event.getBlockPlaced();
        // Clients will request this when they get the block entity data packet
        this.dragonHeads.put(block.getLocation(), new DragonHeadInformation(block, block.isBlockIndirectlyPowered()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }
        this.dragonHeads.remove(event.getBlock().getLocation());
    }

    public void onTick() {
        if (this.dragonHeads.isEmpty()) {
            return;
        }

        for (DragonHeadInformation info : this.dragonHeads.values()) {
            boolean newPowered = info.getBlock().isBlockIndirectlyPowered();
            if (info.isPowered() != newPowered) {
                info.setPowered(newPowered);
                updateBedrockClients(info);
            }
        }
    }

    private void updateBedrockClients(DragonHeadInformation info) {
        Block block = info.getBlock();
        Location location = block.getLocation();
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        float rotation;
        if (block.getBlockData() instanceof Rotatable rotatable) {
            rotation = BLOCK_FACE_TO_ROTATION.get(rotatable.getRotation());
        } else {
            rotation = 0f;
        }

        NbtMap nbt = NbtMap.builder()
                .putInt("x", x)
                .putInt("y", y)
                .putInt("z", z)
                .putByte("SkullType", (byte) 5) // Dragon head
                .putFloat("Rotation", rotation)
                .putBoolean("MouthMoving", info.isPowered())
                .build();
        Vector3i vector = Vector3i.from(x, y, z);

        for (Map.Entry<UUID, GeyserSession> entry : geyser.getSessionManager().getSessions().entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.getWorld().equals(world)) {
                continue;
            }
            GeyserSession session = entry.getValue();

            int dX = Math.abs(location.getBlockX() - player.getLocation().getBlockX()) >> 4;
            int dZ = Math.abs(location.getBlockZ() - player.getLocation().getBlockZ()) >> 4;
            if ((dX * dX + dZ * dZ) > session.getRenderDistance() * session.getRenderDistance()) {
                // Ignore heads outside the player's render distance
                continue;
            }

            BlockEntityDataPacket packet = new BlockEntityDataPacket();
            packet.setBlockPosition(vector);
            packet.setData(nbt);
            session.sendUpstreamPacket(packet);
        }
    }

    @Getter
    public static final class DragonHeadInformation {
        private final Block block;
        @Setter
        private boolean isPowered;

        public DragonHeadInformation(Block block, boolean isPowered) {
            this.block = block;
            this.isPowered = isPowered;
        }
    }
}