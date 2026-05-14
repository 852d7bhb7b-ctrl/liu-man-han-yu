package com.liumanhanyu.app

/**
 * 多语言词库 — 与 Web 演示版同步
 * 结构：正向 en/ja/ko/de → zh，反向 zh → en/ja/ko/de
 */
object TranslationData {

    /** 语言代码 → 中文名称 */
    val langNames: Map<String, String> = mapOf(
        "en" to "英语", "ja" to "日语", "ko" to "韩语", "de" to "德语",
        "fr" to "法语", "es" to "西班牙语", "pt" to "葡萄牙语",
        "ru" to "俄语", "th" to "泰语", "vi" to "越南语",
        "it" to "意大利语", "ar" to "阿拉伯语", "id" to "印尼语",
    )

    /** 正向：各语言 → 中文 */
    val toZh: Map<String, Map<String, String>> = mapOf(
        "en" to enToZh, "ja" to jaToZh, "ko" to koToZh, "de" to deToZh,
        "fr" to frToZh, "es" to esToZh,
    )

    /** 反向：中文 → 各语言 */
    val zhTo: Map<String, Map<String, String>> = mapOf(
        "en" to zhToEn, "ja" to zhToJa, "ko" to zhToKo, "de" to zhToDe,
        "fr" to zhToFr, "es" to zhToEs,
    )

    // ============================================================
    // 正向翻译词典
    // ============================================================

