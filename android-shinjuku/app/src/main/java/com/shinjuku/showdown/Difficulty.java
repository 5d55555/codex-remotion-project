package com.shinjuku.showdown;

import com.shinjuku.showdown.core.BattleConfig;

/**
 * 难度。从左到右：三轮（标准）、里梅（困难）、万（疯狂）、史上最强（噩梦）、测试模式。
 * 每个难度有自己的 configure()，进入战斗前调用：宿傩的血量、AI 开关和强度等都在各自的函数里设置。
 * 现在只有三轮有 AI；其余三个难度还没调，宿傩先不动；测试模式的宿傩默认没有 AI。
 */
public enum Difficulty {
    MIWA("三轮", "三轮霞眼中的宿傩", "标准难度", 0xFF6FC3FF, R.drawable.emblem_miwa) {
        @Override
        void configure(BattleConfig c) {
            c.sukunaAi = true; // 标准难度：AI 参数用 BattleConfig 里的默认值
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
    },
    TEST("测试模式", "宿傩默认没有 AI", "测试", 0xFF7FE0B0, R.drawable.emblem_test) {
        @Override
        void configure(BattleConfig c) {
            c.sukunaAi = false;
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
