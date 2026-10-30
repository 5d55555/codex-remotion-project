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
    static final String PAL_SUKUNA_TRUE_KEYS = "KWHhgSsELBbqmCcdznNoO";
    static final int[] PAL_SUKUNA_TRUE = {0xFF050508, 0xFFFFC9C2, 0xFFF0948E, 0xFFC66A70, 0xFF8E4552, 0xFFF3DCCB, 0xFFDDB9A6, 0xFFFFFFFF, 0xFFFF9AA0, 0xFFE0303F, 0xFF7A1020, 0xFF140A0E, 0xFF9C4A52, 0xFFF2EEE6, 0xFFFFFFFF, 0xFFB9B3A8, 0xFF2A2D36, 0xFF1A1E2B, 0xFF2E3548, 0xFF050507, 0xFF2A2D36};
    static final String PAL_MAHORAGA_KEYS = "KWSsgVvqnNyBbLh";
    static final int[] PAL_MAHORAGA = {0xFF07070B, 0xFFFAF7F0, 0xFFD8D2C6, 0xFFA9A296, 0xFF6E685F, 0xFFF3EFE6, 0xFFB9B1A2, 0xFF3A2024, 0xFF26232C, 0xFF3B3644, 0xFFC29A4E, 0xFFE4ECF4, 0xFF93A0AE, 0xFFFFFFFF, 0xFF4A3E34};
    static final String PAL_AGITO_KEYS = "KWwAaFfRrBbE";
    static final int[] PAL_AGITO = {0xFF05040A, 0xFFF2EEF6, 0xFFB8B2C4, 0xFF1E1A2C, 0xFF37304E, 0xFF0F0C18, 0xFF2B2440, 0xFFC8B48A, 0xFF8E7A55, 0xFF6FE0FF, 0xFFFFFFFF, 0xFFFF3A4A};
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

    static final int SUKUNA_TRUE_HEAD_PX = 15, SUKUNA_TRUE_HEAD_PY = 27;
    static final String SUKUNA_TRUE_HEAD_PAL = "sukuna_true";
    static final String[] SUKUNA_TRUE_HEAD = {
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
            "..KghhhKqSSKhKSSSSKhKSSSSKhK..",
            "..KgghhKqSSSKSSSSSSKSSSSSKhK..",
            "...KgghKqqKKKKSSSSSKKKKKKSK...",
            "...KgghKSqSEbbSSSSSSEbbbKSK...",
            "....KghKSSqBBBSSSSSSBBBBSSK...",
            "....KghKqSSBLBqqSSSSBLLBqqK...",
            ".....KKKqqSEbbSSSSSSEbbbKSK...",
            ".......KsqSBBBSSSSSSBBBBSSK...",
            ".......KssSqqSSKKKKKSSqqSK....",
            "........KssSSSSSSESSSSSSK.....",
            ".........KKssSSSSSSSSKK.......",
            "...........KKKKKKKKKK.........",
    };

    static final int SUKUNA_TRUE_TORSO_PX = 8, SUKUNA_TRUE_TORSO_PY = 16;
    static final String SUKUNA_TRUE_TORSO_PAL = "sukuna_true";
    static final String[] SUKUNA_TRUE_TORSO = {
            "......KKKKK......",
            "....KKCcSScKK....",
            "...KCcCSSSSdCK...",
            "..KCcCSqSSqSdCK..",
            ".KCcCSqSSSSqSdCK.",
            "KCcCSqSSSSSSqSdCK",
            "KCcCSSSSSSSSSSdCK",
            "KCcCSKKKKKKKKSdCK",
            ".KCcKEmEmEmEmKdCK",
            ".KCcSKKKKKKKKSdCK",
            ".KCcCSqSSSSqSdCK.",
            "..KzzzzzzzzzzzzK.",
            "..KzzzzzzzzzzzzK.",
            "..KnNnnnnnnnnndK.",
            "..KnNnnnnnnnnndK.",
            "..KnnnnnKnnnnndK.",
            "...KKKKK.KKKKKK..",
    };

    static final int SUKUNA_TRUE_ARM_PX = 2, SUKUNA_TRUE_ARM_PY = 1;
    static final String SUKUNA_TRUE_ARM_PAL = "sukuna_true";
    static final String[] SUKUNA_TRUE_ARM = {
            ".KKK.",
            "KcCCK",
            "KcCCK",
            "KcCdK",
            "KcCdK",
            "KcCdK",
            "KCCdK",
            ".KSsK",
            ".KqqK",
            ".KSsK",
            "KSSsK",
            "KSSsK",
            ".KKK.",
    };

    static final int SUKUNA_TRUE_ARM2_PX = 2, SUKUNA_TRUE_ARM2_PY = 1;
    static final String SUKUNA_TRUE_ARM2_PAL = "sukuna_true";
    static final String[] SUKUNA_TRUE_ARM2 = {
            ".KKK.",
            "KSSsK",
            "KSSsK",
            "KqqqK",
            "KSSsK",
            "KSSsK",
            ".KSsK",
            ".KqqK",
            ".KSsK",
            "KSSsK",
            "KSSsK",
            ".KKK.",
    };

    static final int MAHORAGA_HEAD_PX = 13, MAHORAGA_HEAD_PY = 25;
    static final String MAHORAGA_HEAD_PAL = "mahoraga";
    static final String[] MAHORAGA_HEAD = {
            "KK......................",
            "KVKK....................",
            ".KVVKK..................",
            "..KVvVKK......KK........",
            "...KVvvVKK...KVVKK......",
            "....KKVvvVKKKVvvVKK.....",
            ".KK...KKVvVKSSSKKVVK....",
            "KVVKK...KKVKSSWWSSKK....",
            ".KVvVKK...KSSSWWSSSSK...",
            "..KKvvVKKKSSSSSSSSSSSK..",
            "....KKvVVVKKKKKKKKKKKKK.",
            ".....KKvvVVVVVVVVVVVVVVK",
            ".......KKvvvvvvvvvvvvvK.",
            ".........KKKKKKKKKKKKSK.",
            ".........KgsSSSSSSSSSSK.",
            ".........KgsSSSSSSSSSSK.",
            ".........KgsSSSSSSKKKSK.",
            ".........KgssSSSSKqqqKK.",
            "..........KgsSSSSSKKKSK.",
            "..........KggsSSSSSSSK..",
            "...........KKggssssKK...",
            ".............KKKKKK.....",
            ".............KsSSsK.....",
            ".............KsSSsK.....",
            ".............KsSSsK.....",
            "............KKKKKKKK....",
    };

    static final int MAHORAGA_TORSO_PX = 11, MAHORAGA_TORSO_PY = 22;
    static final String MAHORAGA_TORSO_PAL = "mahoraga";
    static final String[] MAHORAGA_TORSO = {
            ".......KKKKKKKK.......",
            ".....KKsSSSSSSSsKK....",
            "...KKsSSSWWSSWWSSsKK..",
            "..KsSSSSWWSSSSWWSSSsK.",
            ".KsSSSSSSSSSSSSSSSSSsK",
            "KgsSSSSSSSsSSsSSSSSSsK",
            "KgsSSSSSSsgSSgsSSSSSsK",
            "KgssSSSSsgSSSSgsSSSssK",
            ".KgssSSSSSSSSSSSSSSsK.",
            ".KgsSSSSSSSssSSSSSSsK.",
            ".KgssSSSsSSSSSSsSSssK.",
            "..KgsSSSSSSssSSSSSsK..",
            "..KgssSSsSSSSSSsSssK..",
            "..KgssSSSSSssSSSSssK..",
            "...KgsSSsSSSSSSsSsK...",
            "...KyyyyyyyyyyyyyyK...",
            "...KyhyyyyyyyyyyhyK...",
            "...KnNnnnnnnnnnnnnK...",
            "..KnNNnnnnnnnnnnnnnK..",
            "..KnNnnnnnnKnnnnnnnK..",
            "..KnnnnnnnK.KnnnnnnK..",
            "...KKKKKKK...KKKKKK...",
    };

    static final int MAHORAGA_ARM_PX = 3, MAHORAGA_ARM_PY = 2;
    static final String MAHORAGA_ARM_PAL = "mahoraga";
    static final String[] MAHORAGA_ARM = {
            ".KKKK..",
            "KsSSSK.",
            "KsSSWSK",
            "KsSSWSK",
            "KgsSSSK",
            "KgsSSSK",
            "KgsSSSK",
            ".KgsSSK",
            ".KgsSSK",
            ".KgsSSK",
            ".KgsSSK",
            ".KgsSSK",
            "KKgsSSK",
            "KgsSSSK",
            "KgsSSSK",
            "KgsSSSK",
            ".KKKKK.",
    };

    static final int MAHORAGA_SWORD_PX = 3, MAHORAGA_SWORD_PY = 2;
    static final String MAHORAGA_SWORD_PAL = "mahoraga";
    static final String[] MAHORAGA_SWORD = {
            ".KKKK....",
            "KsSSSK...",
            "KsSSWSK..",
            "KsSSWSK..",
            "KgsSSSK..",
            "KgsSSSK..",
            "KgsSSSK..",
            ".KgsSSK..",
            ".KgsSSK..",
            ".KgsSSK..",
            ".KgsSSK..",
            "KhhhhhhK.",
            "KhKhhKhK.",
            "KgsSSSK..",
            "KgsSSSK..",
            ".KbBLK...",
            ".KbBLK...",
            ".KbBLK...",
            "..KbBLK..",
            "..KbBLK..",
            "..KbBLK..",
            "...KbBLK.",
            "...KbBLK.",
            "...KbBLK.",
            "....KbLK.",
            "....KbLK.",
            ".....KLK.",
            ".....KK..",
    };

    static final int MAHORAGA_LEG_PX = 4, MAHORAGA_LEG_PY = 1;
    static final String MAHORAGA_LEG_PAL = "mahoraga";
    static final String[] MAHORAGA_LEG = {
            "KnnnnnnK..",
            "KnNNnnnK..",
            "KnNnnnnK..",
            "KnNnnnnK..",
            "KnNnnnnK..",
            ".KnNnnnK..",
            ".KnnnnnK..",
            ".KKKKKKK..",
            ".KsSSSSK..",
            ".KgsSSSK..",
            ".KgsSSSK..",
            ".KgsSSSK..",
            ".KgsSSSK..",
            ".KgsSSSK..",
            ".KgsSSSK..",
            ".KgssSSK..",
            ".KgsSSSKK.",
            ".KgsSSSSSK",
            ".KgssSSSSK",
            ".KKKKKKKKK",
    };

    static final int AGITO_BODY_PX = 18, AGITO_BODY_PY = 14;
    static final String AGITO_BODY_PAL = "agito";
    static final String[] AGITO_BODY = {
            ".........KK..KK...............",
            "........KRrK.KRK..............",
            ".......KRrK..KRrK.............",
            "..KK...KRK....KRK.............",
            ".KfFK..KRK...KRK..............",
            "KfFFFK..KRKKKRK...............",
            "KffFFFK..KKWWWK...KKK.........",
            ".KffFFFKKWWWWwwK.KaAAKK.......",
            "..KffFFKWWEWwwKKaAAAAAAK......",
            "...KKfFKWWWwwKaAAaAAAAAAK.....",
            ".....KKKwwwwKaAAAaaAAAAAAK....",
            "....KBbKKKKKaAAAAAAAAAAAAAK...",
            "...KBb.KfFFKAAAAAAAAAAKKAAAK..",
            "....K.KfFFFKKAAAAAAAAK..KAAK..",
            ".....KfFFFFK.KKAKKAAK....KAK..",
            "....KfFFFFK...KAKKAK......KK..",
            "...KffFFFK....KbBKbBK.........",
            "...KKKKKK......KK..KK.........",
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
