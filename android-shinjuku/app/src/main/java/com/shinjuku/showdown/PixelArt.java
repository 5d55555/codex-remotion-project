package com.shinjuku.showdown;

// 由 scripts/pixel_art.py 从 art/pixel/characters.txt 生成，不要手改。

/** 像素角色的部件：每个部件一行行的字符，每个字符对应调色板里的一个颜色（. 是透明）。 */
final class PixelArt {
    private PixelArt() {
    }

    static final String PAL_GOJO_KEYS = "KWHhgSsELBbmCcdznNoO";
    static final int[] PAL_GOJO = {0xFF0B0B14, 0xFFFFFFFF, 0xFFE4E6F0, 0xFFB4BCD0, 0xFF8A93AD, 0xFFFBE6D6, 0xFFE7C3AE, 0xFFFFFFFF, 0xFFC9F4FF, 0xFF5AB8F2, 0xFF285A9B, 0xFFB5636A, 0xFF1B1E27, 0xFF383E4C, 0xFF0A0B10, 0xFF4E5568, 0xFF22252F, 0xFF353A47, 0xFF050507, 0xFF2A2D36};
    static final String PAL_SUKUNA_KEYS = "KWHhgSsELBbqmCcdznNoO";
    static final int[] PAL_SUKUNA = {0xFF050508, 0xFF4A5270, 0xFF252937, 0xFF171A24, 0xFF0D0F15, 0xFFF3DCCB, 0xFFDDB9A6, 0xFFFFFFFF, 0xFFFF9AA0, 0xFFE0303F, 0xFF7A1020, 0xFF1A0F14, 0xFF9C4A52, 0xFF1E2333, 0xFF3A4260, 0xFF0C0E16, 0xFF4C5578, 0xFF1A1E2B, 0xFF2E3548, 0xFF050507, 0xFF2A2D36};
    static final String PAL_FONT_KEYS = "W";
    static final int[] PAL_FONT = {0xFFFFFFFF};

    static final int GOJO_HEAD_PX = 15, GOJO_HEAD_PY = 27;
    static final String GOJO_HEAD_PAL = "gojo";
    static final String[] GOJO_HEAD = {
            "..........K.......K...........",
            ".........KWK.....KWK....K.....",
            "...K.....KWHK...KWHK...KWK....",
            "..KWK...KWHhK..KWHhK..KWHK....",
            "..KWHK..KWHhK..KWHhK.KWHhK..K.",
            "...KWHK.KWHHhKKWHHhKKWHHhK.KWK",
            "...KWHhKWHHHhWHHHHhWHHHhKKWHhK",
            "..KWHHHhHHHHHHHHHHHHHHHHHHHhK.",
            ".KWHHHHHHHWWHHHHHHWWHHHHHHHhK.",
            "KWHHHHHHHHHHHHHHHHHHHHHHHHHHhK",
            ".KhHHHHHHHHHHHHHHHHHHHHHHHHhhK",
            "KhhhHHHHHHHHHHHHHHHHHHHHHHhhK.",
            "KghhhHHHHHHHHHHHHHHHHHHHHHhhK.",
            ".KghhhHHHHHhHHHHhHHHHHhHHHhhK.",
            ".KghhhHHHHhKhHHHhKhHHHhKhHhK..",
            "..KghhHHhKSKhHhKSKhHhKSKhHhK..",
            "..KghhhHKSSSKhKSSSKhKSSSKhhK..",
            "..KgghhKSSSSSKSSSSSKSSSSSKhK..",
            "...KgghKSSKKKKSSSSSKKKKKKSK...",
            "...KgghKSSSEbbSSSSSSEbbbKSK...",
            "....KghKSSSBBBSSSSSSBBBBSSK...",
            "....KghKSSSBLBSSSSSSBLLBSSK...",
            ".....KKKsSSSLSSSsSSSSLLSSSK...",
            ".......KsSSSSSSSSSSSSSSSSSK...",
            ".......KssSSSSSSSSSKSSSSSK....",
            "........KssSSSSSKKSSSSSSK.....",
            ".........KKssSSSSSSSSKK.......",
            "...........KKKKKKKKKK.........",
    };

    static final int GOJO_TORSO_PX = 7, GOJO_TORSO_PY = 14;
    static final String GOJO_TORSO_PAL = "gojo";
    static final String[] GOJO_TORSO = {
            ".....KKKKK.....",
            "....KcCCCdK....",
            "...KKcCzCdKK...",
            "..KcCCCzCCCdK..",
            ".KcCCCCzCCCCdK.",
            "KcCCCCCzCCCCCdK",
            "KcCCCCCzCCCCCdK",
            "KcCCCCCzCCCCCdK",
            ".KcCCCCzCCCCdK.",
            ".KcCCCCzCCCCdK.",
            "..KdddddddddK..",
            "..KnNnnnnnndK..",
            "..KnNnnnnnndK..",
            "..KnnnnKnnndK..",
            "...KKKK.KKKK...",
    };

