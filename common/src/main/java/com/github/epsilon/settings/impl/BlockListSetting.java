package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import com.github.epsilon.utils.world.BlockRegistryUtils;
import net.minecraft.world.level.block.Block;

import java.util.*;

public class BlockListSetting extends Setting<List<Block>> {

    public BlockListSetting(String name, Collection<Block> defaultValue, Dependency dependency) {
        super(name, dependency, null);
        this.defaultValue = normalize(defaultValue);
        this.value = new ArrayList<>(this.defaultValue);
    }

    @Override
    public void setValue(List<Block> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(List<Block> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new ArrayList<>(defaultValue));
    }

    public boolean contains(Block block) {
        return value.contains(block);
    }

    public void add(Block block) {
        if (!BlockRegistryUtils.isSelectable(block) || value.contains(block)) {
            return;
        }
        List<Block> next = new ArrayList<>(value);
        next.add(block);
        setValue(next);
    }

    public void remove(Block block) {
        if (!value.contains(block)) {
            return;
        }
        List<Block> next = new ArrayList<>(value);
        next.remove(block);
        setValue(next);
    }

    public void toggle(Block block) {
        if (contains(block)) {
            remove(block);
        } else {
            add(block);
        }
    }

    public List<String> getIds() {
        return BlockRegistryUtils.toIds(value);
    }

    public void setIds(Collection<String> ids) {
        List<Block> blocks = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                Block block = BlockRegistryUtils.byId(id);
                if (BlockRegistryUtils.isSelectable(block)) {
                    blocks.add(block);
                }
            }
        }
        setValue(blocks);
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    public Set<Block> asSet() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(value));
    }

    private static List<Block> normalize(Collection<Block> source) {
        List<Block> blocks = new ArrayList<>();
        if (source == null) {
            return blocks;
        }
        for (Block block : source) {
            if (BlockRegistryUtils.isSelectable(block) && !blocks.contains(block)) {
                blocks.add(block);
            }
        }
        return blocks;
    }

}
