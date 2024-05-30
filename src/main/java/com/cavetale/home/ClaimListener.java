package com.cavetale.home;

import com.cavetale.core.event.block.PlayerBlockAbilityQuery;
import com.cavetale.core.event.block.PlayerBreakBlockEvent;
import com.cavetale.core.event.entity.PlayerEntityAbilityQuery;
import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.structure.Structures;
import com.cavetale.home.struct.BlockVector;
import com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent;
import io.papermc.paper.entity.Bucketable;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import static com.cavetale.core.util.CamelCase.toCamelCase;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.text;

@RequiredArgsConstructor
final class ClaimListener implements Listener {
    private final HomePlugin plugin;

    public ClaimListener enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        return this;
    }

    /**
     * Check if a player action is permissible and cancel it if not.
     * If the action is in a world not subject to this plugin, nothing
     * will be cancelled and true returned.
     *
     * @return True if the event is permitted, false otherwise.
     */
    public boolean checkPlayerAction(Player player, Block block, TrustType requiredTrust, Cancellable cancellable, boolean notify) {
        if (plugin.doesIgnoreClaims(player)) return true;
        if (!plugin.isLocalHomeWorld(block.getWorld())) return true;
        Claim claim = plugin.getClaimAt(block);
        if (claim == null) {
            if (requiredTrust.gt(TrustType.INTERACT)) {
                NamespacedKey structureKey = Structures.get().structureKeyAt(block);
                if (structureKey != null) {
                    if (cancellable != null) cancellable.setCancelled(true);
                    if (notify) {
                        Component msg = text("You cannot modify an unclaimed "
                                             + toCamelCase(" ", List.of(structureKey.getKey().split("_"))),
                                             NamedTextColor.RED);
                        plugin.sessions.of(player).notify(player, msg);
                    }
                    return false;
                }
            }
            return true;
        }
        TrustType trustType = claim.getTrustType(player.getUniqueId(), block);
        if (trustType.gte(requiredTrust)) return true;
        if (cancellable instanceof PlayerInteractEvent) {
            PlayerInteractEvent pis = (PlayerInteractEvent) cancellable;
            pis.setUseInteractedBlock(Event.Result.DENY);
        } else if (cancellable != null) {
            cancellable.setCancelled(true);
        }
        if (notify) {
            plugin.sessions.of(player).notify(player, claim);
        }
        return false;
    }

    public boolean checkPlayerAction(Player player, Location location, TrustType requiredTrust, Cancellable cancellable, boolean notify) {
        return checkPlayerAction(player, location.getBlock(), requiredTrust, cancellable, notify);
    }

    /**
     * Utility function to determine whether a player owns an
     * entity. To own an entity, it has to be tameable and the
     * player has to have tamed it. Tamed animals should always be
     * able to be interacted with, or even damaged, by their
     * owner.
     *
     * @arg player the player
     * @arg entity the entity
     * @return true if player owns entity, false otherwise
     */
    static boolean isOwner(Player player, Entity entity) {
        if (entity instanceof Tameable) {
            Tameable tameable = (Tameable) entity;
            if (!tameable.isTamed()) return false;
            AnimalTamer owner = tameable.getOwner();
            if (owner == null) return false;
            if (owner.getUniqueId().equals(player.getUniqueId())) return true;
        }
        return false;
    }

    private boolean isHostileMob(Entity entity) {
        switch (entity.getType()) {
        case GHAST:
        case SLIME:
        case PHANTOM:
        case MAGMA_CUBE:
        case ENDER_DRAGON:
        case SHULKER:
        case SHULKER_BULLET:
        case HOGLIN:
            return true;
        case IRON_GOLEM:
            return !((IronGolem) entity).isPlayerCreated();
        default:
            return entity instanceof Monster;
        }
    }

    private boolean isHostileMob(EntityType entityType) {
        switch (entityType) {
        case GHAST:
        case SLIME:
        case PHANTOM:
        case MAGMA_CUBE:
        case ENDER_DRAGON:
        case SHULKER:
        case SHULKER_BULLET:
        case HOGLIN:
            return true;
        default:
            return Monster.class.isAssignableFrom(entityType.getEntityClass());
        }
    }

    private Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        } else if (damager instanceof Projectile) {
            Projectile projectile = (Projectile) damager;
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        checkPlayerAction(player, block, TrustType.BUILD, event, true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        checkPlayerAction(event.getPlayer(), event.getBlock(), TrustType.BUILD, event, true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockDropItem(BlockDropItemEvent event) {
        checkPlayerAction(event.getPlayer(), event.getBlock(), TrustType.BUILD, event, true);
    }

    // Frost Walker
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        checkPlayerAction((Player) event.getEntity(), event.getBlock(), TrustType.BUILD, event, false);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        final Entity damaged = event.getEntity();
        if (!plugin.isLocalHomeWorld(damaged.getWorld())) return;
        final Player player = getPlayerDamager(event.getDamager());
        if (player != null && damaged instanceof Player) {
            // PVP
            if (player.equals(damaged)) return;
            if (!plugin.isPvPAllowed(BlockVector.of(damaged.getLocation()))) {
                event.setCancelled(true);
                player.sendActionBar(Component.text("PvP is not allowed here", TextColor.color(0xFF0000)));
            }
            return;
        } else if (player != null) {
            // Player damaged a non-player
            boolean claimed = plugin.getClaimAt(damaged.getLocation().getBlock()) != null;
            TrustType trustType;
            if (claimed && damaged.getType() == EntityType.SHULKER) {
                // Some extra code for hostile, yet valuable mobs in
                // claims.
                trustType = TrustType.BUILD;
            } else if (isHostileMob(damaged)) {
                Component customName = damaged.customName();
                if (customName == null || empty().equals(customName)) return;
                trustType = TrustType.BUILD;
            } else {
                // Must be an animal
                trustType = TrustType.BUILD;
            }
            checkPlayerAction(player, damaged.getLocation().getBlock(), trustType, event, true);
        } else {
            // Non-player damages something
            switch (event.getCause()) {
            case BLOCK_EXPLOSION:
            case ENTITY_EXPLOSION:
                Claim claim = plugin.getClaimAt(damaged.getLocation().getBlock());
                if (claim == null) {
                    return;
                }
                if (claim.getSetting(ClaimSetting.EXPLOSIONS)) return;
                if (damaged instanceof Player) return;
                if (damaged instanceof Mob) return;
                event.setCancelled(true);
                break;
            default:
                break;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onProjecitleHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        Player player = getPlayerDamager(projectile);
        if (player == null) return;
        Block block = event.getHitBlock();
        if (block != null) {
            if (block.getType() == Material.TARGET && projectile instanceof AbstractArrow && !(projectile instanceof Trident)) {
                return;
            }
            checkPlayerAction(player, block, TrustType.BUILD, event, false);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityDamageByBlock(EntityDamageByBlockEvent event) {
        final Entity damaged = event.getEntity();
        final Block block = event.getDamager();
        if (!plugin.isLocalHomeWorld(damaged.getWorld())) return;
        switch (event.getCause()) {
        case BLOCK_EXPLOSION:
        case ENTITY_EXPLOSION:
            Claim claim = plugin.getClaimAt(damaged.getLocation().getBlock());
            if (claim == null) {
                return;
            }
            if (claim.getSetting(ClaimSetting.EXPLOSIONS)) return;
            if (damaged instanceof Player) return;
            if (damaged instanceof Mob) return;
            event.setCancelled(true);
            break;
        default:
            break;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        if (!plugin.isLocalHomeWorld(event.getEntity().getWorld())) return;
        Player damager = getPlayerDamager(event.getCombuster());
        if (damager == null) return;
        boolean melee = event.getCombuster().equals(damager);
        if (plugin.doesIgnoreClaims(damager)) return;
        Entity damaged = event.getEntity();
        if (damaged instanceof Player) {
            // PVP
            if (damager.equals(damaged)) return;
            if (!plugin.isPvPAllowed(BlockVector.of(damaged.getLocation()))) {
                event.setCancelled(true);
            }
            return;
        }
        if (isHostileMob(damaged)) {
            Component customName = damaged.customName();
            if (customName == null || empty().equals(customName)) return;
        }
        if (isOwner(damager, damaged)) {
            // tamed animals
            return;
        }
        Block block = damaged.getLocation().getBlock();
        checkPlayerAction(damager, block, TrustType.BUILD, event, melee);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (!plugin.isLocalHomeWorld(vehicle.getWorld())) return;
        Player player = getPlayerDamager(event.getAttacker());
        if (player == null) return;
        boolean melee = player.equals(event.getAttacker());
        if (isOwner(player, vehicle)) return;
        if (plugin.getClaimAt(vehicle.getLocation()) == null) return;
        checkPlayerAction(player, vehicle.getLocation().getBlock(), TrustType.BUILD, event, melee);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (!plugin.isLocalHomeWorld(vehicle.getWorld())) return;
        Player player = getPlayerDamager(event.getAttacker());
        if (player == null) return;
        boolean melee = player.equals(event.getAttacker());
        if (isOwner(player, vehicle)) return;
        if (plugin.getClaimAt(vehicle.getLocation()) == null) return;
        checkPlayerAction(player, vehicle.getLocation().getBlock(), TrustType.BUILD, event, melee);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleCreate(VehicleCreateEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (!plugin.isLocalHomeWorld(vehicle.getWorld())) return;
        if (!(vehicle instanceof LivingEntity)
            && plugin.getClaimAt(vehicle.getLocation()) == null) {
            vehicle.setPersistent(false);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        final Player player = event.getPlayer();
        if (plugin.doesIgnoreClaims(player)) return;
        final Entity entity = event.getRightClicked();
        if (entity instanceof Player) return;
        if (isOwner(player, entity)) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (entity instanceof Animals animals && item != null && animals.isBreedItem(item)) {
            checkPlayerAction(player, entity.getLocation().getBlock(), TrustType.CONTAINER, event, true);
        } else if (entity instanceof Bucketable && item != null && item.getType() == Material.WATER_BUCKET) {
            checkPlayerAction(player, entity.getLocation().getBlock(), TrustType.CONTAINER, event, true);
        } else if (entity instanceof RideableMinecart) {
            checkPlayerAction(player, entity.getLocation().getBlock(), TrustType.INTERACT, event, true);
        } else {
            checkPlayerAction(player, entity.getLocation().getBlock(), TrustType.BUILD, event, true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        final Player player = event.getPlayer();
        final Entity entity = event.getRightClicked();
        checkPlayerAction(player, entity.getLocation().getBlock(), TrustType.BUILD, event, true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerShearEntity(PlayerShearEntityEvent event) {
        final Player player = event.getPlayer();
        if (plugin.doesIgnoreClaims(player)) return;
        final Entity entity = event.getEntity();
        if (isOwner(player, entity)) return;
        if (entity.getType() == EntityType.SHEEP) {
            checkPlayerAction(player, entity.getLocation().getBlock(), TrustType.CONTAINER, event, true);
        } else {
            checkPlayerAction(player, entity.getLocation().getBlock(), TrustType.BUILD, event, true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        final Player player = (Player) event.getEntity();
        if (plugin.doesIgnoreClaims(player)) return;
        final Entity mount = event.getMount();
        if (!(mount instanceof Animals)) return; // quick and dirty chair fix
        if (isOwner(player, mount)) return;
        checkPlayerAction(player, mount.getLocation().getBlock(), TrustType.INTERACT, event, true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        final Player player = event.getPlayer();
        if (plugin.doesIgnoreClaims(player)) return;
        final Entity entity = event.getEntity();
        if (isOwner(player, entity)) return;
        checkPlayerAction(player, entity.getLocation().getBlock(), TrustType.BUILD, event, true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        if (plugin.doesIgnoreClaims(player)) return;
        final Block block = event.getClickedBlock();
        if (block == null) return;
        Material mat = block.getType();
        Claim claim = plugin.getClaimAt(block);
        // Consider soil trampling
        switch (event.getAction()) {
        case PHYSICAL:
            if (mat == Material.FARMLAND || mat == Material.TURTLE_EGG) {
                event.setCancelled(true);
            } else {
                if (Tag.PRESSURE_PLATES.isTagged(mat)) return;
                if (Tag.REDSTONE_ORES.isTagged(mat)) return;
                if (mat == Material.BIG_DRIPLEAF) return;
                checkPlayerAction(event.getPlayer(), block, TrustType.INTERACT, event, false);
            }
            return;
        case RIGHT_CLICK_BLOCK:
            if (plugin.sessions.of(player).onPlayerInteract(event)) return;
            if (mat.isInteractable()) {
                if (Tag.DOORS.isTagged(mat) || Tag.BUTTONS.isTagged(mat) || Tag.TRAPDOORS.isTagged(mat)) {
                    checkPlayerAction(player, block, TrustType.INTERACT, event, true);
                } else if (Tag.ANVIL.isTagged(mat)) {
                    checkPlayerAction(player, block, TrustType.CONTAINER, event, true);
                } else if (Tag.BEDS.isTagged(mat)) {
                    if (block.getWorld().getEnvironment() != World.Environment.NORMAL) {
                        if (claim != null && !claim.getSetting(ClaimSetting.EXPLOSIONS)) {
                            plugin.sessions.of(player).notify(player, claim);
                            event.setCancelled(true);
                            return;
                        }
                    }
                    checkPlayerAction(player, block, TrustType.INTERACT, event, true);
                } else {
                    switch (mat) {
                    case ENCHANTING_TABLE:
                    case CRAFTING_TABLE:
                    case ENDER_CHEST:
                    case GRINDSTONE:
                    case STONECUTTER:
                    case LEVER:
                    case SMITHING_TABLE:
                        checkPlayerAction(player, block, TrustType.INTERACT, event, true);
                        break;
                    case RESPAWN_ANCHOR:
                        if (block.getWorld().getEnvironment() != World.Environment.NETHER) {
                            RespawnAnchor data = (RespawnAnchor) block.getBlockData();
                            if (data.getCharges() >= data.getMaximumCharges() && claim != null && !claim.getSetting(ClaimSetting.EXPLOSIONS)) {
                                plugin.sessions.of(player).notify(player, claim);
                                event.setCancelled(true);
                                return;
                            }
                        }
                        checkPlayerAction(player, block, TrustType.BUILD, event, true);
                        break;
                    case JUKEBOX:
                        checkPlayerAction(player, block, TrustType.CONTAINER, event, true);
                        break;
                    default:
                        if (block.getState() instanceof InventoryHolder) {
                            checkPlayerAction(player, block, TrustType.CONTAINER, event, true);
                        } else {
                            checkPlayerAction(player, block, TrustType.BUILD, event, false);
                        }
                        break;
                    }
                }
            } else {
                checkPlayerAction(player, block, TrustType.INTERACT, event, false);
            }
            return;
        case LEFT_CLICK_BLOCK:
            checkPlayerAction(player, block, TrustType.INTERACT, event, true);
            return;
        default:
            break;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityInteract(EntityInteractEvent event) {
        Block block = event.getBlock();
        Material mat = block.getType();
        if (mat == Material.FARMLAND || mat == Material.TURTLE_EGG) {
            Claim claim = plugin.getClaimAt(block);
            if (claim == null) return;
            event.setCancelled(true);
        } else if (event.getEntity() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            checkPlayerAction(player, block, TrustType.INTERACT, event, false);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityPlace(EntityPlaceEvent event) {
        checkPlayerAction(event.getPlayer(), event.getBlock(), TrustType.BUILD, event, true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!plugin.isLocalHomeWorld(event.getEntity().getWorld())) return;
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            Claim claim = plugin.getClaimAt(event.getEntity().getLocation().getBlock());
            if (claim == null || !claim.getSetting(ClaimSetting.EXPLOSIONS)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingPlace(HangingPlaceEvent event) {
        Block block = event.getEntity().getLocation().getBlock();
        checkPlayerAction(event.getPlayer(), block, TrustType.BUILD, null, true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        Player player = getPlayerDamager(event.getRemover());
        if (player != null) {
            boolean melee = player.equals(event.getRemover());
            Block block = event.getEntity().getLocation().getBlock();
            checkPlayerAction(player, block, TrustType.BUILD, event, melee);
        }
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            Claim claim = plugin.getClaimAt(event.getEntity().getLocation().getBlock());
            if (claim == null || !claim.getSetting(ClaimSetting.EXPLOSIONS)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        checkPlayerAction(event.getPlayer(), event.getBlockClicked(), TrustType.BUILD, event, true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        checkPlayerAction(event.getPlayer(), event.getBlockClicked(), TrustType.BUILD, event, true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.isLocalHomeWorld(event.getEntity().getWorld())) return;
        for (Iterator<Block> iter = event.blockList().iterator(); iter.hasNext();) {
            Claim claim = plugin.getClaimAt(iter.next());
            if (claim == null || !claim.getSetting(ClaimSetting.EXPLOSIONS)) {
                iter.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!plugin.isLocalHomeWorld(event.getBlock().getWorld())) return;
        Claim claim = plugin.getClaimAt(event.getBlock());
        if (claim != null && !claim.getSetting(ClaimSetting.EXPLOSIONS)) {
            event.setCancelled(true);
            return;
        }
        for (Iterator<Block> iter = event.blockList().iterator(); iter.hasNext();) {
            claim = plugin.getClaimAt(iter.next());
            if (claim == null || !claim.getSetting(ClaimSetting.EXPLOSIONS)) {
                iter.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!plugin.isLocalHomeWorld(event.getBlock().getWorld())) return;
        Claim claim = plugin.getClaimAt(event.getBlock());
        if (claim == null || !claim.getSetting(ClaimSetting.FIRE)) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!plugin.isLocalHomeWorld(event.getBlock().getWorld())) return;
        switch (event.getCause()) {
        case FLINT_AND_STEEL:
            return;
        case ENDER_CRYSTAL:
        case EXPLOSION:
        case FIREBALL:
        case LAVA:
        case LIGHTNING:
        case SPREAD:
        default:
            break;
        }
        Claim claim = plugin.getClaimAt(event.getBlock());
        if (claim == null || !claim.getSetting(ClaimSetting.FIRE)) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onCauldronLevelChange(CauldronLevelChangeEvent event) {
        if (!plugin.isLocalHomeWorld(event.getBlock().getWorld())) return;
        Entity entity = event.getEntity();
        if (entity != null) {
            Player player = getPlayerDamager(entity);
            if (player == null) return;
            boolean melee = player.equals(entity);
            checkPlayerAction(player, event.getBlock(), TrustType.BUILD, event, melee);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!plugin.isLocalHomeWorld(event.getEntity().getWorld())) return;
        Player player = getPlayerDamager(event.getEntity());
        if (player != null) {
            if (event.getBlock().getType() == Material.BIG_DRIPLEAF
                && event.getTo() == Material.BIG_DRIPLEAF) {
                return;
            }
            boolean melee = player.equals(event.getEntity());
            checkPlayerAction(player, event.getBlock(), TrustType.BUILD, event, melee);
        } else if (event.getEntity().getType() == EntityType.ENDERMAN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        if (!plugin.isLocalHomeWorld(event.getBlock().getWorld())) return;
        // Piston movement is not allowed:
        // - Outside claims
        // - Crossing claim borders
        Claim claim = plugin.getClaimAt(event.getBlock());
        if (claim == null) {
            event.setCancelled(true);
            return;
        }
        for (Block block : event.getBlocks()) {
            Claim claim2 = plugin.getClaimAt(block);
            if (claim != claim2) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        if (!plugin.isLocalHomeWorld(event.getBlock().getWorld())) return;
        // Piston movement is not allowed:
        // - Outside claims
        // - Crossing claim borders
        Claim claim = plugin.getClaimAt(event.getBlock());
        if (claim == null) {
            event.setCancelled(true);
            return;
        }
        for (Block block : event.getBlocks()) {
            Claim claim2 = plugin.getClaimAt(block);
            if (claim != claim2) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    protected void onCreatureSpawn(CreatureSpawnEvent event) {
        onCreatureSpawn(event, event.getSpawnReason(), event.getEntityType(), event.getLocation());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    protected void onPreCreatureSpawn(PreCreatureSpawnEvent event) {
        onCreatureSpawn(event, event.getReason(), event.getType(), event.getSpawnLocation());
    }

    protected void onCreatureSpawn(Cancellable event, SpawnReason reason, EntityType entityType, Location location) {
        if (!plugin.isLocalHomeWorld(location.getWorld())) return;
        switch (reason) {
        case CUSTOM:
        case DEFAULT:
            // We assume these are commands or plugins which is always
            // allowed.
            return;
        case BUILD_WITHER:
            // No wither building in the home worlds
            event.setCancelled(true);
            return;
        case NATURAL:
            if (entityType == EntityType.PHANTOM) {
                // No phantom spawning in the home world
                if (plugin.getClaimAt(location) != null) {
                    event.setCancelled(true);
                }
                return;
            }
            break;
        default: break;
        }
        // Respect claim settings
        Claim claim = plugin.getClaimAt(location);
        if (claim == null) return;
        if (!claim.getSetting(ClaimSetting.MOB_SPAWNING)) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onPlayerTakeLecternBook(PlayerTakeLecternBookEvent event) {
        Player player = event.getPlayer();
        Block block = event.getLectern().getBlock();
        if (block == null) return; // says @NotNull
        checkPlayerAction(player, block, TrustType.BUILD, event, true);
    }

    @EventHandler(ignoreCancelled = true)
    protected void onPlayerTeleport(PlayerTeleportEvent event) {
        switch (event.getCause()) {
        case ENDER_PEARL:
        case CHORUS_FRUIT: {
            Player player = event.getPlayer();
            Claim claim = plugin.getClaimAt(event.getTo());
            if (claim == null) return;
            if (!claim.getSetting(ClaimSetting.ENDER_PEARL)) {
                plugin.sessions.of(player).notify(player, claim);
                event.setCancelled(true);
                return;
            }
            break;
        }
        default: break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    protected void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player)) return;
        Player player = (Player) projectile.getShooter();
        Claim claim = plugin.getClaimAt(player.getLocation());
        if (claim == null) return;
        switch (projectile.getType()) {
        case ENDER_PEARL:
            if (!claim.getSetting(ClaimSetting.ENDER_PEARL)) {
                plugin.sessions.of(player).notify(player, claim);
                event.setCancelled(true);
                return;
            }
        default: break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    protected void onEntityToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        Claim claim = plugin.getClaimAt(player.getLocation());
        if (claim == null) return;
        if (event.isGliding() && !claim.getSetting(ClaimSetting.ELYTRA)) {
            Component msg = Component.text("You cannot fly in this claim!", TextColor.color(0xFF0000));
            plugin.sessions.of(player).notify(player, msg);
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.setGliding(false));
        }
    }

    @EventHandler(ignoreCancelled = true)
    protected void onPlayerBlockAbility(PlayerBlockAbilityQuery query) {
        switch (query.getAction()) {
        case USE: // Buttons: Doors
        case READ: // Lectern
            checkPlayerAction(query.getPlayer(), query.getBlock(), TrustType.INTERACT, query, false);
            break;
        case OPEN: // Chests
        case INVENTORY: // Lectern
            checkPlayerAction(query.getPlayer(), query.getBlock(), TrustType.CONTAINER, query, false);
            break;
        case FLY: {
            Claim claim = plugin.getClaimAt(query.getBlock());
            if (claim == null) return;
            if (!claim.getSetting(ClaimSetting.ELYTRA)) {
                query.setCancelled(true);
            }
            break;
        }
        case BUILD:
        case PLACE_ENTITY:
        case SPAWN_MOB: // PocketMob (before the attempt)
        default:
            checkPlayerAction(query.getPlayer(), query.getBlock(), TrustType.BUILD, query, false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    protected void onPlayerEntityAbility(PlayerEntityAbilityQuery query) {
        Player player = query.getPlayer();
        Entity entity = query.getEntity();
        if (isOwner(player, entity)) return;
        Block block = entity.getLocation().getBlock();
        switch (query.getAction()) {
        case MOUNT:
        case DISMOUNT:
        case SIT:
            checkPlayerAction(player, block, TrustType.INTERACT, query, false);
            break;
        case SHEAR:
        case FEED:
        case BREED:
        case LEASH:
        case PICKUP:
        case INVENTORY:
            checkPlayerAction(player, block, TrustType.CONTAINER, query, false);
            break;
        case DAMAGE:
        case IGNITE:
        case POTION:
        case CATCH:
        case OPEN:
        case MOVE:
        case PLACE:
        case GIMMICK:
        default:
            Component customName = entity.customName();
            if (isHostileMob(entity)
                && (customName == null || empty().equals(customName))
                && entity.getType() != EntityType.SHULKER) {
                return;
            }
            checkPlayerAction(player, block, TrustType.BUILD, query, false);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBreakBlock(PlayerBreakBlockEvent event) {
        checkPlayerAction(event.getPlayer(), event.getBlock(), TrustType.BUILD, event, true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        EntityType entityType = event.getEntityType();
        Location loc = event.getLocation();
        World world = loc.getWorld();
        if (!plugin.isLocalHomeWorld(world)) return;
        switch (event.getEntityType()) {
        case ZOMBIE:
        case SKELETON:
        case CAVE_SPIDER:
        case SPIDER:
        case SILVERFISH:
            if (world.getEnvironment() == World.Environment.NORMAL) {
                return;
            }
            break;
        case BLAZE:
        case MAGMA_CUBE: // 1.16
            if (world.getEnvironment() == World.Environment.NETHER) {
                return;
            }
            break;
        default: break;
        }
        String msg = "Spawner spawned " + event.getEntityType().name().toLowerCase()
            + " at " + world.getName()
            + " " + loc.getBlockX()
            + " " + loc.getBlockY()
            + " " + loc.getBlockZ();
        plugin.getLogger().warning(msg);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (plugin.doesIgnoreClaims(player)) return;
        Vehicle vehicle = event.getVehicle();
        checkPlayerAction(player, vehicle.getLocation().getBlock(), TrustType.BUILD, event, false);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerEggThrow(PlayerEggThrowEvent event) {
        if (!checkPlayerAction(event.getPlayer(), event.getEgg().getLocation().getBlock(), TrustType.BUILD, null, false)) {
            event.setHatching(false);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!plugin.isLocalHomeWorld(event.getBlock().getWorld())) return;
        Claim from = plugin.getClaimAt(event.getBlock());
        Claim to = plugin.getClaimAt(event.getToBlock());
        if (!Objects.equals(from, to)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    protected void onPlayerHud(PlayerHudEvent event) {
        Player player = event.getPlayer();
        plugin.sessions.of(player).onPlayerHud(player, event);
    }
}
