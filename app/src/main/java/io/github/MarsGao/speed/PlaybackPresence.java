package io.github.MarsGao.speed;

/** Weak, bounded progress samples. Unknown players never arm playback-only inactivity locking. */
final class PlaybackPresence {
    interface Position { long read(Object player) throws Exception; }
    private static final java.util.WeakHashMap<Object, Sample> players = new java.util.WeakHashMap<>();
    private static final class Sample {
        final Position read; long position = -1;
        Sample(Position read) { this.read=read; }
    }
    static synchronized void observe(Object player, Position position) {
        if (players.containsKey(player)) return;
        if (players.size() >= 16) players.remove(players.keySet().iterator().next());
        players.put(player,new Sample(position));
    }
    static synchronized void forget(Object player) { players.remove(player); }
    static synchronized boolean moving() {
        boolean moving=false;
        for (java.util.Map.Entry<Object,Sample> e : players.entrySet()) {
            Sample sample=e.getValue();
            try {
                long next=sample.read.read(e.getKey());
                if (next>=0 && sample.position>=0 && next!=sample.position) moving=true;
                sample.position=next;
            } catch (Throwable unavailable) { sample.position=-1; }
        }
        return moving;
    }
}
