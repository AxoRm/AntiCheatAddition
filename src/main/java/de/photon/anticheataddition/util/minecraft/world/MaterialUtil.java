package de.photon.anticheataddition.util.minecraft.world;

import com.google.common.collect.Sets;
import de.photon.anticheataddition.ServerVersion;
import de.photon.anticheataddition.exception.UnknownMinecraftException;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

@UtilityClass
public final class MaterialUtil
{

    /**
     * Materials which bounce the player up when jumping or landing on them.
     */
    public static final Set<Material> BOUNCE_MATERIALS;
    /**
     * Materials which can cause an automatic step upwards (e.g. slabs and stairs)
     */
    public static final Set<Material> AUTO_STEP_MATERIALS;
    public static final Material EXPERIENCE_BOTTLE;
    public static final Material SPAWNER;
    public static final Set<Material> LIQUIDS;
    /**
     * Contains all containers that need a free space of any kind above the container (e.g. chests with a stair above)
     */
    public static final Set<Material> FREE_SPACE_CONTAINERS;
    // A set of materials which hitboxes changed in minecraft 1.9
    public static final Set<Material> CHANGED_HITBOX_MATERIALS = ServerVersion.is18() ? Sets.immutableEnumSet(Material.getMaterial("STAINED_GLASS_PANE"),
                                                                                                              Material.getMaterial("THIN_GLASS"),
                                                                                                              Material.getMaterial("IRON_FENCE"),
                                                                                                              Material.CHEST,
                                                                                                              Material.ANVIL) : Set.of();

    static {
        val autoStepMaterials = EnumSet.of(Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST);
        val bounceMaterials = EnumSet.of(Material.SLIME_BLOCK);
        val freeSpaceContainers = EnumSet.of(Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST);

        switch (ServerVersion.getActiveServerVersion()) {
            case MC18:
            case MC112:
                autoStepMaterials.addAll(getMaterialsEndingWith("_STAIRS", "_SLABS"));
                // This will automatically exclude the "BED" on 1.8.8, as jumping was introduced in 1.12.
                bounceMaterials.addAll(getMaterialsEndingWith("_BED"));
                freeSpaceContainers.addAll(getMaterialsEndingWith("SHULKER_BOK"));

                EXPERIENCE_BOTTLE = Material.getMaterial("EXP_BOTTLE");
                SPAWNER = Material.getMaterial("MOB_SPAWNER");
                LIQUIDS = Sets.immutableEnumSet(Material.WATER, Material.LAVA, Material.getMaterial("STATIONARY_WATER"), Material.getMaterial("STATIONARY_LAVA"));
                break;
            case MC115:
            case MC116:
            case MC117:
            case MC118:
                autoStepMaterials.addAll(ofTags(Tag.SLABS, Tag.WOODEN_SLABS, Tag.STAIRS, Tag.WOODEN_STAIRS));
                bounceMaterials.addAll(ofTags(Tag.BEDS));
                freeSpaceContainers.addAll(ofTags(Tag.SHULKER_BOXES));

                EXPERIENCE_BOTTLE = Material.EXPERIENCE_BOTTLE;
                SPAWNER = Material.SPAWNER;
                LIQUIDS = Sets.immutableEnumSet(Material.WATER, Material.LAVA);
                break;
            default:
                throw new UnknownMinecraftException();
        }

        AUTO_STEP_MATERIALS = Sets.immutableEnumSet(autoStepMaterials);
        BOUNCE_MATERIALS = Sets.immutableEnumSet(bounceMaterials);
        FREE_SPACE_CONTAINERS = Sets.immutableEnumSet(freeSpaceContainers);
    }

    public static Set<Material> getMaterialsEndingWith(String... ends)
    {
        return Arrays.stream(Material.values())
                     .filter(material -> StringUtils.endsWithAny(material.name(), ends))
                     .collect(Sets.toImmutableEnumSet());
    }

    @SafeVarargs
    public static Set<Material> ofTags(Tag<Material>... tags)
    {
        return Arrays.stream(tags)
                     .map(Tag::getValues)
                     .flatMap(Set::stream)
                     .collect(Sets.toImmutableEnumSet());
    }

    /**
     * Fix for Spigot's broken occluding method.
     */
    public static boolean isReallyOccluding(Material material)
    {
        return material != Material.BARRIER &&
               material.isOccluding() &&
               material != SPAWNER;
    }

    /**
     * Checks if a {@link Collection} of {@link Material}s contains any of certain {@link Material}s.
     */
    public static boolean containsMaterials(@NotNull final Collection<Material> searchFor, @NotNull final Collection<Material> toBeSearched)
    {
        for (Material material : searchFor) if (toBeSearched.contains(material)) return true;
        return false;
    }

    /**
     * Checks if a {@link Collection} of {@link Material}s contains liquids.
     */
    public static boolean containsLiquids(@NotNull final Collection<Material> toBeSearched)
    {
        return containsMaterials(LIQUIDS, toBeSearched);
    }
}