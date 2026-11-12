package dev.bytephase.render;

import dev.bytephase.BytePhase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ItemEntity;

/**
 * Entity list snapshot refreshed once per client tick.
 *
 * <p>Each render module used to iterate {@code world.getEntities()} on every
 * frame. With several ESP/HUD modules enabled that was dozens of full entity
 * iterations per frame; filtering the list once per tick is enough because the
 * renderers interpolate entity positions themselves.</p>
 */
public final class EntitySnapshot {
    private static final List<Entity> ENTITY_BUFFER = new ArrayList<>();
    private static final List<LivingEntity> LIVING_BUFFER = new ArrayList<>();
    private static final List<ItemEntity> ITEM_BUFFER = new ArrayList<>();
    private static final List<Entity> ENTITIES = Collections.unmodifiableList(ENTITY_BUFFER);
    private static final List<LivingEntity> LIVING_ENTITIES = Collections.unmodifiableList(LIVING_BUFFER);
    private static final List<ItemEntity> ITEM_ENTITIES = Collections.unmodifiableList(ITEM_BUFFER);

    private EntitySnapshot() {
    }

    /** Called once per client tick from {@code ModuleManager.tick()}. */
    public static void refresh() {
        var mc = BytePhase.mc;
        if (mc == null || mc.world == null) {
            clear();
            return;
        }
        ENTITY_BUFFER.clear();
        LIVING_BUFFER.clear();
        ITEM_BUFFER.clear();
        for (Entity entity : mc.world.getEntities()) {
            ENTITY_BUFFER.add(entity);
            if (entity instanceof LivingEntity living) {
                LIVING_BUFFER.add(living);
            }
            if (entity instanceof ItemEntity item) {
                ITEM_BUFFER.add(item);
            }
        }
    }

    /** Snapshot from the last tick. Do not modify. */
    public static List<Entity> entities() {
        return ENTITIES;
    }

    public static List<LivingEntity> livingEntities() {
        return LIVING_ENTITIES;
    }

    public static List<ItemEntity> itemEntities() {
        return ITEM_ENTITIES;
    }

    public static void clear() {
        ENTITY_BUFFER.clear();
        LIVING_BUFFER.clear();
        ITEM_BUFFER.clear();
    }
}
