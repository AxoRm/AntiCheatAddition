package de.photon.aacadditionpro.modules.additions.esp;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import de.photon.aacadditionpro.util.mathematics.Hitbox;
import de.photon.aacadditionpro.util.mathematics.ResetLocation;
import de.photon.aacadditionpro.util.mathematics.ResetVector;
import de.photon.aacadditionpro.util.minecraft.world.InternalPotion;
import de.photon.aacadditionpro.util.minecraft.world.MaterialUtil;
import de.photon.aacadditionpro.util.minecraft.world.WorldUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.val;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
class CanSee
{
    // This cache reduces the required getBlock() calls.
    public static final LoadingCache<Location, Boolean> BLOCK_CACHE = CacheBuilder.newBuilder()
                                                                                  // About 32000 locations can be cached at most.
                                                                                  .maximumSize(1L << 14)
                                                                                  .expireAfterWrite(Duration.ofMillis(Esp.ESP_INTERVAL))
                                                                                  .build(new CacheLoader<>()
                                                                                  {
                                                                                      @Override
                                                                                      public @NotNull Boolean load(@NotNull Location key)
                                                                                      {
                                                                                          final Block block = key.getBlock();
                                                                                          return block != null && !block.isEmpty() && MaterialUtil.isReallyOccluding(block.getType());
                                                                                      }
                                                                                  });

    // The real MAX_FOV is 110 (quake pro), which results in 150° according to tests.
    // 150° + 15° (compensation) = 165°
    public static final double MAX_FOV = Math.toRadians(165D);

    public static boolean canSee(Player observer, Player watched)
    {
        // Glowing.
        if (InternalPotion.GLOWING.hasPotionEffect(watched)) return true;

        // ----------------------------------- Calculation ---------------------------------- //
        final Vector viewDirection = observer.getLocation().getDirection();

        for (Location cameraLocation : CameraVectorSupplier.INSTANCE.getCameraLocations(observer)) {
            var between = new ResetVector(cameraLocation.toVector().multiply(-1));
            for (Location hitLoc : Hitbox.fromPlayer(watched).getEspLocations(watched.getLocation())) {
                // Effectively hitLoc - cameraLocation because of the multiply(-1) above.
                between.resetToBase().add(hitLoc.toVector());

                // Ignore directions that cannot be seen by the player due to FOV.
                if (viewDirection.angle(between) > MAX_FOV) continue;

                // Make sure the chunks are loaded.
                // If the chunks are not loaded assume the players can see each other.
                if (!WorldUtil.INSTANCE.areChunksLoadedBetweenLocations(cameraLocation, hitLoc)) return true;

                // No intersection found
                if (canSeeHeuristic(cameraLocation, between, hitLoc)) return true;
            }
        }
        return false;
    }

    /**
     * This uses the heuristic scalars as defined in {@link #heuristicScalars(double)} to check if one can see the "to" {@link Location} from the "from" {@link Location}.
     *
     * @return <code>true</code> if no occluding blocks are found at the heuristic scalars, so one MAY see the "to" location.<br></br>
     * <code>false</code> if an occluding block has been found. In this case one cannot see the "to" location.
     */
    public static boolean canSeeHeuristic(Location from, Vector between, Location to)
    {
        val resetFrom = new ResetLocation(from);
        val normalBetween = new ResetVector(between.normalize());
        for (double d : heuristicScalars(from.distance(to))) {
            // An occluding block exists.
            if (Boolean.TRUE.equals(BLOCK_CACHE.getUnchecked(resetFrom.add(normalBetween.multiply(d))))) return false;

            resetFrom.resetToBase();
            normalBetween.resetToBase();
        }
        return true;
    }

    private static double[] heuristicScalars(double distance)
    {
        if (distance <= 2) return new double[]{1};
        if (distance <= 3) return new double[]{1, distance - 1};
        if (distance <= 5) return new double[]{1, 1.5, 2, distance - 2, distance - 1.5, distance - 1};
        if (distance <= 10) return new double[]{1, 1.5, 2, 2.5, distance / 2, distance - 2.5, distance - 2, distance - 1.5, distance - 1};
        if (distance <= 25) return new double[]{1, 1.5, 2, 2.5, distance / 4, distance / 2, 3 * distance / 4, distance - 2.5, distance - 2, distance - 1.5, distance - 1};
        return new double[]{1, 1.5, 2, 2.5, distance / 4, distance / 3, distance / 2, 2 * distance / 3, 3 * distance / 4, distance - 2.5, distance - 2, distance - 1.5, distance - 1};
    }
}
