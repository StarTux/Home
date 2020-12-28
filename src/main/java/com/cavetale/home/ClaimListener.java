package com.cavetale.home;

import com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent;
import com.winthier.exploits.Exploits;
import com.winthier.generic_events.PlayerCanBuildEvent;
import java.util.Iterator;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Lectern;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.spigotmc.event.entity.EntityMountEvent;

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
    public boolean checkPlayerAction(Player player, Block block, Action action, Cancellable cancellable) {
        if (plugin.doesIgnoreClaims(player)) return true;
        String w = block.getWorld().getName();
        if (!plugin.getHomeWorlds().contains(w)) return true;
        final String world = plugin.getMirrorWorlds().containsKey(w)
            ? plugin.getMirrorWorlds().get(w) : w;
        // Find claim
        Claim claim = plugin.getClaims().stream()
            .filter(c -> c.isInWorld(world)
                    && c.getArea().contains(block.getX(), block.getZ()))
            .findFirst().orElse(null);
        if (claim == null) {
            if (action == Action.PVP) {
                if (event != null) event.setCancelled(True);
                return false;
            } else {
                return true;
            }
        }
        // We know there is a claim, so return on the player is
        // privileged here.  Claim perms override subclaim ones.
        Subclaim subclaim = claim.getSubclaimAt(block);
        if (subclaim != null) {
            if (hasSubclaimTrust(player, subclaim, action)) return true;
        } else {
            if (hasClaimTrust(player, claim, action)) return true;
        }
        // Action is not covered by visitor, member, or owner
        // privilege.  Therefore, nothing is allowed.
        if (cancellable instanceof PlayerInteractEvent) {
            PlayerInteractEvent pis = (PlayerInteractEvent) cancellable;
            pis.setUseInteractedBlock(Event.Result.DENY);
        } else if (cancellable != null) {
            cancellable.setCancelled(true);
        }
        return false;
    }

    /**
     * Check for the given action based on claim permissions (trust or
     * invite).
     */
    public boolean hasClaimTrust(Player player, Claim claim, Action action) {
        switch (action) {
        case BUILD:
        case BUCKET:
        case VEHICLE:
        case CONTAINER:
            return claim.getBoolSetting(Claim.Setting.PUBLIC) || claim.canBuild(player);
        case INTERACT:
            return claim.canVisit(player);
        case PVP:
            return claim.getBoolSetting(Claim.Setting.PVP);
        default:
            return claim.isOwner(player);
        }
    }

    public boolean hasSubclaimTrust(Player player, Subclaim subclaim, Action action) {
        switch (action) {
        case BUILD:
        case VEHICLE:
        case BUCKET:
            return subclaim.getParent().isOwner(player) || subclaim.getTrust(player).entails(Subclaim.Trust.BUILD);
        case CONTAINER:
            return subclaim.getParent().isOwner(player) || subclaim.getTrust(player).entails(Subclaim.Trust.CONTAINER);
        case INTERACT:
            return subclaim.getParent().isOwner(player) || subclaim.getTrust(player).entails(Subclaim.Trust.ACCESS);
        case PVP:
            // TODO: combat requires pvp setting
            return subclaim.getParent().getBoolSetting(Claim.Setting.PVP);
        default:
            return subclaim.getParent().isOwner(player) || subclaim.getTrust(player).entails(Subclaim.Trust.OWNER);
        }
    }

    void warnNoBuild(Player player, Block block) {
        Claim claim = plugin.getClaimAt(block);
        if (claim != null) {
            player.sendActionBar(ChatColor.RED + "This area is claimed by "
                                 + claim.getOwnerName() + "!");
        } else {
            player.sendActionBar(ChatColor.RED + "This area is claimed!");
        }
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        player.spawnParticle(Particle.BARRIER, loc, 1, 0, 0, 0, 0);
        player.playSound(loc, Sound.ENTITY_POLAR_BEAR_WARNING,
                         SoundCategory.MASTER,
                         0.1f, 1.0f);
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

    // Events

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (!checkPlayerAction(player, block, Action.BUILD, event)) {
            warnNoBuild(player, block);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (!checkPlayerAction(event.getPlayer(), event.getBlock(), Action.BUILD, event)) {
            warnNoBuild(player, block);
        }
    }

    // Frost Walker
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        checkPlayerAction((Player) event.getEntity(), event.getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        final Entity damaged = event.getEntity();
        if (!plugin.isHomeWorld(damaged.getWorld())) return;
        final Player player = getPlayerDamager(event.getDamager());
        if (player != null && damaged instanceof Player) {
            // PVP
            if (player.equals(damaged)) return;
            Block block = damaged.getLocation().getBlock();
            checkPlayerAction(player, block, Action.PVP, event);
            return;
        } else if (player != null) {
            // Player damaged a non-player
            boolean claimed = plugin.getClaimAt(damaged.getLocation().getBlock()) != null;
            Action action;
            if (claimed && damaged.getType() == EntityType.SHULKER) {
                // Some extra code for hostile, yet valuable mobs in
                // claims.
                action = Action.BUILD;
            } else if (isHostileMob(damaged)) {
                if (damaged.getCustomName() == null) return;
                action = Action.BUILD;
            } else {
                // Must be an animal
                action = Action.BUILD;
            }
            checkPlayerAction(player, damaged.getLocation().getBlock(), action, event);
        } else {
            // Non-player damages something
            switch (event.getCause()) {
            case BLOCK_EXPLOSION:
            case ENTITY_EXPLOSION:
                Claim claim = plugin.getClaimAt(damaged.getLocation().getBlock());
                if (claim == null) {
                    return;
                }
                if (claim.getBoolSetting(Claim.Setting.EXPLOSIONS)) return;
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
    public void onEntityDamageByBlock(EntityDamageByBlockEvent event) {
        final Entity damaged = event.getEntity();
        final Block block = event.getDamager();
        if (!plugin.isHomeWorld(damaged.getWorld())) return;
        switch (event.getCause()) {
        case BLOCK_EXPLOSION:
        case ENTITY_EXPLOSION:
            Claim claim = plugin.getClaimAt(damaged.getLocation().getBlock());
            if (claim == null) {
                return;
            }
            if (claim.getBoolSetting(Claim.Setting.EXPLOSIONS)) return;
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
        if (!plugin.isHomeWorld(event.getEntity().getWorld())) return;
        Player damager = getPlayerDamager(event.getCombuster());
        if (damager == null) return;
        if (damager.hasMetadata(plugin.META_IGNORE)) return;
        Entity damaged = event.getEntity();
        if (damaged instanceof Player) {
            // PVP
            if (damager.equals(damaged)) return;
            Block block = damaged.getLocation().getBlock();
            checkPlayerAction(damager, block, Action.PVP, event);
            return;
        }
        if (isHostileMob(damaged)) {
            if (damaged.getCustomName() == null) return;
        }
        if (isOwner(damager, damaged)) {
            // tamed animals
            return;
        }
        Block block = damaged.getLocation().getBlock();
        checkPlayerAction(damager, block, Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (!plugin.isHomeWorld(vehicle.getWorld())) return;
        Player player = getPlayerDamager(event.getAttacker());
        if (player == null) return;
        if (isOwner(player, vehicle)) return;
        if (plugin.getClaimAt(vehicle.getLocation()) == null) return;
        checkPlayerAction(player, vehicle.getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (!plugin.isHomeWorld(vehicle.getWorld())) return;
        Player player = getPlayerDamager(event.getAttacker());
        if (player == null) return;
        if (isOwner(player, vehicle)) return;
        if (plugin.getClaimAt(vehicle.getLocation()) == null) return;
        checkPlayerAction(player, vehicle.getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleCreate(VehicleCreateEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (!plugin.isHomeWorld(vehicle.getWorld())) return;
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
        if (isOwner(player, entity)) return;
        checkPlayerAction(player, entity.getLocation().getBlock(), Action.BUILD, event);
    }

    // Should this just be the same as onPlayerInteractEntity() ?
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        final Player player = event.getPlayer();
        if (plugin.doesIgnoreClaims(player)) return;
        final Entity entity = event.getRightClicked();
        if (isOwner(player, entity)) return;
        checkPlayerAction(player, entity.getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        final Player player = event.getPlayer();
        final Entity entity = event.getRightClicked();
        checkPlayerAction(player, entity.getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerShearEntity(PlayerShearEntityEvent event) {
        final Player player = event.getPlayer();
        if (plugin.doesIgnoreClaims(player)) return;
        final Entity entity = event.getEntity();
        if (isOwner(player, entity)) return;
        checkPlayerAction(player, entity.getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        final Player player = (Player) event.getEntity();
        if (plugin.doesIgnoreClaims(player)) return;
        final Entity mount = event.getMount();
        if (isOwner(player, mount)) return;
        checkPlayerAction(player, mount.getLocation().getBlock(), Action.INTERACT, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        final Player player = event.getPlayer();
        if (plugin.doesIgnoreClaims(player)) return;
        final Entity entity = event.getEntity();
        if (isOwner(player, entity)) return;
        checkPlayerAction(player, entity.getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        if (plugin.sessions.of(player).onPlayerInteract(event)) return;
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
                checkPlayerAction(event.getPlayer(), block, Action.INTERACT, event);
            }
            return;
        case RIGHT_CLICK_BLOCK:
            if (slimeballUse(player, event, block, claim)) return;
            if (mat.isInteractable()) {
                if (Tag.DOORS.isTagged(mat) || Tag.BUTTONS.isTagged(mat) || Tag.TRAPDOORS.isTagged(mat)) {
                    checkPlayerAction(player, block, Action.INTERACT, event);
                } else if (Tag.ANVIL.isTagged(mat)) {
                    checkPlayerAction(player, block, Action.CONTAINER, event);
                } else if (Tag.BEDS.isTagged(mat)) {
                    if (block.getWorld().getEnvironment() != World.Environment.NORMAL) {
                        if (claim != null && !claim.getBoolSetting(Claim.Setting.EXPLOSIONS)) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                    checkPlayerAction(player, block, Action.INTERACT, event);
                } else {
                    switch (mat) {
                    case ENCHANTING_TABLE:
                    case CRAFTING_TABLE:
                    case ENDER_CHEST:
                    case GRINDSTONE:
                    case STONECUTTER:
                    case LEVER:
                        checkPlayerAction(player, block, Action.INTERACT, event);
                        break;
                    case RESPAWN_ANCHOR:
                        if (block.getWorld().getEnvironment() != World.Environment.NETHER) {
                            RespawnAnchor data = (RespawnAnchor) block.getBlockData();
                            if (data.getCharges() >= data.getMaximumCharges() && claim != null && !claim.getBoolSetting(Claim.Setting.EXPLOSIONS)) {
                                event.setCancelled(true);
                                return;
                            }
                        }
                        checkPlayerAction(player, block, Action.BUILD, event);
                        break;
                    default:
                        if (block.getState() instanceof InventoryHolder) {
                            checkPlayerAction(player, block, Action.CONTAINER, event);
                        } else {
                            checkPlayerAction(player, block, Action.BUILD, event);
                        }
                        break;
                    }
                }
            } else {
                checkPlayerAction(player, block, Action.INTERACT, event);
            }
            return;
        case LEFT_CLICK_BLOCK:
            checkPlayerAction(player, block, Action.INTERACT, event);
            return;
        default:
            break;
        }
    }

    /**
     * @return true if this is the valid use of a slimeball, false otherwise.
     */
    boolean slimeballUse(Player player, PlayerInteractEvent event, Block block, Claim claim) {
        if (claim == null) return false;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.SLIME_BALL) return false;
        if (!block.getType().isSolid()) return false;
        if (event.getBlockFace() != BlockFace.UP) return false;
        if (block.getY() > 40) return false;
        if (!claim.canBuild(player)) return false;
        if (block.getChunk().isSlimeChunk()) {
            player.sendMessage(ChatColor.GREEN + "Slime chunk!");
            Location loc = block.getRelative(event.getBlockFace())
                .getLocation().add(0.5, 0.05, 0.5);
            player.playSound(loc, Sound.BLOCK_SLIME_BLOCK_BREAK,
                             SoundCategory.BLOCKS, 1.0f, 1.0f);
            player.spawnParticle(Particle.SLIME, loc, 8,
                                 0.25, 0.0, 0.25, 0);
        } else {
            player.sendMessage(ChatColor.RED + "Not a slime chunk.");
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityInteract(EntityInteractEvent event) {
        Block block = event.getBlock();
        Claim claim = plugin.getClaimAt(block);
        if (claim == null) return;
        Material mat = block.getType();
        if (mat == Material.FARMLAND || mat == Material.TURTLE_EGG) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityPlace(EntityPlaceEvent event) {
        checkPlayerAction(event.getPlayer(), event.getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!plugin.isHomeWorld(event.getEntity().getWorld())) return;
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            Claim claim = plugin.getClaimAt(event.getEntity().getLocation().getBlock());
            if (claim == null || !claim.getBoolSetting(Claim.Setting.EXPLOSIONS)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingPlace(HangingPlaceEvent event) {
        Block block = event.getEntity().getLocation().getBlock();
        checkPlayerAction(event.getPlayer(), block, Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player) {
            final Player player = (Player) event.getRemover();
            Block block = event.getEntity().getLocation().getBlock();
            checkPlayerAction(player, block, Action.BUILD, event);
        }
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            Claim claim = plugin.getClaimAt(event.getEntity().getLocation().getBlock());
            if (claim == null || !claim.getBoolSetting(Claim.Setting.EXPLOSIONS)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        checkPlayerAction(event.getPlayer(), event.getBlockClicked(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        checkPlayerAction(event.getPlayer(), event.getBlockClicked(), Action.BUCKET, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.isHomeWorld(event.getEntity().getWorld())) return;
        for (Iterator<Block> iter = event.blockList().iterator(); iter.hasNext();) {
            Claim claim = plugin.getClaimAt(iter.next());
            if (claim == null || !claim.getBoolSetting(Claim.Setting.EXPLOSIONS)) {
                iter.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!plugin.isHomeWorld(event.getBlock().getWorld())) return;
        Claim claim = plugin.getClaimAt(event.getBlock());
        if (claim != null && !claim.getBoolSetting(Claim.Setting.EXPLOSIONS)) {
            event.setCancelled(true);
            return;
        }
        for (Iterator<Block> iter = event.blockList().iterator(); iter.hasNext();) {
            claim = plugin.getClaimAt(iter.next());
            if (claim == null || !claim.getBoolSetting(Claim.Setting.EXPLOSIONS)) {
                iter.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!plugin.isHomeWorld(event.getBlock().getWorld())) return;
        Claim claim = plugin.getClaimAt(event.getBlock());
        if (claim == null || !claim.getBoolSetting(Claim.Setting.FIRE)) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!plugin.isHomeWorld(event.getBlock().getWorld())) return;
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
        if (claim == null || !claim.getBoolSetting(Claim.Setting.FIRE)) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!plugin.isHomeWorld(event.getEntity().getWorld())) return;
        Player player = getPlayerDamager(event.getEntity());
        if (player != null) {
            checkPlayerAction(player, event.getBlock(), Action.BUILD, event);
        } else if (event.getEntity().getType() == EntityType.ENDERMAN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (plugin.doesIgnoreClaims(player)) return;
        if (!plugin.isHomeWorld(player.getWorld())) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder == null) return;
        if (holder instanceof Entity) {
            if (holder.equals(player)) return;
            if (isOwner(player, (Entity) holder)) return;
            Block block = ((Entity) holder).getLocation().getBlock();
            checkPlayerAction(player, block, Action.CONTAINER, event);
        } else if (holder instanceof Lectern) {
            Block block = ((Lectern) holder).getBlock();
            if (block == null) return; // @NotNull
            checkPlayerAction(player, block, Action.INTERACT, event);
        } else if (holder instanceof BlockState) {
            // One block containers, as opposed to double chest
            Block block = ((BlockState) holder).getBlock();
            checkPlayerAction(player, block, Action.CONTAINER, event);
        } else if (holder instanceof DoubleChest) {
            Block block = ((DoubleChest) holder).getLocation().getBlock();
            checkPlayerAction(player, block, Action.CONTAINER, event);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        if (!plugin.isHomeWorld(event.getBlock().getWorld())) return;
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
        if (!plugin.isHomeWorld(event.getBlock().getWorld())) return;
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    void onCreatureSpawn(CreatureSpawnEvent event) {
        onCreatureSpawn(event, event.getSpawnReason(), event.getEntityType(), event.getLocation());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    void onPreCreatureSpawn(PreCreatureSpawnEvent event) {
        onCreatureSpawn(event, event.getReason(), event.getType(), event.getSpawnLocation());
    }

    void onCreatureSpawn(Cancellable event, CreatureSpawnEvent.SpawnReason reason, EntityType entityType, Location location) {
        if (!plugin.isHomeWorld(location.getWorld())) return;
        if (entityType == EntityType.PHANTOM && reason != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
        if (entityType == EntityType.WITHER && reason != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
        Claim claim = plugin.getClaimAt(location);
        if (claim == null) return;
        if (!claim.getBoolSetting(Claim.Setting.MOB_SPAWNING)) {
            switch (reason) {
            case BREEDING:
            case BUILD_WITHER:
            case JOCKEY:
            case LIGHTNING:
            case NATURAL:
            case OCELOT_BABY:
            case PATROL:
            case RAID:
            case REINFORCEMENTS:
            case TRAP:
            case VILLAGE_DEFENSE:
            case VILLAGE_INVASION:
                event.setCancelled(true);
                return;
            default: break;
            }
        }
        if (!claim.isAdminClaim() && isHostileMob(entityType) && location.getWorld().getEnvironment() == World.Environment.NORMAL) {
            switch (entityType) {
                // These are exempt
            case SLIME:
            case GUARDIAN:
            case ELDER_GUARDIAN:
                break;
            default:
                switch (reason) {
                case NATURAL:
                case REINFORCEMENTS:
                case VILLAGE_INVASION:
                case TRAP: // Skeleton riders(?)
                    Block block = location.getBlock();
                    int light = block.getLightFromBlocks();
                    if (light > 0) {
                        event.setCancelled(true);
                        return;
                    }
                    int sunlight = block.getLightFromSky();
                    if (sunlight > 0) {
                        Block baseBlock = location.getBlock().getRelative(0, -1, 0);
                        if (!baseBlock.isEmpty() && !baseBlock.isLiquid() && Exploits.isPlayerPlaced(baseBlock)) {
                            event.setCancelled(true);
                        }
                    }
                    break;
                default: break;
                }
                break;
            }
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("dynmap")) {
            plugin.enableDynmap();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("dynmap")) {
            plugin.disableDynmap();
        }
    }

    @EventHandler
    public void onPlayerTakeLecternBook(PlayerTakeLecternBookEvent event) {
        Player player = event.getPlayer();
        Block block = event.getLectern().getBlock();
        if (block == null) return; // says @NotNull
        checkPlayerAction(player, block, Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true)
    void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        Player player = event.getPlayer();
        Claim claim = plugin.getClaimAt(player.getLocation());
        if (claim == null) return;
        if (!claim.getBoolSetting(Claim.Setting.ENDER_PEARL)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onEntityToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        Claim claim = plugin.getClaimAt(player.getLocation());
        if (claim == null) return;
        if (!claim.getBoolSetting(Claim.Setting.ELYTRA)) {
            if (event.isGliding()) {
                event.setCancelled(true);
                plugin.getServer().getScheduler().runTask(plugin,
                                                          () -> player.setGliding(false));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerCanBuild(PlayerCanBuildEvent event) {
        checkPlayerAction(event.getPlayer(), event.getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        EntityType entityType = event.getEntityType();
        Location loc = event.getLocation();
        World world = loc.getWorld();
        if (!plugin.isHomeWorld(world)) return;
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
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!(player.hasPermission("home.admin"))) continue;
            player.sendMessage(ChatColor.RED + "[Home] " + msg);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        Vehicle vehicle = event.getVehicle();
        checkPlayerAction(player, vehicle.getLocation().getBlock(), Action.VEHICLE, event);
    }
}