    static final int GOJO_ARM_PX = 2, GOJO_ARM_PY = 1;
    static final String GOJO_ARM_PAL = "gojo";
    static final String[] GOJO_ARM = {
            ".KKK.",
            "KcCCK",
            "KcCCK",
            "KcCdK",
            "KcCdK",
            "KcCdK",
            "KcCdK",
            ".KCdK",
            ".KCdK",
            ".KKKK",
            "KSSsK",
            "KSSsK",
            ".KKK.",
    };

    static final int GOJO_LEG_PX = 2, GOJO_LEG_PY = 1;
    static final String GOJO_LEG_PAL = "gojo";
    static final String[] GOJO_LEG = {
            "KnnnK..",
            "KnNnK..",
            "KnNnK..",
            "KnNnK..",
            "KnNdK..",
            "KnNdK..",
            "KnNdK..",
            "KnndK..",
            "KnndK..",
            "KnndK..",
            "KnndK..",
            "KOooKK.",
            "KoOoooK",
            "KoooooK",
            ".KKKKK.",
    };

    static final int SUKUNA_HEAD_PX = 15, SUKUNA_HEAD_PY = 27;
    static final String SUKUNA_HEAD_PAL = "sukuna";
    static final String[] SUKUNA_HEAD = {
            ".....K......K.....K......K....",
            ".....KWK....KWK..KWK....KWK...",
            "K.....KWK..KWHK.KWHK...KWHK...",
            "KWK...KWHK.KWHhKKWHhK.KWHhK..K",
            ".KWKK.KWHhKKWHhKKWHhKKKWHhK.KW",
            ".KWHHKKWHhHKWHHhWHHHhKWHHhKKWH",
            "..KWHhKWHHHhHHHHhHHHHhHHHhWHHK",
            ".KWHHHhHHHHHHHHHHHHHHHHHHHHhK.",
            "KWHHHHHHHHWWHHHHHHWWHHHHHHHhhK",
            ".KhHHHHHHHHHHHHHHHHHHHHHHHHhhK",
            "KhhhHHHHHHHHHHHHHHHHHHHHHHHhhK",
            "KghhhHHHHHHHHHHHHHHHHHHHHHhhhK",
            ".KghhhHHHhhHHHHhhHHHHhhHHHhhK.",
            ".KghhhHHhKhHHHhKKhHHhKKhHHhK..",
            "..KghhHhKSKhHhKSSKhHKSSKhHhK..",
            "..KghhhKSSSKhKSSSSKhKSSSSKhK..",
            "..KgghhKSSSSKSSSSSSKSSSSSKhK..",
            "...KgghKSSKKKKSSSSSKKKKKKSK...",
            "...KgghKSSSEbbSSSSSSEbbbKSK...",
            "....KghKSSSBBBSSSSSSBBBBSSK...",
            "....KghKSSSBLBSSSSSSBLLBSSK...",
            ".....KKKsSqqqSSSsSSSqqqqSSK...",
            ".......KsSSSSSSSSSSSSSSSSSK...",
            ".......KssSqqSSKKKKKSSqqSK....",
            "........KssSSSSSSESSSSSSK.....",
            ".........KKssSSSSSSSSKK.......",
            "...........KKKKKKKKKK.........",
    };

    static final int SUKUNA_TORSO_PX = 7, SUKUNA_TORSO_PY = 14;
    static final String SUKUNA_TORSO_PAL = "sukuna";
    static final String[] SUKUNA_TORSO = {
            ".....KKKKK.....",
            "....KcCCCdK....",
            "...KKcCzCdKK...",
            "..KcCCCzCCCdK..",
            ".KcCCCCzCCCCdK.",
            "KcCCCCCzCCCCCdK",
            "KcCCCCCzCCCCCdK",
            "KcCCCCCzCCCCCdK",
            ".KcCCCCzCCCCdK.",
            ".KcCCCCzCCCCdK.",
            "..KdddddddddK..",
            "..KnNnnnnnndK..",
            "..KnNnnnnnndK..",
            "..KnnnnKnnndK..",
            "...KKKK.KKKK...",
    };

    static final int SUKUNA_ARM_PX = 2, SUKUNA_ARM_PY = 1;
    static final String SUKUNA_ARM_PAL = "sukuna";
    static final String[] SUKUNA_ARM = {
            ".KKK.",
            "KcCCK",
            "KcCCK",
            "KcCdK",
            "KcCdK",
            "KcCdK",
            "KcCdK",
            ".KCdK",
            ".KCdK",
            ".KqqK",
            "KSSsK",
            "KSSsK",
            ".KKK.",
    };

