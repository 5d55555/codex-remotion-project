package com.shinjuku.showdown;

import com.shinjuku.showdown.core.BattleConfig;

/**
 * 难度。从左到右：三轮（标准）、里梅（困难）、万（疯狂）、史上最强（噩梦）、测试模式。
 * 每个难度有自己的 configure()，进入战斗前调用：宿傩的 AI、数值档、能用的术式在这里设置。
 * 难度越高宿傩越聪明（会试探、打断、抓破绽、骗闪避、读习惯），伤害和血量每高一档 ×1.1（定），都没有领域。
 * 三轮是十影宿傩：没有御厨子（解、世界斩）、没有法阵和魔虚罗、不能变原身（定）；里梅开始有魔虚罗和原身（定）。
 * 测试模式的宿傩默认没有 AI，没有魔虚罗（定）。
 */
public enum Difficulty {
    MIWA("三轮", "三轮霞眼中的宿傩", "标准难度", 0xFF6FC3FF, R.drawable.emblem_miwa) {
        @Override
        void configure(BattleConfig c) {
            c.sukunaDifficulty(0); // 标准：十影宿傩（没有御厨子、魔虚罗、原身），AI 是基础结构
        }
    },
    URAUME("里梅", "里梅眼中的宿傩", "困难难度", 0xFF9B8CFF, R.drawable.emblem_uraume) {
        @Override
        void configure(BattleConfig c) {
            c.sukunaDifficulty(1); // 困难：×1.1；魔虚罗、原身；会进退试探、打断念咒和瞬移前摇、抓长后摇、被连打会脱身
        }
    },
    YOROZU("万", "万眼中的宿傩", "疯狂难度", 0xFFD07CFF, R.drawable.emblem_yorozu) {
        @Override
        void configure(BattleConfig c) {
            c.sukunaDifficulty(2); // 疯狂：×1.21；再加开着展延骗闪避和出招、追闪避、够不着就关展延改用解
        }
    },
    STRONGEST("史上最强", "史上最强！", "噩梦难度", 0xFFFF3B4E, R.drawable.emblem_strongest) {
        @Override
        void configure(BattleConfig c) {
            c.sukunaDifficulty(3); // 噩梦：×1.331；再加读习惯（记下五条悟面对展延贴近时的第一反应，针对着改节奏）
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