    private val enToZh = mapOf(
        "Settings" to "设置", "Edit Profile" to "编辑资料",
        "Follow" to "关注", "Following" to "已关注", "Message" to "私信",
        "Share" to "分享", "Save" to "收藏", "Report" to "举报",
        "Delete" to "删除", "Cancel" to "取消", "Done" to "完成",
        "Next" to "下一步", "Back" to "返回", "Close" to "关闭", "Open" to "打开",
        "Home" to "首页", "Search" to "搜索", "Add" to "发布",
        "Activity" to "动态", "Profile" to "我的",
        "Loading..." to "加载中...", "No results" to "无结果", "Try again" to "重试",
        "Log in" to "登录", "Sign up" to "注册", "Log out" to "退出登录",
        "Post" to "发送", "Add a comment..." to "说点什么...",
        "View all" to "查看全部", "comments" to "条评论",
        "likes" to "次赞", "ago" to "前",
        "hours" to "小时", "minutes" to "分钟",
        "Your Story" to "你的快拍", "New York, USA" to "美国，纽约",
        "1,234 likes" to "1,234 次赞",
        "View all 24 comments" to "查看全部 24 条评论", "2 hours ago" to "2 小时前",
        "Nice!" to "好看！", "Nice" to "好看", "nice" to "好看",
        "Great!" to "太棒了！", "great" to "很棒",
        "Beautiful!" to "好美！", "Amazing!" to "太棒了！", "Awesome!" to "厉害！",
        "Cool!" to "酷！", "Haha" to "哈哈", "Hahaha" to "哈哈哈",
        "LOL" to "笑死", "LMAO" to "笑死", "OMG" to "天哪",
        "Wow!" to "哇！", "Whoa!" to "哇！",
        "Love it!" to "爱了！", "Love this!" to "爱了！",
        "Not bad" to "不错", "Good!" to "好！", "good" to "好", "Good" to "好",
        "Oh my god" to "我的天", "Oh lord" to "老天",
        "Oh my lord!" to "我勒个老天爷！", "What the heck!" to "我勒个去！",
        "Holy crap!" to "卧槽！", "No way!" to "不是吧！",
        "So beautiful!" to "太好看了！", "Gorgeous!" to "真美！",
        "So jealous!" to "羡慕！", "Impressive!" to "厉害！", "Incredible!" to "绝了！",
        "Nice shot!" to "拍得真好！", "I want to go there!" to "想去！",
        "I want to go too!" to "我也想去！", "Where is this?" to "这是哪里？",
        "Keep it up!" to "加油！", "Looking forward to it!" to "期待！",
        "First!" to "前排！", "First comment!" to "沙发！", "Checking in!" to "打卡！",
        "Learned something!" to "学习了！", "Saved!" to "收藏了！", "Bookmarked!" to "马克！",
        "So talented!" to "太有才了！", "Pretty!" to "漂亮！", "Handsome!" to "帅！",
        "So awesome!" to "太牛了！", "Great job!" to "很棒！",
        "Sorry" to "对不起", "Sorry!" to "对不起！", "sorry" to "对不起",
        "Thank you" to "谢谢", "Thanks" to "谢谢",
        "Please" to "请", "Yes" to "是的", "No" to "不", "Maybe" to "也许",
        "OK" to "好的", "Ok" to "好的", "ok" to "好的", "okay" to "好的",
        "Hello" to "你好", "Hi" to "嗨", "Hey" to "嘿",
        "How are you" to "你好吗", "What's up" to "怎么样",
        "Long time no see" to "好久不见", "See you" to "回见", "Bye" to "拜拜",
        "Good morning" to "早上好", "Good night" to "晚安",
        "Happy birthday" to "生日快乐", "Congratulations" to "恭喜",
        "I'm happy" to "我很开心", "I'm sad" to "我很难过",
        "I miss you" to "我想你", "That's funny" to "真好笑",
        "That's crazy" to "太疯狂了", "That's cute" to "好可爱",
        "I can't believe it" to "难以置信", "I'm speechless" to "我无语了",
        "You're right" to "你说得对", "Exactly" to "就是", "Of course" to "当然",
        "Delicious" to "好吃", "Yummy" to "美味",
        "Beautiful view" to "风景好美", "Breathtaking" to "美到窒息",
        "You can do it" to "你可以的", "Don't give up" to "别放弃",
        "Don't worry" to "别担心", "Take it easy" to "放轻松",
        "Just now" to "刚刚", "Today" to "今天", "Tomorrow" to "明天",
        "Yesterday" to "昨天", "Tonight" to "今晚",
        "What is this" to "这是什么", "Where are you" to "你在哪",
        "How much" to "多少钱", "How long" to "多久",
        "What happened" to "发生了什么", "What's wrong" to "怎么了",
        "Wait" to "等一下", "Hurry up" to "快点", "Come here" to "过来",
        "Look at this" to "看这个", "Try this" to "试试这个",
        "Follow me" to "关注我", "Subscribe" to "订阅",
        "I agree" to "同意", "Good luck" to "祝好运", "Take care" to "保重",
        "Let's go" to "走起", "For real" to "认真的", "Anyway" to "不管了",
        "By the way" to "对了", "To be honest" to "说实话",
        "I'm dying laughing" to "笑死我了", "Everything will be ok" to "一切都会好的",
        "john_doe" to "约翰", "alex_photo" to "阿力相册",
        "travelbug" to "旅行控", "food_lover" to "吃货本货",
    )

