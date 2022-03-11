package de.photon.aacadditionpro.util.visibility;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import de.photon.aacadditionpro.AACAdditionPro;
import de.photon.aacadditionpro.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

abstract class PlayerInformationHider implements Listener
{
    private final PacketListener informationPacketListener;
    private final Multimap<Entity, Entity> hiddenFromPlayerMap;

    protected PlayerInformationHider(@NotNull PacketType... affectedPackets)
    {
        informationPacketListener = new PacketAdapter(AACAdditionPro.getInstance(), ListenerPriority.NORMAL, Set.of(affectedPackets))
        {
            @Override
            public void onPacketSending(final PacketEvent event)
            {
                if (event.isPlayerTemporary()) return;
                final int entityId = event.getPacket().getIntegers().read(0);

                // Get all hidden entities
                final Collection<Entity> hiddenEntities;
                synchronized (hiddenFromPlayerMap) {
                    hiddenEntities = hiddenFromPlayerMap.get(event.getPlayer());
                }

                // If the entityId of the packet is in the hidden entities, cancel the packet sending.
                for (Entity entity : hiddenEntities) {
                    if (entityId == entity.getEntityId()) {
                        event.setCancelled(true);
                        break;
                    }
                }
            }
        };

        hiddenFromPlayerMap = MultimapBuilder.hashKeys(AACAdditionPro.SERVER_EXPECTED_PLAYERS).hashSetValues(AACAdditionPro.WORLD_EXPECTED_PLAYERS).build();
    }

    public void clear()
    {
        synchronized (hiddenFromPlayerMap) {
            hiddenFromPlayerMap.clear();
        }
    }

    public void registerListeners()
    {
        // Only start if the ServerVersion is supported
        if (ServerVersion.containsActiveServerVersion(this.getSupportedVersions())) {
            // Register events and packet listener
            AACAdditionPro.getInstance().registerListener(this);
            ProtocolLibrary.getProtocolManager().addPacketListener(this.informationPacketListener);
        }
    }

    public void unregisterListeners()
    {
        HandlerList.unregisterAll(this);
        ProtocolLibrary.getProtocolManager().removePacketListener(this.informationPacketListener);
    }

    protected Set<ServerVersion> getSupportedVersions()
    {
        return EnumSet.allOf(ServerVersion.class);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event)
    {
        removeEntity(event.getEntity());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event)
    {
        // Cache entities for performance reasons so the server doesn't need to load them again when the
        // task is executed.
        synchronized (hiddenFromPlayerMap) {
            for (final Entity entity : event.getChunk().getEntities()) removeEntity(entity);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event)
    {
        removeEntity(event.getPlayer());
    }

    /**
     * Remove the given entity from the underlying map.
     *
     * @param entity - the entity to remove.
     */
    private void removeEntity(Entity entity)
    {
        synchronized (hiddenFromPlayerMap) {
            hiddenFromPlayerMap.removeAll(entity);
            // Remove all the instances of entity from the values.
            //noinspection StatementWithEmptyBody
            while (hiddenFromPlayerMap.values().remove(entity)) ;
        }
    }

    /**
     * Hides a {@link Player} from another {@link Player}.
     */
    public void setHiddenEntities(@NotNull Player observer, @NotNull Set<Entity> toHide)
    {
        Set<Entity> oldHidden;
        synchronized (hiddenFromPlayerMap) {
            oldHidden = Set.copyOf(hiddenFromPlayerMap.replaceValues(observer, toHide));
        }

        final Set<Entity> newRevealed = Sets.difference(oldHidden, toHide);
        final Set<Entity> newHidden = Sets.difference(toHide, oldHidden);

        // ProtocolManager check is needed to prevent errors.
        if (ProtocolLibrary.getProtocolManager() != null) {
            // Update the entities that have been hidden and shall now be revealed.
            updateEntities(observer, newRevealed);
        }

        // Call onHide for those entities that have been revealed and shall now be hidden.
        this.onHide(observer, newHidden);
    }

    public void revealAllEntities(@NotNull Player observer)
    {
        Collection<Entity> oldEntities;
        synchronized (hiddenFromPlayerMap) {
            oldEntities = hiddenFromPlayerMap.removeAll(observer);
        }

        updateEntities(observer, oldEntities);
    }

    public void updateEntities(@NotNull Player observer, Collection<Entity> entities)
    {
        if (entities.isEmpty()) return;

        Bukkit.getScheduler().runTask(AACAdditionPro.getInstance(), () -> {
            var playerList = List.of(observer);
            for (Entity entity : entities) ProtocolLibrary.getProtocolManager().updateEntity(entity, playerList);
        });
    }

    protected abstract void onHide(@NotNull Player observer, @NotNull Collection<Entity> toHide);
}
