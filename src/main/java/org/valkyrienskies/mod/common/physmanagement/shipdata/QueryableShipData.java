package org.valkyrienskies.mod.common.physmanagement.shipdata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.googlecode.cqengine.query.Query;
import com.googlecode.cqengine.resultset.ResultSet;
import lombok.extern.log4j.Log4j2;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.valkyrienskies.mod.common.physics.management.physo.PhysicsObject;
import org.valkyrienskies.mod.common.physics.management.physo.ShipData;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.valkyrienskies.mod.common.util.cqengine.ConcurrentUpdatableIndexedCollection;
import org.valkyrienskies.mod.common.util.cqengine.UpdatableHashIndex;
import org.valkyrienskies.mod.common.util.cqengine.UpdatableUniqueIndex;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.googlecode.cqengine.query.QueryFactory.equal;

/**
 * A class that keeps track of ship data
 */
@MethodsReturnNonnullByDefault
@Log4j2
@SuppressWarnings("WeakerAccess")
public class QueryableShipData implements Iterable<ShipData> {

    // Where every ship data instance is stored, regardless if the corresponding PhysicsObject is
    // loaded in the World or not.
    private ConcurrentUpdatableIndexedCollection<ShipData> allShips;

    public QueryableShipData() {
        this(new ConcurrentUpdatableIndexedCollection<>());
    }

    @JsonCreator // This tells Jackson to pass in allShips when serializing
    // The default thing that is passed in will be 'null' if none exists
    public QueryableShipData(
        @JsonProperty("allShips") ConcurrentUpdatableIndexedCollection<ShipData> ships) {

        if (ships == null) {
            ships = new ConcurrentUpdatableIndexedCollection<>();
        }

        this.allShips = ships;

        // For every ship data, set the 'owner' field to us -- kinda hacky but what can I do
        // I don't want to serialize a billion references to this
        // This probably only needs to be done once per world, so this is fine
        this.allShips.forEach(data -> {
            try {
                Field owner = ShipData.class.getDeclaredField("owner");
                owner.setAccessible(true);
                owner.set(data, this.allShips);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });

        this.allShips.addIndex(UpdatableHashIndex.onAttribute(ShipData.NAME));
        this.allShips.addIndex(UpdatableUniqueIndex.onAttribute(ShipData.UUID));
        this.allShips.addIndex(UpdatableUniqueIndex.onAttribute(ShipData.CHUNKS));

    }

    /**
     * @see ValkyrienUtils#getQueryableData(World)
     */
    public static QueryableShipData get(World world) {
        return ValkyrienUtils.getQueryableData(world);
    }

    /**
     * @deprecated Do not use -- thinking of better API choices
     */
    @Deprecated
    public ConcurrentUpdatableIndexedCollection<ShipData> getAllShips() {
        return allShips;
    }

    /**
     * Retrieves a list of all ships.
     */
    public List<ShipData> getShips() {
        return ImmutableList.copyOf(allShips);
    }

    public Optional<ShipData> getShipFromChunk(int chunkX, int chunkZ) {
        return getShipFromChunk(ChunkPos.asLong(chunkX, chunkZ));
    }

    public Optional<ShipData> getShipFromChunk(long chunkLong) {
        Query<ShipData> query = equal(ShipData.CHUNKS, chunkLong);
        ResultSet<ShipData> resultSet = allShips.retrieve(query);

        if (resultSet.size() > 1) {
            throw new IllegalStateException(
                "How the heck did we get 2 or more ships both managing the chunk at " + chunkLong);
        }
        if (resultSet.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(resultSet.uniqueResult());
        }
    }

    public Optional<ShipData> getShip(UUID uuid) {
        Query<ShipData> query = equal(ShipData.UUID, uuid);
        ResultSet<ShipData> resultSet = allShips.retrieve(query);

        if (resultSet.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(resultSet.uniqueResult());
        }
    }

    public Optional<ShipData> getShipFromName(String name) {
        Query<ShipData> query = equal(ShipData.NAME, name);
        ResultSet<ShipData> shipDataResultSet = allShips.retrieve(query);

        if (shipDataResultSet.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(shipDataResultSet.uniqueResult());
        }
    }

    public void removeShip(UUID uuid) {
        getShip(uuid).ifPresent(ship -> allShips.remove(ship));
    }

    public void removeShip(ShipData data) {
        allShips.remove(data);
    }

    public void addShip(ShipData ship) {
        allShips.add(ship);
    }

    /**
     * Adds the ship data if it doesn't exist, or replaces the old ship data with the new ship data, while preserving
     * the physics object attached to the old data if there was one.
     *
     * @param ship
     */
    public void addOrUpdateShipPreservingPhysObj(ShipData ship) {
        Optional<ShipData> old = getShip(ship.getUuid());
        PhysicsObject physicsObject = null;
        if (old.isPresent()) {
            physicsObject = old.get().getPhyso();
            this.removeShip(old.get());
        }
        old.ifPresent(this::removeShip);
        if (physicsObject != null) {
            ship.setPhyso(physicsObject);
        }
        this.addShip(ship);
    }

    @Override
    public Iterator<ShipData> iterator() {
        return allShips.iterator();
    }

    public Stream<ShipData> stream() {
        return allShips.stream();
    }
}
