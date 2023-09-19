package com.cavetale.home;

import com.cavetale.home.sql.SQLHome;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class Homes implements Iterable<SQLHome> {
    private TreeMap<Integer, SQLHome> idMap = new TreeMap<>();
    private HashMap<UUID, HashMap<String, SQLHome>> ownerMap = new HashMap<>();
    private HashMap<String, SQLHome> publicNameMap = new HashMap<>();

    public void clear() {
        idMap.clear();
        ownerMap.clear();
        publicNameMap.clear();
    }

    public void add(SQLHome home) {
        if (home.getId() != null) {
            idMap.put(home.getId(), home);
        }
        ownerMap.computeIfAbsent(home.getOwner(), u -> new HashMap<>()).put(home.getName(), home);
        if (home.isPublic()) {
            publicNameMap.put(home.getPublicName(), home);
        }
    }

    public void remove(SQLHome home) {
        if (home.getId() != null) {
            idMap.remove(home.getId());
        }
        ownerMap.computeIfAbsent(home.getOwner(), u -> new HashMap<>()).remove(home.getName());
        if (home.isPublic()) {
            publicNameMap.remove(home.getPublicName());
        }
    }

    public SQLHome findById(int id) {
        return idMap.get(id);
    }

    public SQLHome findOwnedHome(UUID uuid, String name) {
        return ownerMap.computeIfAbsent(uuid, u -> new HashMap<>()).get(name);
    }

    public SQLHome findPrimaryHome(UUID uuid) {
        return ownerMap.computeIfAbsent(uuid, u -> new HashMap<>()).get(null);
    }

    public List<SQLHome> findOwnedHomes(UUID uuid) {
        List<SQLHome> result = new ArrayList<>(ownerMap.computeIfAbsent(uuid, u -> new HashMap<>()).values());
        result.sort(SQLHome.NAME_COMPARATOR);
        return result;
    }

    public List<SQLHome> getPublicHomes() {
        return new ArrayList<>(publicNameMap.values());
    }

    public SQLHome findPublicHome(String publicName) {
        return publicNameMap.get(publicName);
    }

    @Override
    public Iterator<SQLHome> iterator() {
        return idMap.values().iterator();
    }

    public List<SQLHome> all() {
        return new ArrayList<>(idMap.values());
    }
}