    private val jaToZh = mapOf(
        "設定" to "设置", "プロフィール編集" to "编辑资料",
        "フォロー" to "关注", "フォロー中" to "已关注",
        "メッセージ" to "私信", "共有" to "分享", "保存" to "收藏",
        "報告" to "举报", "削除" to "删除", "キャンセル" to "取消",
        "完了" to "完成", "次へ" to "下一步", "戻る" to "返回", "閉じる" to "关闭",
        "ホーム" to "首页", "検索" to "搜索", "投稿" to "发布",
        "アクティビティ" to "动态", "プロフィール" to "我的",
        "読み込み中..." to "加载中...", "結果なし" to "无结果",
        "ログイン" to "登录", "ログアウト" to "退出登录",
        "新規登録" to "注册", "ヘルプ" to "帮助", "お知らせ" to "通知",
        "いいね" to "赞", "コメント" to "评论", "シェア" to "分享",
        "フォロワー" to "粉丝", "ストーリー" to "快拍",
        "トレンド" to "趋势", "おすすめ" to "推荐", "人気" to "热门",
        "すごい" to "厉害", "きれい" to "好看", "かわいい" to "可爱",
        "おいしい" to "好吃", "楽しい" to "开心", "嬉しい" to "高兴",
        "悲しい" to "难过", "寂しい" to "寂寞",
        "羨ましい" to "羡慕", "最高" to "最棒", "完璧" to "完美",
        "素敵" to "很棒", "感動" to "感动", "面白い" to "有趣",
        "やばい" to "太强了", "マジ" to "真的", "信じられない" to "难以置信",
        "サイコー" to "太棒了", "エモい" to "有感觉",
        "ありがとう" to "谢谢", "すみません" to "对不起", "ごめんなさい" to "对不起",
        "おはよう" to "早上好", "こんにちは" to "你好", "こんばんは" to "晚上好",
        "おやすみ" to "晚安", "さようなら" to "再见", "またね" to "回头见",
        "お疲れ様" to "辛苦了", "頑張って" to "加油", "大丈夫" to "没关系",
        "了解" to "明白", "なるほど" to "原来如此", "確かに" to "确实",
        "お願いします" to "拜托了", "おめでとう" to "恭喜", "久しぶり" to "好久不见",
        "どこ" to "在哪", "いくら" to "多少钱", "なぜ" to "为什么",
    )

    private val koToZh = mapOf(
        "설정" to "设置", "프로필 편집" to "编辑资料",
        "팔로우" to "关注", "팔로잉" to "已关注",
        "메시지" to "私信", "공유" to "分享", "저장" to "收藏",
        "신고" to "举报", "삭제" to "删除", "취소" to "取消",
        "완료" to "完成", "다음" to "下一步", "뒤로" to "返回", "닫기" to "关闭",
        "홈" to "首页", "검색" to "搜索", "글쓰기" to "发帖",
        "로딩 중..." to "加载中...", "로그인" to "登录", "로그아웃" to "退出登录",
        "회원가입" to "注册", "도움말" to "帮助", "알림" to "通知",
        "좋아요" to "赞", "댓글" to "评论", "공유하기" to "分享",
        "팔로워" to "粉丝", "스토리" to "快拍", "라이브" to "直播",
        "피드" to "动态", "트렌드" to "趋势", "추천" to "推荐", "인기" to "热门",
        "대박" to "厉害", "예뻐요" to "好看", "귀여워" to "可爱",
        "맛있어" to "好吃", "좋아" to "喜欢", "재밌어" to "有趣",
        "슬퍼" to "难过", "부러워" to "羡慕", "최고" to "最棒", "완벽" to "完美",
        "멋져" to "很帅", "감동" to "感动", "웃겨" to "好笑",
        "미쳤어" to "疯了", "진짜" to "真的", "헐" to "天哪", "대단해" to "了不起",
        "감사합니다" to "谢谢", "미안합니다" to "对不起", "죄송합니다" to "抱歉",
        "안녕하세요" to "你好", "잘 가" to "再见", "또 봐" to "回头见",
        "수고했어" to "辛苦了", "파이팅" to "加油", "괜찮아" to "没关系",
        "알겠어" to "明白", "그렇구나" to "原来如此", "맞아" to "没错",
        "축하해" to "恭喜", "오랜만이야" to "好久不见",
        "어디" to "在哪", "얼마" to "多少钱", "왜" to "为什么",
        "행복해" to "幸福", "보고 싶어" to "想你", "사랑해" to "爱你",
    )

    private val deToZh = mapOf(
        "Einstellungen" to "设置", "Profil bearbeiten" to "编辑资料",
        "Folgen" to "关注", "Nachricht" to "私信",
        "Teilen" to "分享", "Speichern" to "收藏", "Melden" to "举报",
        "Löschen" to "删除", "Abbrechen" to "取消",
        "Fertig" to "完成", "Weiter" to "下一步", "Zurück" to "返回",
        "Startseite" to "首页", "Suchen" to "搜索",
        "Lädt..." to "加载中...", "Anmelden" to "登录",
        "Gefällt mir" to "赞", "Kommentare" to "评论",
        "Super" to "厉害", "Schön" to "好看",
        "Lecker" to "好吃", "Toll" to "很棒",
        "Danke" to "谢谢", "Entschuldigung" to "对不起",
        "Hallo" to "你好", "Tschüss" to "再见", "Gute Nacht" to "晚安",
    )

