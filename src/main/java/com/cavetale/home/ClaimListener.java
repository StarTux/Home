package com.cavetale.home;

import java.util.Iterator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
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
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.InventoryHolder;
import org.spigotmc.event.entity.EntityMountEvent;

@RequiredArgsConstructor
final class ClaimListener implements Listener {
    private final HomePlugin plugin;

    enum Action {
        BUILD,
        INTERACT,
        COMBAT,
        VEHICLE,
        BUCKET;
    }

    // Utilty

    /**
     * Check if a player action is permissible and cancel it if not.
     * If the action is in a world not subject to this plugin, nothing
     * will be cancelled and true returned.
     *
     * @return True if the event is permitted, false otherwise.
     */
    private boolean checkPlayerAction(Player player, Block block,
                                      Action action, Cancellable cancellable) {
        if (player.hasMetadata(plugin.META_IGNORE) || player.isOp()) return true;
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
            // Action is not in a claim.  Apply default permissions.
            // Building is not allowed, combat is.
            switch (action) {
            case BUILD:
                if (cancellable != null) cancellable.setCancelled(true);
                return false;
            case COMBAT:
            case INTERACT:
            case VEHICLE:
            case BUCKET:
            default:
                return true;
            }
        }
        // We know there is a claim, so return on the player is
        // privileged here.  The owner and members can do anything.
        UUID uuid = player.getUniqueId();
        if (claim.canBuild(uuid)) return true;
        if (claim.getSetting(Claim.Setting.PUBLIC) == Boolean.TRUE) return true;
        // Visitors may interact and do combat.
        if (claim.canVisit(uuid) || claim.getSetting(Claim.Setting.PUBLIC_INVITE) == Boolean.TRUE) {
            switch (action) {
            case COMBAT:
            case INTERACT:
                return true;
            case BUILD:
            case VEHICLE:
            case BUCKET:
            default:
                // Forbidden actions are cancelled further down.
                break;
            }
        } else {
            switch (action) {
            case COMBAT: return true;
            default: break;
            }
        }
        // Action is not covered by visitor, member, or owner
        // privilege.  Therefore, nothing is allowed.
        if (cancellable != null) cancellable.setCancelled(true);
        return false;
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
            return true;
        default:
            return entity instanceof Monster;
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

