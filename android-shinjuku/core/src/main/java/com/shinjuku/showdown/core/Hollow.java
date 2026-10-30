package com.shinjuku.showdown.core;

/**
 * 无指向茈：赫与苍相遇时在两者之间生成，向四周扩张。
 * 范围扩到谁身上就打中谁，每人一次：宿傩、魔虚罗 850；五条悟自己也挨，只有一半。
 */
public final class Hollow {
    public boolean active;
    public int tick, instanceId;
    public float x, y, radius;
    int hitMask;                 // 已经打中过谁（按 Fighter.id 的位）
}
