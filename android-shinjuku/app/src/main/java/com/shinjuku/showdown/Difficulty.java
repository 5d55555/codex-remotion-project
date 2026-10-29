package com.shinjuku.showdown;

import com.shinjuku.showdown.core.BattleConfig;

/**
 * 难度。从左到右：三轮（标准）、里梅（困难）、万（疯狂）、史上最强（噩梦）。
 * 每个难度有自己的 configure()，进入战斗前调用；现在宿傩还是活靶子，这里先只放占位，
 * 以后宿傩的血量、技能、AI 强度等都在各自的函数里设置。
 */
public enum Difficulty {
    MIWA("三轮", "三轮霞眼中的宿傩", "标准难度", 0xFF6FC3FF, R.drawable.emblem_miwa) {
        @Override
        void configure(BattleConfig c) {
            // 标准难度：以后在这里设置
        }
    },
    URAUME("里梅", "里梅眼中的宿傩", "困难难度", 0xFF9B8CFF, R.drawable.emblem_uraume) {
        @Override
        void configure(BattleConfig c) {
            // 困难难度：以后在这里设置
        }
    },
    YOROZU("万", "万眼中的宿傩", "疯狂难度", 0xFFD07CFF, R.drawable.emblem_yorozu) {
        @Override
        void configure(BattleConfig c) {
            // 疯狂难度：以后在这里设置
        }
    },
    STRONGEST("史上最强", "史上最强！", "噩梦难度", 0xFFFF3B4E, R.drawable.emblem_strongest) {
        @Override
        void configure(BattleConfig c) {
            // 噩梦难度：以后在这里设置
        }
    };

    final String title, subtitle, tier;
    final int accent;
    final int emblem;

    Difficulty(String title, String subtitle, String tier, int accent, int emblem) {
        this.title = title;
        this.subtitle = subtitle;
        this.tier = tier;
        this.accent = accent;
        this.emblem = emblem;
    }

    /** 把这个难度的设定写进战斗配置。 */
    abstract void configure(BattleConfig c);
}
