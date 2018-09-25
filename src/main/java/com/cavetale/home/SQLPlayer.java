package com.cavetale.home;

import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.Data;

/**
 * Store some player information.
 */
@Data
@Table(name = "players",
       uniqueConstraints = {@UniqueConstraint(columnNames = {"uuid"})})
public final class SQLPlayer {
    @Id Integer id;
    @Column(nullable = false) UUID uuid;
    @Column(nullable = false, length = 1024) String data;

    public SQLPlayer() { }

    SQLPlayer(UUID playerId) {
        this.uuid = playerId;
        this.data = "{}";
    }
}