    private val frToZh = mapOf(
        "Paramètres" to "设置", "Modifier le profil" to "编辑资料",
        "Suivre" to "关注", "Message" to "私信",
        "Partager" to "分享", "Enregistrer" to "收藏", "Signaler" to "举报",
        "Supprimer" to "删除", "Annuler" to "取消",
        "Terminé" to "完成", "Suivant" to "下一步", "Retour" to "返回",
        "Accueil" to "首页", "Rechercher" to "搜索",
        "Chargement..." to "加载中...", "Connexion" to "登录",
        "J'aime" to "赞", "Commentaires" to "评论",
        "Super" to "厉害", "Beau" to "好看",
        "Délicieux" to "好吃", "Génial" to "很棒",
        "Merci" to "谢谢", "Pardon" to "对不起",
        "Bonjour" to "你好", "Au revoir" to "再见",
    )

    private val esToZh = mapOf(
        "Configuración" to "设置", "Editar perfil" to "编辑资料",
        "Seguir" to "关注", "Mensaje" to "私信",
        "Compartir" to "分享", "Guardar" to "收藏", "Denunciar" to "举报",
        "Eliminar" to "删除", "Cancelar" to "取消",
        "Hecho" to "完成", "Siguiente" to "下一步", "Atrás" to "返回",
        "Inicio" to "首页", "Buscar" to "搜索",
        "Cargando..." to "加载中...", "Iniciar sesión" to "登录",
        "Me gusta" to "赞", "Comentarios" to "评论",
        "Genial" to "厉害", "Bonito" to "好看",
        "Delicioso" to "好吃", "Excelente" to "很棒",
        "Gracias" to "谢谢", "Perdón" to "对不起",
        "Hola" to "你好", "Adiós" to "再见",
    )

    // ============================================================
    // 反向翻译词典
    // ============================================================

    private val zhToEn = mapOf(
        "好看" to "Nice!", "太好看了" to "So beautiful!", "真美" to "Gorgeous!",
        "好美" to "Beautiful!", "美" to "Beautiful!", "漂亮" to "Pretty!",
        "帅" to "Handsome!", "酷" to "Cool!",
        "拍得真好" to "Nice shot!", "太棒了" to "Amazing!", "赞" to "Great!",
        "喜欢" to "Love it!", "爱了" to "Love this!",
        "绝了" to "Incredible!", "厉害" to "Impressive!",
        "牛" to "Awesome!", "太牛了" to "So awesome!", "666" to "Awesome!",
        "不错" to "Not bad", "可以" to "Nice", "好的" to "OK", "很棒" to "Great job!",
        "哈哈" to "Haha", "哈哈哈" to "Hahaha",
        "笑死" to "LMAO", "笑死我了" to "I'm dying laughing",
        "天哪" to "Oh my god", "我的天" to "Oh my god",
        "老天" to "Oh lord", "我勒个老天爷" to "Oh my lord!",
        "我勒个去" to "What the heck!", "卧槽" to "Holy crap!",
        "我去" to "Whoa!", "真的假的" to "No way!", "不是吧" to "No way!",
        "羡慕" to "So jealous!", "想去" to "I want to go there!",
        "我也想去" to "I want to go too!",
        "这是哪里" to "Where is this?", "在哪" to "Where is this?",
        "什么时候去的" to "When did you go?",
        "学习了" to "Learned something!", "收藏了" to "Saved!",
        "马克" to "Bookmarked!", "同款" to "Where can I get this?",
        "链接" to "Link please!", "怎么做到的" to "How did you do that?",
        "太有才了" to "So talented!", "有才" to "Talented!",
        "加油" to "Keep it up!", "期待" to "Looking forward to it!",
        "等你更新" to "Waiting for your update!",
        "来晚了" to "Late to the party!", "前排" to "First!",
        "沙发" to "First comment!", "打卡" to "Checking in!",
        "火钳刘明" to "Commenting before this goes viral!",
        "你好" to "Hello", "谢谢" to "Thank you", "对不起" to "Sorry",
        "没关系" to "It's ok", "别担心" to "Don't worry",
        "好吃" to "Delicious", "美味" to "Yummy",
        "无语" to "Speechless", "离谱" to "Ridiculous",
        "破防了" to "I'm broken", "拿捏" to "Nailed it", "起飞" to "Let's gooo",
        "酸了" to "So salty", "绝绝子" to "Absolutely amazing",
        "好的" to "OK",
    )