    static final int SUKUNA_LEG_PX = 2, SUKUNA_LEG_PY = 1;
    static final String SUKUNA_LEG_PAL = "sukuna";
    static final String[] SUKUNA_LEG = {
            "KnnnK..",
            "KnNnK..",
            "KnNnK..",
            "KnNnK..",
            "KnNdK..",
            "KnNdK..",
            "KnNdK..",
            "KnndK..",
            "KnndK..",
            "KnndK..",
            "KnndK..",
            "KOooKK.",
            "KoOoooK",
            "KoooooK",
            ".KKKKK.",
    };

    static final int DIGIT_0_PX = 0, DIGIT_0_PY = 0;
    static final String DIGIT_0_PAL = "font";
    static final String[] DIGIT_0 = {
            ".WWW.",
            "W...W",
            "W..WW",
            "W.W.W",
            "WW..W",
            "W...W",
            ".WWW.",
    };

    static final int DIGIT_1_PX = 0, DIGIT_1_PY = 0;
    static final String DIGIT_1_PAL = "font";
    static final String[] DIGIT_1 = {
            "..W..",
            ".WW..",
            "..W..",
            "..W..",
            "..W..",
            "..W..",
            ".WWW.",
    };

    static final int DIGIT_2_PX = 0, DIGIT_2_PY = 0;
    static final String DIGIT_2_PAL = "font";
    static final String[] DIGIT_2 = {
            ".WWW.",
            "W...W",
            "....W",
            "...W.",
            "..W..",
            ".W...",
            "WWWWW",
    };

    static final int DIGIT_3_PX = 0, DIGIT_3_PY = 0;
    static final String DIGIT_3_PAL = "font";
    static final String[] DIGIT_3 = {
            "WWWW.",
            "....W",
            "....W",
            ".WWW.",
            "....W",
            "....W",
            "WWWW.",
    };

    static final int DIGIT_4_PX = 0, DIGIT_4_PY = 0;
    static final String DIGIT_4_PAL = "font";
    static final String[] DIGIT_4 = {
            "...W.",
            "..WW.",
            ".W.W.",
            "W..W.",
            "WWWWW",
            "...W.",
            "...W.",
    };

    static final int DIGIT_5_PX = 0, DIGIT_5_PY = 0;
    static final String DIGIT_5_PAL = "font";
    static final String[] DIGIT_5 = {
            "WWWWW",
            "W....",
            "WWWW.",
            "....W",
            "....W",
            "W...W",
            ".WWW.",
    };

    static final int DIGIT_6_PX = 0, DIGIT_6_PY = 0;
    static final String DIGIT_6_PAL = "font";
    static final String[] DIGIT_6 = {
            "..WW.",
            ".W...",
            "W....",
            "WWWW.",
            "W...W",
            "W...W",
            ".WWW.",
    };

    static final int DIGIT_7_PX = 0, DIGIT_7_PY = 0;
    static final String DIGIT_7_PAL = "font";
    static final String[] DIGIT_7 = {
            "WWWWW",
            "....W",
            "...W.",
            "..W..",
            ".W...",
            ".W...",
            ".W...",
    };

    static final int DIGIT_8_PX = 0, DIGIT_8_PY = 0;
    static final String DIGIT_8_PAL = "font";
    static final String[] DIGIT_8 = {
            ".WWW.",
            "W...W",
            "W...W",
            ".WWW.",
            "W...W",
            "W...W",
            ".WWW.",
    };

    static final int DIGIT_9_PX = 0, DIGIT_9_PY = 0;
    static final String DIGIT_9_PAL = "font";
    static final String[] DIGIT_9 = {
            ".WWW.",
            "W...W",
            "W...W",
            ".WWWW",
            "....W",
            "...W.",
            ".WW..",
    };

    static final int DIGIT_SLASH_PX = 0, DIGIT_SLASH_PY = 0;
    static final String DIGIT_SLASH_PAL = "font";
    static final String[] DIGIT_SLASH = {
            "....W",
            "....W",
            "...W.",
            "..W..",
            ".W...",
            "W....",
            "W....",
    };

    static final int ICON_BACK_PX = 0, ICON_BACK_PY = 0;
    static final String ICON_BACK_PAL = "font";
    static final String[] ICON_BACK = {
            "...W.....",
            "..WW.....",
            ".WWWWWWWW",
            "WWWWWWWWW",
            ".WWWWWWWW",
            "..WW.....",
            "...W.....",
    };

    static final int ICON_NOTE_PX = 0, ICON_NOTE_PY = 0;
    static final String ICON_NOTE_PAL = "font";
    static final String[] ICON_NOTE = {
            "...WWWWW",
            "...WWWWW",
            "...W...W",
            "...W...W",
            "...W...W",
            "...W...W",
            ".WWW.WWW",
            "WWWWWWWW",
            "WWWW.WWW",
            ".WW...W.",
    };

}
