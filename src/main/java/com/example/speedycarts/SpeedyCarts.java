package com.example.speedycarts;

import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeEntityMinecart;
import net.minecraftforge.event.entity.minecart.MinecartUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Main mod class for Speedy Carts. This class registers an event handler
 * which listens for {@link MinecartUpdateEvent} and applies a speed
 * multiplier to carts the first time they are updated. The multiplier and
 * some safeguards are documented in the code for clarity.
 */
@Mod(SpeedyCarts.MOD_ID)
public class SpeedyCarts {

    /** The mod identifier used in {@code mods.toml}. */
    public static final String MOD_ID = "speedycarts";

    /**
     * Factor by which minecart speed should be increased. A normal rail has a
     * maximum speed of about 0.4 blocks/tick, and Forge documentation notes
     * that speeds above 1.1 blocks/tick may cause chunk loading issues【272171985542909†L346-L354】. Multiplying by
     * fifteen would nominally produce a maximum of 6.0, so be aware this
     * intentionally pushes the game beyond recommended limits.
     */
    private static final float SPEED_MULTIPLIER = 15.0f;

    public SpeedyCarts() {
        // Register this object on the mod event bus so that our event handlers
        // receive Forge events. Without this, methods annotated with
        // @SubscribeEvent would never run.
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Called every tick for each minecart. When a minecart is first
     * encountered, this method adjusts its speed cap and air drag. Changes
     * are only performed once per cart by storing a flag in its persistent
     * NBT data. If you do not track this, the values would be multiplied
     * repeatedly each tick, leading to extreme speeds and numerical overflow.
     *
     * @param event the update event for a particular minecart
     */
    @SubscribeEvent
    public void onMinecartUpdate(MinecartUpdateEvent event) {
        AbstractMinecartEntity cart = event.getMinecart();
        // Only modify carts on the logical server. Doing this on both sides
        // would duplicate the effect.
        if (cart.level.isClientSide()) {
            return;
        }

        // Use the cart's persistent data (saved across world reloads) to
        // record whether we've already applied the speed multiplier. The key
        // "SpeedyCartsApplied" is arbitrary but unique to this mod.
        CompoundNBT data = cart.getPersistentData();
        if (data.getBoolean("SpeedyCartsApplied")) {
            return;
        }

        // Cast to IForgeEntityMinecart to access extension methods like
        // setCurrentCartSpeedCapOnRail() and setDragAir()【272171985542909†L166-L176】. These methods
        // are documented to control minecart speed limits and air drag【272171985542909†L346-L367】.
        IForgeEntityMinecart forgeCart = (IForgeEntityMinecart) cart;

        // Read the default maximum speed on rail. According to Forge
        // documentation, this value is about 0.4 for normal rails【272171985542909†L346-L354】.
        float baseMaxRailSpeed = forgeCart.getMaxCartSpeedOnRail();

        // Increase the current speed cap. Forge clamps this value to a maximum
        // allowed by getMaxCartSpeedOnRail(), so using an extremely large
        // multiplier may not have the full effect.
        forgeCart.setCurrentCartSpeedCapOnRail(baseMaxRailSpeed * SPEED_MULTIPLIER);

        // Increase the minecart's lateral and vertical air speed limits. These
        // control how fast the cart can move through the air when off rails.
        forgeCart.setMaxSpeedAirLateral(forgeCart.getMaxSpeedAirLateral() * SPEED_MULTIPLIER);
        forgeCart.setMaxSpeedAirVertical(forgeCart.getMaxSpeedAirVertical() * SPEED_MULTIPLIER);

        // Reduce air drag so that carts lose less speed when travelling through
        // the air. Lower drag results in higher sustained speeds.
        forgeCart.setDragAir(forgeCart.getDragAir() / SPEED_MULTIPLIER);

        // Optionally rescale the current velocity to approach the new speed cap.
        Vector3d motion = cart.getDeltaMovement();
        double horizontalSpeed = Math.hypot(motion.x(), motion.z());
        if (horizontalSpeed > 0) {
            // Compute the target speed as base speed times multiplier. Limit
            // the new speed to avoid going to zero (division by zero).
            double targetSpeed = baseMaxRailSpeed * SPEED_MULTIPLIER;
            double desiredSpeed = horizontalSpeed * SPEED_MULTIPLIER;
            double newSpeed = Math.min(desiredSpeed, targetSpeed);
            if (newSpeed > 0) {
                double scale = newSpeed / horizontalSpeed;
                cart.setDeltaMovement(motion.x() * scale, motion.y(), motion.z() * scale);
            }
        }

        // Mark this cart as modified to avoid repeat application.
        data.putBoolean("SpeedyCartsApplied", true);
    }
}