    private val zhToJa = mapOf(
        "好看" to "いいね！", "厉害" to "すごい！",
        "可爱" to "かわいい！", "好吃" to "おいしい！",
        "谢谢" to "ありがとう", "对不起" to "すみません",
        "你好" to "こんにちは", "再见" to "さようなら",
        "加油" to "頑張って", "没关系" to "大丈夫",
        "太棒了" to "最高！", "喜欢" to "好き！",
        "哈哈" to "www", "哈哈哈" to "wwww", "笑死" to "草",
        "羡慕" to "羨ましい！", "想去" to "行きたい！",
        "开心" to "嬉しい！", "难过" to "悲しい",
        "无语" to "言葉にならない", "恭喜" to "おめでとう",
        "好久不见" to "久しぶり", "辛苦了" to "お疲れ様",
        "没错" to "確かに", "多少钱" to "いくら",
        "等一下" to "ちょっと待って", "快点" to "早く",
    )

    private val zhToKo = mapOf(
        "好看" to "예뻐요!", "厉害" to "대박!",
        "可爱" to "귀여워!", "好吃" to "맛있어!",
        "谢谢" to "감사합니다", "对不起" to "미안합니다",
        "你好" to "안녕하세요", "再见" to "안녕히 가세요",
        "加油" to "파이팅!", "没关系" to "괜찮아",
        "太棒了" to "최고예요!", "喜欢" to "좋아!",
        "哈哈" to "ㅋㅋ", "哈哈哈" to "ㅋㅋㅋ", "笑死" to "웃겨 죽겠다",
        "羡慕" to "부러워요!", "想去" to "가고 싶어요!",
        "开心" to "행복해요!", "难过" to "슬퍼요",
        "无语" to "할 말을 잃었어요", "恭喜" to "축하해요",
        "好久不见" to "오랜만이에요", "辛苦了" to "수고했어요",
        "没错" to "맞아요", "多少钱" to "얼마예요",
        "等一下" to "잠깐만요", "快点" to "빨리요",
    )

    private val zhToDe = mapOf(
        "好看" to "Schön!", "厉害" to "Super!",
        "谢谢" to "Danke", "对不起" to "Entschuldigung",
        "你好" to "Hallo", "再见" to "Tschüss",
        "太棒了" to "Toll!", "喜欢" to "Gefällt mir!",
        "哈哈" to "Haha", "好吃" to "Lecker!",
    )

    private val zhToFr = mapOf(
        "好看" to "Beau!", "厉害" to "Super!",
        "谢谢" to "Merci", "对不起" to "Pardon",
        "你好" to "Bonjour", "再见" to "Au revoir",
        "太棒了" to "Génial!", "好吃" to "Délicieux!",
    )

    private val zhToEs = mapOf(
        "好看" to "Bonito!", "厉害" to "Genial!",
        "谢谢" to "Gracias", "对不起" to "Perdón",
        "你好" to "Hola", "再见" to "Adiós",
        "太棒了" to "Excelente!", "好吃" to "Delicioso!",
    )
}