    void noClaimWarning(Player player, long now) {
        long time = plugin.getMetadata(player, plugin.META_NOCLAIM_WARN, Long.class).orElse(0L);
        if (now - time < 10000000000L) return;
        ComponentBuilder cb = new ComponentBuilder("");
        cb.append("You did not ").color(ChatColor.RED);
        cb.append("claim").color(ChatColor.YELLOW);
        cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim"));
        BaseComponent[] tooltip = TextComponent
            .fromLegacyText(ChatColor.YELLOW + "/claim\n"
                            + ChatColor.WHITE + ChatColor.ITALIC
                            + "Claim land and make it yours.");
        cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip));
        cb.append(" this area. Mining and building is limited.", FormatRetention.NONE)
            .color(ChatColor.RED);
        player.spigot().sendMessage(cb.create());
        cb = new ComponentBuilder("");
        cb.append("Consider visiting the ").color(ChatColor.RED);
        cb.append("mining").color(ChatColor.YELLOW);
        cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mine"));
        BaseComponent[] tooltip = TextComponent
            .fromLegacyText(ChatColor.YELLOW + "/mine\n" + ChatColor.WHITE + ChatColor.ITALIC
                            + "Visit the mining world which can be raided"
                            + " and will be reset regularly.");
        cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip));
        cb.append(" world.").color(ChatColor.RED);
        player.spigot().sendMessage(cb.create());
        player.playSound(player.getEyeLocation(), Sound.ENTITY_POLAR_BEAR_WARNING,
                         SoundCategory.MASTER, 2.0f, 1.0f);
        plugin.setMetadata(player, plugin.META_NOCLAIM_WARN, now);
    }

    boolean noClaimBuild(Player player, Block block) {
        long now = System.nanoTime();
        noClaimWarning(player, now);
        long noClaimCount = 0L;
        if (plugin.findNearbyBuildClaim(player, 48) != null) return true;
        long noClaimTime = plugin.getMetadata(player, plugin.META_NOCLAIM_TIME, Long.class)
            .orElse(0L);
        if (now - noClaimTime > 30000000000L) {
            noClaimTime = now;
            noClaimCount = 1L;
        } else {
            noClaimCount = plugin.getMetadata(player, plugin.META_NOCLAIM_COUNT, Long.class)
                .orElse(0L);
            noClaimCount += 1L;
            if (noClaimCount > 10L) return false;
        }
        plugin.setMetadata(player, plugin.META_NOCLAIM_TIME, noClaimTime);
        plugin.setMetadata(player, plugin.META_NOCLAIM_COUNT, noClaimCount);
        return true;
    }

    // Events

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!plugin.isHomeWorld(block.getWorld())) return;
        Claim claim = plugin.getClaimAt(block);
        Player player = event.getPlayer();
        if (player.isOp() || player.hasMetadata(plugin.META_IGNORE)) return;
        if (claim == null) {
            switch (block.getType()) {
            case CHEST:
            case SPAWNER:
            case TRAPPED_CHEST:
            case IRON_ORE:
            case DIAMOND_ORE:
            case GOLD_ORE:
            case EMERALD_ORE:
            case COAL_ORE:
            case DRAGON_EGG:
            case DRAGON_HEAD:
                event.setCancelled(true);
                return;
            default:
                break;
            }
            if (!noClaimBuild(player, block)) event.setCancelled(true);
            return;
        }
        checkPlayerAction(player, block, Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!plugin.isHomeWorld(block.getWorld())) return;
        Claim claim = plugin.getClaimAt(block);
        Player player = event.getPlayer();
        if (player.isOp() || player.hasMetadata(plugin.META_IGNORE)) return;
        if (claim == null) {
            switch (block.getType()) {
            case FIRE:
            case LAVA:
            case TNT:
                event.setCancelled(true);
                return;
            default:
                break;
            }
            if (!noClaimBuild(player, block)) event.setCancelled(true);
            return;
        }
        checkPlayerAction(event.getPlayer(), event.getBlock(), Action.BUILD, event);
    }

    // Frost Walker
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        checkPlayerAction((Player) event.getEntity(), event.getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        final Entity entity = event.getEntity();
        if (!plugin.isHomeWorld(entity.getWorld())) return;
        final Player player = getPlayerDamager(event.getDamager());
        if (player != null && entity instanceof Player) {
            if (player.hasMetadata(plugin.META_IGNORE)) return;
            // PvP
            if (player.equals(entity)) return;
            Claim claim = plugin.getClaimAt(entity.getLocation().getBlock());
            if (claim == null) {
                // PvP is disabled in claim worlds, outside of claims
                event.setCancelled(true);
                return;
            }
            if (claim.getSetting(Claim.Setting.PVP) == Boolean.TRUE) return;
            event.setCancelled(true);
        } else if (player != null) {
            boolean claimed = plugin.getClaimAt(entity.getLocation().getBlock()) != null;
            Action action;
            if (claimed && entity.getType() == EntityType.SHULKER) {
                // Some extra code for hostile, yet valuable mobs in
                // claims.
                action = Action.BUILD;
            } else if (isHostileMob(entity)) {
                action = Action.COMBAT;
            } else {
                action = Action.BUILD;
            }
            checkPlayerAction(player, entity.getLocation().getBlock(), action, event);
        } else {
            switch (event.getCause()) {
            case BLOCK_EXPLOSION:
            case ENTITY_EXPLOSION:
                Claim claim = plugin.getClaimAt(entity.getLocation().getBlock());
                if (claim == null) {
                    return;
                }
                if (claim.getSetting(Claim.Setting.EXPLOSIONS) == Boolean.TRUE) return;
                if (entity instanceof Player) return;
                if (entity instanceof Mob) return;
                event.setCancelled(true);
                break;
            default:
                break;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        if (!plugin.isHomeWorld(event.getEntity().getWorld())) return;
        Player damager = getPlayerDamager(event.getCombuster());
        if (damager == null) return;
        if (damager.hasMetadata(plugin.META_IGNORE)) return;
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            if (damager.equals(entity)) return;
            Claim claim = plugin.getClaimAt(event.getEntity().getLocation().getBlock());
            if (claim == null || claim.getSetting(Claim.Setting.PVP) != Boolean.TRUE) {
                event.setCancelled(true);
            }
            return;
        }
        if (isOwner(damager, entity)) return;
        checkPlayerAction(damager, entity.getLocation().getBlock(), Action.BUILD, event);
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
        if (player.isOp() || player.hasMetadata(plugin.META_IGNORE)) return;
        final Entity entity = event.getRightClicked();
        if (isOwner(player, entity)) return;
        checkPlayerAction(player, entity.getLocation().getBlock(), Action.BUILD, event);
    }

    // Should this just be the same as onPlayerInteractEntity() ?
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        final Player player = event.getPlayer();
        if (player.isOp() || player.hasMetadata(plugin.META_IGNORE)) return;
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
        if (player.isOp() || player.hasMetadata(plugin.META_IGNORE)) return;
        final Entity entity = event.getEntity();
        if (isOwner(player, entity)) return;
        checkPlayerAction(player, entity.getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        final Player player = (Player) event.getEntity();
        if (player.isOp() || player.hasMetadata(plugin.META_IGNORE)) return;
        final Entity mount = event.getMount();
        if (isOwner(player, mount)) return;
        checkPlayerAction(player, mount.getLocation().getBlock(), Action.INTERACT, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        final Player player = event.getPlayer();
        if (player.isOp() || player.hasMetadata(plugin.META_IGNORE)) return;
        final Entity entity = event.getEntity();
        if (isOwner(player, entity)) return;
        checkPlayerAction(player, entity.getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        if (player.isOp() || player.hasMetadata(plugin.META_IGNORE)) return;
        final Block block = event.getClickedBlock();
        if (block == null) return;
        // Consider soil trampling
        switch (event.getAction()) {
        case PHYSICAL:
            if (block.getType() == Material.FARMLAND) {
                checkPlayerAction(event.getPlayer(), block, Action.BUILD, event);
            } else {
                checkPlayerAction(event.getPlayer(), block, Action.INTERACT, event);
            }
            return;
        case RIGHT_CLICK_BLOCK:
            switch (block.getType()) {
            case ANVIL: checkPlayerAction(player, block, Action.BUILD, event); break;
            default: checkPlayerAction(player, block, Action.INTERACT, event); break;
            }
            return;
        case LEFT_CLICK_BLOCK:
            checkPlayerAction(player, block, Action.INTERACT, event);
            return;
        default:
            break;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!plugin.isHomeWorld(event.getEntity().getWorld())) return;
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            Claim claim = plugin.getClaimAt(event.getEntity().getLocation().getBlock());
            if (claim == null || claim.getSetting(Claim.Setting.EXPLOSIONS) != Boolean.TRUE) {
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
            if (claim == null || claim.getSetting(Claim.Setting.EXPLOSIONS) != Boolean.TRUE) {
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
            if (claim == null || claim.getSetting(Claim.Setting.EXPLOSIONS) != Boolean.TRUE) {
                iter.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!plugin.isHomeWorld(event.getBlock().getWorld())) return;
        Claim claim = plugin.getClaimAt(event.getBlock());
        if (claim == null || claim.getSetting(Claim.Setting.FIRE) != Boolean.TRUE) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!plugin.isHomeWorld(event.getBlock().getWorld())) return;
        switch (event.getCause()) {
        case ENDER_CRYSTAL:
        case EXPLOSION:
        case FIREBALL:
        case FLINT_AND_STEEL:
            return;
        case LAVA:
        case LIGHTNING:
        case SPREAD:
        default:
            break;
        }
        Claim claim = plugin.getClaimAt(event.getBlock());
        if (claim == null || claim.getSetting(Claim.Setting.FIRE) != Boolean.TRUE) {
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
        if (player.isOp() || player.hasMetadata(plugin.META_IGNORE)) return;
        if (!plugin.isHomeWorld(player.getWorld())) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder == null) return;
        if (holder instanceof Entity) {
            if (holder.equals(player)) return;
            if (isOwner(player, (Entity) holder)) return;
            Block block = ((Entity) holder).getLocation().getBlock();
            checkPlayerAction(player, block, Action.BUILD, event);
        } else if (holder instanceof BlockState) {
            Block block = ((BlockState) holder).getBlock();
            checkPlayerAction(player, block, Action.BUILD, event);
        } else if (holder instanceof DoubleChest) {
            Block block = ((DoubleChest) holder).getLocation().getBlock();
            checkPlayerAction(player, block, Action.BUILD, event);
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
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!plugin.isHomeWorld(event.getEntity().getLocation().getWorld())) return;
        if (event.getEntity().getType() == EntityType.PHANTOM
            && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
        if (event.getEntity().getType() == EntityType.WITHER
            && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
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
}
