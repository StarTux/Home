package com.cavetale.home;

import com.winthier.sql.SQLDatabase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Value;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class HomePlugin extends JavaPlugin {
    // Globals
    static final String META_LOCATION = "home.location";
    static final String META_BUY = "home.buyclaimblocks";
    static final String META_ABANDON = "home.abandonclaim";
    static final String META_NEWCLAIM = "home.newclaim";
    static final String META_IGNORE = "home.ignore";
    // Database
    SQLDatabase db;
    // Worlds
    String primaryHomeWorld;
    final Map<String, WorldSettings> worldSettings = new HashMap<>();
    final Map<String, String> mirrorWorlds = new HashMap<>();
    // Homes
    final List<Home> homes = new ArrayList<>();
    final List<String> homeWorlds = new ArrayList<>();
    // Claims
    final List<Claim> claims = new ArrayList<>();
    // Utilty
    long ticks;
    Random random = ThreadLocalRandom.current();
    // Interface
    DynmapClaims dynmapClaims;
    // Commands
    final HomeAdminCommand homeAdminCommand = new HomeAdminCommand(this);
    final ClaimCommand claimCommand = new ClaimCommand(this);
    final HomeCommand homeCommand = new HomeCommand(this);
    final HomesCommand homesCommand = new HomesCommand(this);
    final VisitCommand visitCommand = new VisitCommand(this);
    final SetHomeCommand setHomeCommand = new SetHomeCommand(this);
    final BuildCommand buildCommand = new BuildCommand(this);
    final InviteHomeCommand inviteHomeCommand = new InviteHomeCommand(this);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        db = new SQLDatabase(this);
        db.registerTables(Claim.SQLRow.class, ClaimTrust.class, Home.class,
                          HomeInvite.class);
        db.createAllTables();
        getServer().getPluginManager().registerEvents(new ClaimListener(this), this);
        getCommand("homeadmin").setExecutor(homeAdminCommand);
        getCommand("claim").setExecutor(claimCommand);
        getCommand("home").setExecutor(homeCommand);
        getCommand("homes").setExecutor(homesCommand);
        getCommand("visit").setExecutor(visitCommand);
        getCommand("sethome").setExecutor(setHomeCommand);
        getCommand("invitehome").setExecutor(inviteHomeCommand);
        getCommand("build").setExecutor(buildCommand);
        loadFromConfig();
        loadFromDatabase();
        getServer().getScheduler().runTaskTimer(this, this::onTick, 1, 1);
        if (getServer().getPluginManager().isPluginEnabled("dynmap")) {
            enableDynmap();
        }
    }

    @Override
    public void onDisable() {
        claims.clear();
        homes.clear();
        disableDynmap();
    }

    // --- Inner classes for utility

    @Value
    class CachedLocation {
        final String world;
        final int x;
        final int z;
        final int claimId;
    }

    // --- Ticking

    void onTick() {
        ticks += 1;
        for (Player player : getServer().getOnlinePlayers()) {
            tickPlayer(player);
        }
        if ((ticks % 200L) == 0L) {
            if (dynmapClaims != null) dynmapClaims.update();
        }
    }

    void tickPlayer(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (!isHomeWorld(player.getWorld())) {
            player.removeMetadata(META_LOCATION, this);
            return;
        }
        Location pl = player.getLocation();
        Claim claim = getClaimAt(pl);
        if (claim != null
            && claim.isOwner(player) && (ticks % 100) == 0
            && claim.getBlocks() > claim.getArea().size()
            && claim.getBoolSetting(Claim.Setting.AUTOGROW)) {
            if (autoGrowClaim(claim)) {
                highlightClaim(claim, player);
            }
        }
        CachedLocation cl1 = getMetadata(player, META_LOCATION, CachedLocation.class)
            .orElse(null);
        if (cl1 == null || !cl1.world.equals(pl.getWorld().getName())
            || cl1.x != pl.getBlockX() || cl1.z != pl.getBlockZ()) {
            CachedLocation cl2 = new CachedLocation(pl.getWorld().getName(),
                                                    pl.getBlockX(), pl.getBlockZ(),
                                                    claim == null ? -1 : claim.getId());
            if (cl1 == null) cl1 = cl2; // Taking the easy way out
            player.setMetadata(META_LOCATION, new FixedMetadataValue(this, cl2));
            if (claim == null) {
                if (cl1.claimId != cl2.claimId) {
                    Claim oldClaim = getClaimById(cl1.claimId);
                    if (oldClaim != null) {
                        if (!oldClaim.getBoolSetting(Claim.Setting.HIDDEN)) {
                            if (oldClaim.isOwner(player)) {
                                BaseComponent[] txt = TextComponent
                                    .fromLegacyText(ChatColor.GRAY + "Leaving your claim");
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, txt);
                            } else {
                                BaseComponent[] txt = TextComponent
                                    .fromLegacyText(ChatColor.GRAY + "Leaving "
                                                    + oldClaim.getOwnerName() + "'s claim");
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, txt);
                            }
                            if (oldClaim.getBoolSetting(Claim.Setting.SHOW_BORDERS)) {
                                highlightClaim(oldClaim, player);
                            }
                        }
                    }
                }
            } else { // (claim != null)
                if (cl1.claimId != cl2.claimId) {
                    if (!claim.getBoolSetting(Claim.Setting.HIDDEN)) {
                        if (claim.isOwner(player)) {
                            BaseComponent[] txt = TextComponent
                                .fromLegacyText(ChatColor.GRAY + "Entering your claim");
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, txt);
                        } else {
                            BaseComponent[] txt = TextComponent
                                .fromLegacyText(ChatColor.GRAY + "Entering "
                                                + claim.getOwnerName() + "'s claim");
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, txt);
                        }
                        if (claim.getBoolSetting(Claim.Setting.SHOW_BORDERS)) {
                            highlightClaim(claim, player);
                        }
                    }
                }
            }
        }
    }

    // --- Player interactivity

    void findPlaceToBuild(Player player) throws PlayerCommand.Wrong {
        // Determine center and border
        String worldName = primaryHomeWorld; // Set up for future expansion
        World bworld = getServer().getWorld(worldName);
        if (bworld == null) {
            getLogger().warning("Home world not found: " + worldName);
            throw new PlayerCommand
                .Wrong("Something went wrong. Please contact an administrator.");
        }
        WildTask wildTask = new WildTask(this, bworld, player);
        wildTask.withCooldown();
    }

    boolean autoGrowClaim(Claim claim) {
        Area area = claim.getArea();
        Area newArea = new Area(area.ax - 1, area.ay - 1, area.bx + 1, area.by + 1);
        if (newArea.size() > claim.getBlocks()) return false;
        String claimWorld = claim.getWorld();
        for (Claim other : claims) {
            if (other != claim && other.isInWorld(claimWorld)
                && other.getArea().overlaps(newArea)) {
                return false;
            }
        }
        claim.setArea(newArea);
        claim.saveToDatabase();
        return true;
    }

    // --- Configuration utility

    void loadFromConfig() {
        reloadConfig();
        ConfigurationSection section = getConfig().getConfigurationSection("Worlds");
        homeWorlds.clear();
        worldSettings.clear();
        for (String key : section.getKeys(false)) {
            ConfigurationSection worldSection = section.getConfigurationSection(key);
            WorldSettings settings = new WorldSettings();
            worldSettings.put(key, settings);
            settings.load(getConfig());
            if (worldSection != null) {
                settings.load(worldSection);
                if (worldSection.isSet("mirror")) {
                    mirrorWorlds.put(key, worldSection.getString("mirror"));
                }
            }
            homeWorlds.add(key);
        }
        if (!homeWorlds.isEmpty()) {
            primaryHomeWorld = homeWorlds.get(0);
        } else {
            primaryHomeWorld = getServer().getWorlds().get(0).getName();
        }
    }

    void loadFromDatabase() {
        claims.clear();
        homes.clear();
        for (Claim.SQLRow row : db.find(Claim.SQLRow.class).findList()) {
            Claim claim = new Claim(this);
            claim.loadSQLRow(row);
            claims.add(claim);
        }
        for (ClaimTrust trust : db.find(ClaimTrust.class).findList()) {
            for (Claim claim : claims) {
                if (claim.id.equals(trust.claimId)) {
                    switch (trust.type) {
                    case "visit": claim.visitors.add(trust.trustee); break;
                    case "member": claim.members.add(trust.trustee); break;
                    default: break;
                    }
                    break;
                }
            }
        }
        homes.addAll(db.find(Home.class).findList());
        for (HomeInvite invite : db.find(HomeInvite.class).findList()) {
            for (Home home : homes) {
                if (home.id.equals(invite.homeId)) {
                    home.invites.add(invite.getInvitee());
                    break;
                }
            }
        }
    }

    String worldDisplayName(String worldName) {
        World world = getServer().getWorld(worldName);
        if (world == null) return worldName;
        switch (world.getEnvironment()) {
        case NORMAL: return "overworld";
        case NETHER: return "nether";
        case THE_END: return "end";
        default: return worldName;
        }
    }

    // --- Claim Utility

    boolean isHomeWorld(World world) {
        return homeWorlds.contains(world.getName());
    }

    Claim getClaimById(int claimId) {
        return claims.stream().filter(c -> c.getId() == claimId).findFirst().orElse(null);
    }

    Claim getClaimAt(Block block) {
        return getClaimAt(block.getWorld().getName(), block.getX(), block.getZ());
    }

    Claim getClaimAt(Location location) {
        return getClaimAt(location.getWorld().getName(),
                          location.getBlockX(),
                          location.getBlockZ());
    }

    Claim getClaimAt(String w, int x, int y) {
        final String world = mirrorWorlds.containsKey(w) ? mirrorWorlds.get(w) : w;
        return claims.stream()
            .filter(c -> c.isInWorld(world) && c.getArea().contains(x, y))
            .findFirst().orElse(null);
    }

    Claim findNearestOwnedClaim(Player player) {
        Location playerLocation = player.getLocation();
        String playerWorld = playerLocation.getWorld().getName();
        final String w = mirrorWorlds.containsKey(playerWorld)
            ? mirrorWorlds.get(playerWorld)
            : playerWorld;
        int x = playerLocation.getBlockX();
        int z = playerLocation.getBlockZ();
        return claims.stream()
            .filter(c -> c.isOwner(player) && c.isInWorld(w))
            .min((a, b) -> Integer.compare(a.getArea().distanceToPoint(x, z),
                                           b.getArea().distanceToPoint(x, z)))
            .orElse(null);
    }

    Claim findNearbyBuildClaim(Player player, int radius) {
        Location playerLocation = player.getLocation();
        String playerWorld = playerLocation.getWorld().getName();
        final String w = mirrorWorlds.containsKey(playerWorld)
            ? mirrorWorlds.get(playerWorld)
            : playerWorld;
        int x = playerLocation.getBlockX();
        int z = playerLocation.getBlockZ();
        return claims.stream()
            .filter(c -> c.canBuild(player)
                    && c.isInWorld(w)
                    && c.getArea().isWithin(x, z, radius))
            .findFirst().orElse(null);
    }

    void highlightClaim(Claim claim, Player player) {
        List<Block> blocks = new ArrayList<>();
        Area area = claim.getArea();
        Location playerLocation = player.getLocation();
        int playerX = playerLocation.getBlockX();
        int playerY = playerLocation.getBlockY();
        int playerZ = playerLocation.getBlockZ();
        World world = player.getWorld();
        final int maxd = 16 * 6;
        // Upper X axies
        if (Math.abs(area.ay - playerZ) <= maxd) {
            for (int x = area.ax; x <= area.bx; x += 1) {
                if (Math.abs(x - playerX) <= maxd && world.isChunkLoaded(x >> 4, area.ay >> 4)) {
                    blocks.add(world.getBlockAt(x, playerY, area.ay));
                }
            }
        }
        // Lower X axis
        if (Math.abs(area.by - playerZ) <= maxd) {
            for (int x = area.ax; x <= area.bx; x += 1) {
                if (Math.abs(x - playerX) <= maxd && world.isChunkLoaded(x >> 4, area.by >> 4)) {
                    blocks.add(world.getBlockAt(x, playerY, area.by));
                }
            }
        }
        // Left Z axis
        if (Math.abs(area.ax - playerX) <= maxd) {
            for (int z = area.ay; z < area.by; z += 1) {
                if (Math.abs(z - playerZ) <= maxd && world.isChunkLoaded(area.ax >> 4, z >> 4)) {
                    blocks.add(world.getBlockAt(area.ax, playerY, z));
                }
            }
        }
        // Right Z axis
        if (Math.abs(area.bx - playerX) <= maxd) {
            for (int z = area.ay; z < area.by; z += 1) {
                if (Math.abs(z - playerZ) <= maxd && world.isChunkLoaded(area.bx >> 4, z >> 4)) {
                    blocks.add(world.getBlockAt(area.bx, playerY, z));
                }
            }
        }
        for (Block block : blocks) {
            while (block.isEmpty() && block.getY() > 0) block = block.getRelative(0, -1, 0);
            while (!block.isEmpty() && block.getY() < 127) block = block.getRelative(0, 1, 0);
            player.spawnParticle(Particle.BARRIER, block.getLocation().add(0.5, 0.5, 0.5), 1,
                                 0.0, 0.0, 0.0, 0.0);
        }
    }

    // --- Public home and claim finders

    public List<Home> findHomes(UUID owner) {
        return homes.stream().filter(h -> h.isOwner(owner)).collect(Collectors.toList());
    }

    public Home findHome(UUID owner, String name) {
        return homes.stream()
            .filter(h -> h.isOwner(owner) && h.isNamed(name))
            .findFirst().orElse(null);
    }

    public Home findPublicHome(String name) {
        return homes.stream()
            .filter(h -> name.equals(h.getPublicName()))
            .findFirst().orElse(null);
    }

    public Claim findPrimaryClaim(UUID owner) {
        for (Claim claim : claims) {
            if (claim.isOwner(owner)) return claim;
        }
        return null;
    }

    public List<Claim> findClaims(UUID owner) {
        return claims.stream().filter(c -> c.isOwner(owner)).collect(Collectors.toList());
    }

    public List<Claim> findClaims(Player player) {
        return claims.stream().filter(c -> c.isOwner(player)).collect(Collectors.toList());
    }

    public List<Claim> findClaimsInWorld(UUID owner, String w) {
        final String world = mirrorWorlds.containsKey(w) ? mirrorWorlds.get(w) : w;
        return claims.stream()
            .filter(c -> c.isOwner(owner) && c.isInWorld(world))
            .collect(Collectors.toList());
    }

    public List<Claim> findClaimsInWorld(String w) {
        final String world = mirrorWorlds.containsKey(w) ? mirrorWorlds.get(w) : w;
        return claims.stream().filter(c -> c.isInWorld(world)).collect(Collectors.toList());
    }

    public Claim findClaimWithId(int id) {
        for (Claim claim : claims) {
            if (claim.getId() == id) return claim;
        }
        return null;
    }

    public void deleteClaim(Claim claim) {
        int claimId = claim.getId();
        db.find(Claim.SQLRow.class).eq("id", claimId).delete();
        db.find(ClaimTrust.class).eq("claimId", claimId).delete();
        claims.remove(claim);
    }

    // --- Metadata

    <T> Optional<T> getMetadata(Metadatable entity, String key, Class<T> clazz) {
        for (MetadataValue meta : entity.getMetadata(key)) {
            if (meta.getOwningPlugin() == this) return Optional.of(clazz.cast(meta.value()));
        }
        return Optional.empty();
    }

    void setMetadata(Metadatable entity, String key, Object value) {
        entity.setMetadata(key, new FixedMetadataValue(this, value));
    }

    void removeMetadata(Metadatable entity, String key) {
        entity.removeMetadata(key, this);
    }

    boolean doesIgnoreClaims(Player player) {
        return player.hasMetadata(META_IGNORE);
    }

    // --- Dynmap

    void enableDynmap() {
        try {
            dynmapClaims = new DynmapClaims(this);
            dynmapClaims.update();
        } catch (Exception e) {
            getLogger().warning("Cancelling connection with dynmap.");
            e.printStackTrace();
            dynmapClaims = null;
        }
    }

    void disableDynmap() {
        if (dynmapClaims != null) {
            dynmapClaims.disable();
            dynmapClaims = null;
        }
    }
}
