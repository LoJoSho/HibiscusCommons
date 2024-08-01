package me.lojosho.hibiscuscommons.nms.v1_20_R4;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftEquipmentSlot;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftEntityType;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.scoreboard.CraftScoreboard;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class NMSHandler implements me.lojosho.hibiscuscommons.nms.NMSHandler {


    @Override
    public int getNextEntityId() {
        return net.minecraft.world.entity.Entity.nextEntityId();
    }

    @Override
    public org.bukkit.entity.Entity getEntity(int entityId) {
        net.minecraft.world.entity.Entity entity = getNMSEntity(entityId);
        if (entity == null) return null;
        return entity.getBukkitEntity();
    }

    private net.minecraft.world.entity.Entity getNMSEntity(int entityId) {
        for (ServerLevel world : ((CraftServer) Bukkit.getServer()).getHandle().getServer().getAllLevels()) {
            net.minecraft.world.entity.Entity entity = world.getEntity(entityId);
            if (entity == null) continue;
            return entity;
        }
        return null;
    }

    @Override
    public void equipmentSlotUpdate(
            int entityId,
            org.bukkit.inventory.EquipmentSlot slot,
            ItemStack item,
            List<Player> sendTo
    ) {

        EquipmentSlot nmsSlot = null;
        net.minecraft.world.item.ItemStack nmsItem = null;

        // Converting EquipmentSlot and ItemStack to NMS ones.
        nmsSlot = CraftEquipmentSlot.getNMS(slot);
        nmsItem = CraftItemStack.asNMSCopy(item);

        if (nmsSlot == null) return;

        Pair<EquipmentSlot, net.minecraft.world.item.ItemStack> pair = new Pair<>(nmsSlot, nmsItem);

        List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> pairs = Collections.singletonList(pair);

        ClientboundSetEquipmentPacket packet = new ClientboundSetEquipmentPacket(entityId, pairs);
        for (Player p : sendTo) sendPacket(p, packet);
    }

    @Override
    public void equipmentSlotUpdate(
            int entityId,
            HashMap<org.bukkit.inventory.EquipmentSlot, ItemStack> equipment,
            List<Player> sendTo
    ) {

        List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> pairs = new ArrayList<>();

        for (org.bukkit.inventory.EquipmentSlot slot : equipment.keySet()) {
            EquipmentSlot nmsSlot = CraftEquipmentSlot.getNMS(slot);
            net.minecraft.world.item.ItemStack nmsItem = CraftItemStack.asNMSCopy(equipment.get(slot));

            Pair<EquipmentSlot, net.minecraft.world.item.ItemStack> pair = new Pair<>(nmsSlot, nmsItem);
            pairs.add(pair);
        }

        ClientboundSetEquipmentPacket packet = new ClientboundSetEquipmentPacket(entityId, pairs);
        for (Player p : sendTo) sendPacket(p, packet);
    }


    @Override
    public void slotUpdate(
            Player player,
            int slot
    ) {
        int index = 0;

        ServerPlayer player1 = ((CraftPlayer) player).getHandle();

        if (index < Inventory.getSelectionSize()) {
            index += 36;
        } else if (index > 39) {
            index += 5; // Off hand
        } else if (index > 35) {
            index = 8 - (index - 36);
        }
        ItemStack item = player.getInventory().getItem(slot);

        Packet packet = new ClientboundContainerSetSlotPacket(player1.inventoryMenu.containerId, player1.inventoryMenu.incrementStateId(), index, CraftItemStack.asNMSCopy(item));
        sendPacket(player, packet);
    }

    @Override
    public void hideNPCName(Player player, String NPCName) {
        //Creating the team
        PlayerTeam team = new PlayerTeam(((CraftScoreboard) Bukkit.getScoreboardManager().getMainScoreboard()).getHandle(), NPCName);

        //Setting name visibility
        team.setNameTagVisibility(Team.Visibility.NEVER);

        //Remove the Team (i assume so if it exists)
        ClientboundSetPlayerTeamPacket removeTeamPacket = ClientboundSetPlayerTeamPacket.createRemovePacket(team);
        sendPacket(player, removeTeamPacket);
        //Creating the Team
        ClientboundSetPlayerTeamPacket createTeamPacket = ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true);
        sendPacket(player, createTeamPacket);
        //Adding players to the team (You have to use the NPC's name, and add it to a list)
        ClientboundSetPlayerTeamPacket createPlayerTeamPacket = ClientboundSetPlayerTeamPacket.createMultiplePlayerPacket(team, new ArrayList<String>() {{
            add(NPCName);
        }}, ClientboundSetPlayerTeamPacket.Action.ADD);
        sendPacket(player, createPlayerTeamPacket);
    }

    @Override
    public void entitySpawn(int entityId, UUID uuid, EntityType entityType, Location location, List<Player> sendTo) {
        net.minecraft.world.entity.EntityType<?> entityType1 = CraftEntityType.bukkitToMinecraft(entityType);
        double x = location.getX(), y = location.getY(), z = location.getZ();
        float yaw = location.getYaw(), pitch = location.getPitch();

        ClientboundAddEntityPacket packet = new ClientboundAddEntityPacket(entityId, UUID.randomUUID(), x, y, z, pitch, yaw, entityType1, 0, Vec3.ZERO, 0f);
        for (Player p : sendTo) sendPacket(p, packet);
    }

    @Override
    public void entityDestroy(IntList entityIds, List<Player> sendTo) {
        ClientboundRemoveEntitiesPacket packet = new ClientboundRemoveEntitiesPacket(entityIds);
        for (Player p : sendTo) sendPacket(p, packet);
    }

    @Override
    public void entityDestroy(int entityId, List<Player> sendTo) {
        ClientboundRemoveEntitiesPacket packet = new ClientboundRemoveEntitiesPacket(IntList.of(entityId));
        for (Player p : sendTo) sendPacket(p, packet);
    }

    @Override
    public void itemDisplayMetadata(int entityId, float width, float height, float viewRange, int blockLight, int skyLight, ItemDisplay.ItemDisplayTransform transform, ItemStack itemStack, List<Player> sendTo) {
        List<SynchedEntityData.DataValue<?>> dataValues = new ArrayList<>();

        dataValues.add(new SynchedEntityData.DataValue<>(9, EntityDataSerializers.INT, 0));
        dataValues.add(new SynchedEntityData.DataValue<>(16, EntityDataSerializers.INT, (blockLight << 4 | skyLight << 20)));
        dataValues.add(new SynchedEntityData.DataValue<>(17, EntityDataSerializers.FLOAT, viewRange));
        dataValues.add(new SynchedEntityData.DataValue<>(20, EntityDataSerializers.FLOAT, width));
        dataValues.add(new SynchedEntityData.DataValue<>(21, EntityDataSerializers.FLOAT, height));

        dataValues.add(new SynchedEntityData.DataValue<>(23, EntityDataSerializers.ITEM_STACK, CraftItemStack.asNMSCopy(itemStack)));
        dataValues.add(new SynchedEntityData.DataValue<>(24, EntityDataSerializers.BYTE, (byte) transform.ordinal()));

        ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(entityId, dataValues);
        for (Player p : sendTo) sendPacket(p, packet);
    }

    public void sendPacket(Player player, Packet packet) {
        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        ServerPlayerConnection connection = serverPlayer.connection;
        connection.send(packet);
    }

    @Override
    public boolean getSupported() {
        return true;
    }
}
