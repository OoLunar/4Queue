package net.forsaken_borders.mc.FourQueue;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

public final class QueueHolder {
    private final ConcurrentLinkedQueue<UUID> _queue = new ConcurrentLinkedQueue<UUID>();
    private final Semaphore _lock = new Semaphore(1);

    public int addPlayer(UUID uuid) {
        // Lock the queue
        _lock.acquireUninterruptibly();
        try {
            _queue.add(uuid);
            return _queue.size();
        } finally {
            _lock.release();
        }
    }

    public void removePlayer(UUID uuid) {
        _lock.acquireUninterruptibly();
        try {
            _queue.remove(uuid);
        } finally {
            _lock.release();
        }
    }

    public int getPlayerPosition(UUID uuid) {
        _lock.acquireUninterruptibly();
        try {
            int index = 0;
            for (UUID _queueUuid : _queue) {
                // Increase here so that the index isn't zero based.
                index++;

                if (_queueUuid.equals(uuid)) {
                    return index;
                }
            }

            // Player isn't in the queue.
            return -1;
        } finally {
            _lock.release();
        }
    }

    public UUID peekNextPlayer() {
        _lock.acquireUninterruptibly();
        try {
            return _queue.peek();
        } finally {
            _lock.release();
        }
    }

    public int getTotalPlayerCount() {
        return _queue.size();
    }

    public Iterator<UUID> getAllPlayers() {
        return _queue.iterator();
    }
}