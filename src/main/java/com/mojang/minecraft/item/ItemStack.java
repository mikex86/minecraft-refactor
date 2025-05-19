package com.mojang.minecraft.item;

public class ItemStack {

    private final Item item;
    private final int maxCount;
    private int count;

    /**
     * Time in milliseconds when a new item was last picked up into this stack.
     * Used for animation purposes.
     */
    public long lastPickupTimeMs;

    public ItemStack(Item item, int count) {
        this.item = item;
        this.count = count;
        this.maxCount = item.getMaxStackSize();
    }

    public Item getItem() {
        return item;
    }

    public int getCount() {
        return count;
    }

    public void decreaseAmount(int amount) {
        if (count == 0) {
            return;
        }
        count -= amount;
    }

    /**
     * Increases the amount of this item stack by the specified amount.
     *
     * @param amount The amount to increase by
     * @param isItemPickup whether this increase is due to an item pickup. if true, this will trigger associated animation timers to reset.
     * @return The amount added to the stack
     */
    public int increaseAmount(int amount, boolean isItemPickup) {
        int added = Math.min(amount, maxCount - count);
        count += added;
        if (isItemPickup && added > 0) {
            lastPickupTimeMs = System.currentTimeMillis();
        }
        return added;
    }
}
