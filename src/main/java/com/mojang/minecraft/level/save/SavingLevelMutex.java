package com.mojang.minecraft.level.save;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SavingLevelMutex {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private boolean isSaving = false;
    private boolean isLoading = false;

    public void acquireSaving() {
        lock.writeLock().lock();
        isSaving = true;
    }

    public void releaseSaving() {
        lock.writeLock().unlock();
        isSaving = false;
    }

    public void acquireLoading() {
        lock.readLock().lock();
        if (isSaving) {
            throw new IllegalStateException("Cannot acquire loading lock while saving is in progress");
        }
        isLoading = true;
    }

    public void releaseLoading() {
        lock.readLock().unlock();
        isLoading = false;
    }

}
