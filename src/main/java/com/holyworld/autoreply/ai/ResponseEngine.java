package com.holyworld.autoreply.ai;

import com.holyworld.autoreply.HolyWorldAutoReply;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Response engine that matches player messages during checks to appropriate moderator responses.
 * Built from extensive log analysis of real moderator interactions on HolyWorld.
 */
public class ResponseEngine {

    private final ConcurrentHashMap<String, PlayerState> playerStates = new ConcurrentHashMap<>();
    private final List<ResponseRule> rules = new ArrayList<>();

    public ResponseEngine() {
        initializeRules();
    }

    // ======================== PLAYER STATE ========================

    public static class PlayerState {
        public long checkStartTime;
        public int messageCount = 0;
        public boolean askedForAnydesk = false;
        public boolean gaveCodes = false;
        public boolean offeredConfession = false;
        public boolean mentionedRudesk = false;
        public boolean mentionedRustdesk = false;
        public String lastResponseCategory = "";
        public long lastMessageTime = 0;

        public PlayerState() {
            this.checkStartTime = System.currentTimeMillis();
        }

        public long getElapsedMinutes() {
            return (System.currentTimeMillis() - checkStartTime) / 60000;
        }

        public int getRemainingMinutes() {
            int elapsed = (int) getElapsedMinutes();
            return Math.max(1, 7 - elapsed);
        }
    }

    // ======================== RULE SYSTEM ========================

    @FunctionalInterface
    private interface RuleMatcher {
        boolean matches(String msg, String lower, PlayerState state, String name);
    }

    @FunctionalInterface
    private interface RuleResponder {
        String respond(String msg, String lower, PlayerState state, String name);
    }

    private static class ResponseRule {
        final String category;
        final int priority;
        final RuleMatcher matcher;
        final RuleResponder responder;

        ResponseRule(String category, int priority, RuleMatcher matcher, RuleResponder responder) {
            this.category = category;
            this.priority = priority;
            this.matcher = matcher;
            this.responder = responder;
        }
    }

    // ======================== HELPERS ========================

    private static String pick(String... opts) {
        return opts[ThreadLocalRandom.current().nextInt(opts.length)];
    }

    private static boolean has(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private static boolean isCode(String text) {
        String c = text.replaceAll("[\\s\\-]", "");
        return c.matches("\\d{6,10}");
    }

    // ======================== RULES ========================

    private void initializeRules() {

        // ===== PRIORITY 100: INSULTS -> BAN SIGNAL =====
        rules.add(new ResponseRule("insult", 100,
            (msg, l, s, n) -> has(l,
                "нахуй", "нахуи", "пошел нах", "пошёл нах", "иди нах",
                "хуй", "хуи", "хуе", "хуё", "хуесос", "хуёсос",
                "ебал", "ебан", "ебат", "ебу", "ёба",
                "сука", "суки", "сучка", "блядь", "бляд", "блять",
                "далбаеб", "долбаеб", "долбоеб", "дебил",
                "мразь", "урод", "гандон", "гондон",
                "пидор", "пидр", "педик",
                "чмо", "чмошник",
                "безмамн", "мертвой мам", "мёртвой мам",
                "твою мать", "маму ебал", "маме пизд",
                "пузо вырезал", "сын бляд", "сын свинь",
                "соси", "саси", "сосо езз",
                "пизд", "пизду"),
            (msg, l, s, n) -> null
        ));

        // ===== PRIORITY 95: EXPLICIT CONFESSION =====
        rules.add(new ResponseRule("confession", 95,
            (msg, l, s, n) -> has(l,
                "я софт", "я читер", "я чит ", "я читор",
                "я с софт", "я с читами", "я играю с чит",
                "у меня софт", "у меня чит", "у меня читы",
                "у меня x-ray", "у меня xray",
                "я с модом", "я с софтом",
                "я читер бб", "я чит бань", "я чит баньте",
                "у меня селестиал", "у меня celestial",
                "у меня night", "с софтом бань",
                "признание ентити", "за хранения забань",
                "у меня всего x-ray",
                "все равно айпи сменю", "всё равно на этот акк",
                "мне все равно на этот", "мне всё равно на этот",
                "бань нахуй", "хочеш бань", "хочешь бань"),
            (msg, l, s, n) -> null
        ));

        // ===== PRIORITY 94: LEAVE / BB =====
        rules.add(new ResponseRule("leave", 94,
            (msg, l, s, n) -> {
                String t = l.trim();
                return t.equals("бб") || t.equals("bb") ||
                    has(l, "bb all", "бб всем", "all bb",
                        "лад баньте", "ладно баньте", "ладна банте",
                        "давай бан", "я жду бан",
                        "качать не охота", "качать не буду",
                        "не буду ничего скачивать",
                        "не хочу раст", "бб короче");
            },
            (msg, l, s, n) -> {
                if (has(l, "удачи")) return "Спасибо за сотрудничество";
                return null;
            }
        ));

        // ===== PRIORITY 93: EXPLICIT REFUSAL =====
        rules.add(new ResponseRule("refusal", 93,
            (msg, l, s, n) -> has(l,
                "отпусти", "мне лень",
                "забань на минимальн", "эту залупу",
                "я не буду ничего", "баньте"),
            (msg, l, s, n) -> {
                if (has(l, "мне лень", "залупу", "баньте", "не буду")) return null;
                return pick("Скачивай анидеск", "Аник жду");
            }
        ));

        // ===== PRIORITY 92: SHORT CONFESSION =====
        rules.add(new ResponseRule("confession_short", 92,
            (msg, l, s, n) -> {
                String t = l.trim();
                return t.equals("признание") || t.equals("признаюсь") || t.equals("признаю") ||
                       t.equals("го признание") ||
                       has(l, "признаюсь что", "я признаюсь", "я признаюс",
                           "хорошо я признаюсь", "ладно я софт");
            },
            (msg, l, s, n) -> null
        ));

        // ===== PRIORITY 85: ANYDESK/RUDESK CODE =====
        rules.add(new ResponseRule("code", 85,
            (msg, l, s, n) -> isCode(msg.replaceAll("[^\\d]", "")),
            (msg, l, s, n) -> {
                s.gaveCodes = true;
                return pick("Принимай", "+", "Грузит", "Ща подключусь", "Принимай запрос");
            }
        ));

        // ===== PRIORITY 83: DISCORD OFFER =====
        rules.add(new ResponseRule("discord", 83,
            (msg, l, s, n) -> has(l,
                "через дс", "давай дс", "дс можно", "го дс", "го в дс",
                "го через дс", "можно дс", "мб дс", "по дс",
                "давай в дс", "го по дс", "давай по дс",
                "через дискорд", "можно дискорд", "го дискорд",
                "мой дс", "могу в дс", "могу дс",
                "через д ", "go cheres ds", "go w ds",
                "это дс", "я дс кинул", "дс пойти",
                "в звонок", "пойдем в звонок",
                "давай в дискорд", "го по диск"),
            (msg, l, s, n) -> pick("-", "По дс проверки не проводим", "Анидеск скачивай", "Скачивай аник")
        ));

        // ===== PRIORITY 82: VK / TG / OTHER =====
        rules.add(new ResponseRule("other_platform", 82,
            (msg, l, s, n) -> has(l,
                "через вк", "го вк", "го в вк", "можно вк",
                "через тг", "го тг", "тг можно", "можно тг",
                "есть тг", "есть вк",
                "в коменты", "демонстрация", "демку",
                "го по вк"),
            (msg, l, s, n) -> pick("-", "Скачивай аник")
        ));

        // ===== PRIORITY 81: LM / MESSAGE OFFERS =====
        rules.add(new ResponseRule("lm_offer", 81,
            (msg, l, s, n) -> has(l, "можно в лс", "могу в лс", "кому в лс"),
            (msg, l, s, n) -> pick("Мне", "Принимай")
        ));

        // ===== PRIORITY 80: GREETING =====
        rules.add(new ResponseRule("greeting", 80,
            (msg, l, s, n) -> {
                if (s.messageCount > 3) return false;
                String t = l.trim();
                return has(t, "привет", "прив", "хай", "здравств", "приветик", "прывект") ||
                       t.equals("ку") || t.equals("qq") || t.equals("hi");
            },
            (msg, l, s, n) -> {
                if (has(l, "привет я не читер")) {
                    return pick("Привет давай аник", "Привет скачивай анидеск");
                }
                return pick(
                    "Это проверка на читы, у Вас есть 7 мин. чтобы скинуть AnyDesk и пройти проверку! Признание уменьшает наказание! В случае отказа/выхода/игнора - Бан!",
                    "Привет! Жду аник",
                    "qq жду аник",
                    "Приветики) Жду аник"
                );
            }
        ));

        // ===== PRIORITY 78: WHY CHECK / REASON =====
        rules.add(new ResponseRule("reason", 78,
            (msg, l, s, n) -> has(l,
                "за что", "причина", "за что прове",
                "почему вызвал", "за что вызвал",
                "почему меня", "что я сделал", "что я зделал",
                "а чо решил", "что случилось",
                "а за что", "за что собственно",
                "какая проверка", "а чё это",
                "я тока зашёл", "я только зашел",
                "я возле дк", "я бутылочки",
                "я зельки", "я сижу шахту",
                "я просто игра", "я на спавне",
                "а щас то за что", "в чем причина",
                "зачем вызвал", "а за что проверка",
                "за что проверка", "чего блять"),
            (msg, l, s, n) -> {
                if (has(l, "причина", "в чем причина")) {
                    return pick("Многочисленные репорты", "Репорты",
                        "Модератор в праве не разглашать причину проверки игроку", "За все хорошее");
                }
                return pick("За все хорошее", "Репорты", "Надо", "Многочисленные репорты", "Играл");
            }
        ));

        // ===== PRIORITY 77: NOT CHEATER =====
        rules.add(new ResponseRule("not_cheater", 77,
            (msg, l, s, n) -> has(l,
                "я не читер", "я не читар", "я не софт",
                "я чист", "у меня нет читов", "у меня нету читов",
                "без читов", "без софта", "я ансофт",
                "я 100% ансофт", "я не использую",
                "я легит", "я без", "я готов пройти"),
            (msg, l, s, n) -> pick("Скачивай аник", "Верю скачивай", "Аник жду",
                "Скачивай анидеск", "Ну я жду")
        ));

        // ===== PRIORITY 75: WHAT IS ANYDESK =====
        rules.add(new ResponseRule("what_anydesk", 75,
            (msg, l, s, n) -> has(l,
                "что за аник", "что такое аник", "что за анидеск",
                "что такое анидеск", "что это за прог",
                "что за прога", "удаленный доступ",
                "типо ты в моем", "будешь лазать",
                "управлять моим", "че за прога",
                "анидеск это что", "а что это"),
            (msg, l, s, n) -> {
                if (has(l, "типо ты в моем", "будешь лазать", "управлять моим")) return "+";
                return pick("Программа удаленного доступа", "Удаленный доступ",
                    "Программа такая", "Проверка");
            }
        ));

        // ===== PRIORITY 74: DOWNLOADING STATUS =====
        rules.add(new ResponseRule("downloading", 74,
            (msg, l, s, n) -> has(l,
                "скачиваю", "скачиваеться", "скачивается",
                "качаю", "качается", "загружается", "грузит",
                "устанавливаю", "устанавливается",
                "пачти скачался", "почти скачал",
                "немного осталось", "ок ща скачаю",
                "щас скачаю", "ща скачаю",
                "загрузил", "скачал", "жди качаю",
                "ок скачаю", "я качаю", "скачиваетсяя"),
            (msg, l, s, n) -> {
                if (has(l, "скачал", "загрузил", "скачался")) {
                    return pick("Кидай код", "Кидай длинный код", "Открывай его");
                }
                int r = s.getRemainingMinutes();
                return pick("Жду", r + " min", r + " минут", "Жду жду", "Время идет");
            }
        ));

        // ===== PRIORITY 73: CANT DOWNLOAD =====
        rules.add(new ResponseRule("cant_download", 73,
            (msg, l, s, n) -> has(l,
                "не скачивается", "не качается", "не загружается",
                "не грузит", "не могу скачать",
                "не работает", "не робит", "ошибка",
                "вирус", "трояны", "не дает скачать",
                "не запускается", "немагу", "не магу",
                "сайт не грузит", "не открывается",
                "виндоус", "антивирус", "бяка",
                "у меня не работает", "у меня не скачивается",
                "не могу понять", "не выходит",
                "у меня ошибка"),
            (msg, l, s, n) -> {
                if (has(l, "аник не", "анидеск не")) {
                    if (!s.mentionedRudesk) {
                        s.mentionedRudesk = true;
                        return pick("Скачивай RuDeskTop", "Качай рудеск", "Газуй рудеск");
                    }
                    if (!s.mentionedRustdesk) {
                        s.mentionedRustdesk = true;
                        return pick("Скачивай RustDesk", "Качай RustDesk");
                    }
                }
                if (!s.mentionedRudesk) {
                    s.mentionedRudesk = true;
                    return pick("Все должно работать", "Скачивай RuDeskTop");
                }
                return pick("Все должно работать", "Скачивай RustDesk");
            }
        ));

        // ===== PRIORITY 72: DONT HAVE ANYDESK =====
        rules.add(new ResponseRule("no_anydesk", 72,
            (msg, l, s, n) -> has(l,
                "нету аник", "нет аник", "у меня нету ани",
                "у меня нет ани", "аника нет", "анидеска нет",
                "нету такого", "нету его",
                "у меня нету", "нету программы",
                "нет программы", "у меня нет никакой",
                "просто нету", "тут анидеска нет"),
            (msg, l, s, n) -> pick("Скачивай", "Скачивай анидеск", "Качай")
        ));

        // ===== PRIORITY 71: RUDESK =====
        rules.add(new ResponseRule("rudesk", 71,
            (msg, l, s, n) -> has(l,
                "рудеск", "rudesk", "rudesktop", "рудесктоп",
                "рудекс", "рудекстор", "рудескоп",
                "можно по рудеск", "рудеск сойдет",
                "а рудеск не подойдет"),
            (msg, l, s, n) -> {
                s.mentionedRudesk = true;
                if (has(l, "можно", "подойдет", "сойдет")) return pick("+", "Газуй", "Да");
                return pick("+", "Газуй", "Скачивай");
            }
        ));

        // ===== PRIORITY 70: RUSTDESK =====
        rules.add(new ResponseRule("rustdesk", 70,
            (msg, l, s, n) -> has(l,
                "растдеск", "растдекс", "раст деск", "раст декс",
                "rustdesk", "rust desk"),
            (msg, l, s, n) -> {
                s.mentionedRustdesk = true;
                if (has(l, "можно", "подойдет", "сойдет", "могу")) return pick("+", "Да");
                return pick("+", "Скачивай");
            }
        ));

        // ===== PRIORITY 69: WHERE DOWNLOAD =====
        rules.add(new ResponseRule("where_download", 69,
            (msg, l, s, n) -> has(l,
                "где скачать", "как скачать", "откуда скачат",
                "хз как скачать", "с какого сайта",
                "какая ссылка", "какая сылка",
                "а что надо скачать", "что скачать",
                "что качать", "название ани",
                "а где код", "где код найти",
                "в плей маркете", "в плеймаркете",
                "в гугл плей", "где его найти",
                "скинь ссылку"),
            (msg, l, s, n) -> {
                if (has(l, "код", "где код")) {
                    return pick("При запуске сразу будет", "Прямо на самом видном месте",
                        "Открываешь и смотришь");
                }
                if (has(l, "название")) return "Да";
                return pick("anydesk com", "В гугле пиши анидеск", "Инструкция в чате",
                    "Инструкция в лс", "В браузере пиши anydesk com");
            }
        ));

        // ===== PRIORITY 68: PHONE PLAYER =====
        rules.add(new ResponseRule("phone", 68,
            (msg, l, s, n) -> has(l,
                "я с телефон", "с телефона", "на телефоне",
                "я на тел", "с мобильн", "на андроид"),
            (msg, l, s, n) -> pick("Скачивай аник на телефон", "Скачивай анидеск на телефон",
                "Вообще не волнует")
        ));

        // ===== PRIORITY 67: WHAT NEXT =====
        rules.add(new ResponseRule("what_next", 67,
            (msg, l, s, n) -> has(l,
                "что дальше", "чё дальше", "что делать",
                "чё делать", "что мне делать", "чо делать",
                "что скидывать", "что нужно делать",
                "как мне пройти", "что сделать",
                "куда жмать", "куда тыкать",
                "я не понимаю", "угу дальше",
                "чё делать то", "как пользоват",
                "что мне надо делать"),
            (msg, l, s, n) -> {
                if (s.gaveCodes) return pick("Принимай", "Принять нажми");
                if (s.askedForAnydesk) return pick("Кидай код", "Кидай длинный код", "Скидывай код");
                return pick("Скачивай анидеск", "Все инструкции в чате", "Инструкция в лс");
            }
        ));

        // ===== PRIORITY 66: TIME LEFT =====
        rules.add(new ResponseRule("time", 66,
            (msg, l, s, n) -> has(l,
                "скок времени", "сколько времени", "скок время",
                "скок минут", "сколько минут", "скок у меня",
                "сколько у меня", "сколько ещё", "сколько еще",
                "сколько осталось", "скок осталось",
                "доп время", "продли время", "дай время",
                "можно доп", "можно подождать"),
            (msg, l, s, n) -> {
                if (has(l, "доп", "продли", "подождать")) return pick("-", "Нет");
                int r = s.getRemainingMinutes();
                return pick(r + " min", r + " минут", r + " мин");
            }
        ));

        // ===== PRIORITY 65: WAIT =====
        rules.add(new ResponseRule("wait", 65,
            (msg, l, s, n) -> {
                String t = l.trim();
                return t.equals("ща") || t.equals("щас") || t.equals("сек") || t.equals("секу") ||
                    has(l, "подожд", "погод", "чуть чуть", "жди", "ша сек",
                        "щяс", "щаща", "щас сек", "ок щас");
            },
            (msg, l, s, n) -> {
                int r = s.getRemainingMinutes();
                return pick("Жду", r + " минут", "+", "Давай");
            }
        ));

        // ===== PRIORITY 64: CONFESSION QUESTION =====
        rules.add(new ResponseRule("confession_q", 64,
            (msg, l, s, n) -> has(l,
                "какое признание", "признание в чем", "что за признание",
                "какое", "на скок меньше", "на сколько забаните",
                "сколько бан", "на сколько бан", "а скок целый"),
            (msg, l, s, n) -> {
                s.offeredConfession = true;
                if (has(l, "какое")) return "Признание в читах";
                return pick("Признание 20 дней, отказ 30 дней", "30 дней", "на 10 дней");
            }
        ));

        // ===== PRIORITY 63: ACCEPT =====
        rules.add(new ResponseRule("accept", 63,
            (msg, l, s, n) -> has(l,
                "принял", "я принял", "как принять",
                "приинимать", "принимаю", "нет кнопки",
                "не пришло", "от имени", "от кого"),
            (msg, l, s, n) -> {
                if (has(l, "как принять", "нет кнопки")) return "Нажми кнопку принять";
                if (has(l, "от имени", "от кого")) return "Любой";
                if (has(l, "не пришло")) return "Принимай";
                return pick("+", "Пред 1/3 не трогай мышку");
            }
        ));

        // ===== PRIORITY 62: REGISTRATION =====
        rules.add(new ResponseRule("registration", 62,
            (msg, l, s, n) -> has(l, "регаюсь", "регаться", "регистрац", "зарегаю"),
            (msg, l, s, n) -> "Не надо там регаться"
        ));

        // ===== PRIORITY 61: MINIMAP =====
        rules.add(new ResponseRule("minimap", 61,
            (msg, l, s, n) -> has(l, "миникарта", "минимап", "пульс это"),
            (msg, l, s, n) -> {
                if (has(l, "пульс")) {
                    if (has(l, "офиц")) return "Не не софт";
                    return "Смотря какой";
                }
                return "+";
            }
        ));

        // ===== PRIORITY 60: REPORT PLAYER =====
        rules.add(new ResponseRule("report", 60,
            (msg, l, s, n) -> has(l,
                "тут один читер", "тут читер", "могу дать его ник",
                "против меня софтер", "стажеры с софтом", "стажёры с софтом"),
            (msg, l, s, n) -> {
                if (has(l, "могу дать", "могу ник")) return "Давай";
                if (has(l, "стажер", "стажёр")) return "Примем меры";
                return pick("Давай", "Напиши /cr nik");
            }
        ));

        // ===== PRIORITY 59: RESOURCE REQUESTS =====
        rules.add(new ResponseRule("resources", 59,
            (msg, l, s, n) -> has(l,
                "можно ресы", "ресы раздам", "можно сложити",
                "можно баблко", "деньги отдам", "дам сетку",
                "можно кинуть", "дай денег", "тимейту деньги",
                "можно груз", "можно пеперони"),
            (msg, l, s, n) -> pick("-", "Неа")
        ));

        // ===== PRIORITY 58: LEGAL CONCERNS =====
        rules.add(new ResponseRule("legal", 58,
            (msg, l, s, n) -> has(l,
                "не законно", "незаконно", "незаконо",
                "переживаю за", "не доверяю",
                "родительский контроль"),
            (msg, l, s, n) -> {
                if (has(l, "родительский")) return "Скачивай анидеск проси разрешения";
                return "1.Заходя на сервер вы соглашаетесь с правилами и при проверке вы обязаны предоставить анидеск";
            }
        ));

        // ===== PRIORITY 57: FROM RUSSIA =====
        rules.add(new ResponseRule("from_rf", 57,
            (msg, l, s, n) -> has(l, "я из рф", "я с рф", "из рф", "с рф", "из россии",
                "аник не ворк на территории"),
            (msg, l, s, n) -> {
                s.mentionedRudesk = true;
                return pick("Скачивай RuDeskTop", "Cкачивай RuDeskTop",
                    "Скачивай RuDeskTop или запускай впн на пк и с впн аник включай");
            }
        ));

        // ===== PRIORITY 56: VPN =====
        rules.add(new ResponseRule("vpn", 56,
            (msg, l, s, n) -> has(l, "впн", "vpn", "кикнет"),
            (msg, l, s, n) -> pick("Скачивай RuDeskTop значит", "Скачивай RuDeskTop")
        ));

        // ===== PRIORITY 55: PREVIOUSLY CHECKED =====
        rules.add(new ResponseRule("prev_check", 55,
            (msg, l, s, n) -> has(l,
                "меня проверяли", "уже проверяли",
                "вчера проверял", "проверяли сегодня",
                "я вчера прову"),
            (msg, l, s, n) -> {
                if (has(l, "сегодня")) return pick("Я тебя еще раз проверю", "Ща проверю");
                if (has(l, "вчера")) return "Обманывать не хорошо";
                return "Аник жду";
            }
        ));

        // ===== PRIORITY 54: PAID / FREE =====
        rules.add(new ResponseRule("paid", 54,
            (msg, l, s, n) -> has(l, "платная", "платный", "платно",
                "евро надо", "бесплатн", "расширеная"),
            (msg, l, s, n) -> pick("Она не платная", "Он бесплатный",
                "Заходишь на сайт anydesk com для домашнего использования")
        ));

        // ===== PRIORITY 52: YES/OK/DA =====
        rules.add(new ResponseRule("yes", 52,
            (msg, l, s, n) -> {
                String t = l.trim();
                return t.equals("да") || t.equals("да?") || t.equals("+") ||
                    t.equals("ок") || t.equals("окей") || t.equals("ладно") ||
                    t.equals("хорошо") || t.equals("понял") || t.equals("пон") ||
                    t.equals("угу") || t.equals("ну") || t.equals("ага") ||
                    t.equals("da") || t.equals("ladno") || t.equals("ну ок") ||
                    t.equals("тогд ок") || t.equals("тогда ок");
            },
            (msg, l, s, n) -> {
                if (!s.askedForAnydesk) {
                    s.askedForAnydesk = true;
                    return s.getRemainingMinutes() + " min у тебя";
                }
                if (s.gaveCodes) return pick("Принимай", "+");
                return pick("Жду", "+", "Давай", "Скачивай");
            }
        ));

        // ===== PRIORITY 51: QUESTION MARKS =====
        rules.add(new ResponseRule("qmarks", 51,
            (msg, l, s, n) -> l.trim().matches("\\?+"),
            (msg, l, s, n) -> {
                if (s.messageCount <= 2) return "Проверка";
                return pick("Аник жду", "Жду", "Скачивай аник");
            }
        ));

        // ===== PRIORITY 50: SHORT WORDS =====
        rules.add(new ResponseRule("short", 50,
            (msg, l, s, n) -> {
                String t = l.trim();
                return t.equals("аник") || t.equals("аник?") || t.equals("кидай") ||
                    t.equals("ну че") || t.equals("ну чо") || t.equals("го") || t.equals("go") ||
                    t.equals("вот") || t.equals("на") || t.equals("это?") ||
                    t.equals("ало") || t.equals("ау") || t.equals("аууу") ||
                    t.equals("модер") || t.equals("ты тут") || t.equals("ты тут?") ||
                    t.equals("ты здесь") || t.equals("ты здесь?") || t.equals("але");
            },
            (msg, l, s, n) -> {
                String t = l.trim();
                if (has(t, "аник")) return pick("+", "Жду код");
                if (has(t, "кидай")) return pick("Ты из рф?", "Кидай код");
                if (has(t, "вот", "на", "это")) return pick("+", "Принимай");
                if (has(t, "ты тут", "ты здесь", "ало", "ау", "модер", "але"))
                    return pick("Да да я тут", "+", "Я тут");
                return pick("Жду аник", "+", "Аник жду");
            }
        ));

        // ===== PRIORITY 48: WEAK PC =====
        rules.add(new ResponseRule("weak_pc", 48,
            (msg, l, s, n) -> has(l,
                "пк слаб", "комп слаб", "интернет слаб",
                "инет слаб", "инет говно", "пк за 15к",
                "медленно качается", "микроволновк",
                "черный экран", "лагает"),
            (msg, l, s, n) -> pick("Жду еще минута у тебя", s.getRemainingMinutes() + " минут", "Скачивай")
        ));

        // ===== PRIORITY 47: PUBLIC COMPUTER =====
        rules.add(new ResponseRule("public_pc", 47,
            (msg, l, s, n) -> has(l, "в компах", "в компьютерн", "нельзя скачивать"),
            (msg, l, s, n) -> pick("Зови админа аник обычно на таких компах есть", "Тг есть?")
        ));

        // ===== PRIORITY 46: EMOJI/EMOTIONAL =====
        rules.add(new ResponseRule("emotional", 46,
            (msg, l, s, n) -> {
                String t = l.trim();
                return t.equals(")") || t.matches("[)(]+") ||
                    t.equals("хаха") || t.equals("хахаха") || t.equals("ахахах") ||
                    t.equals("xd") || t.equals("найс") || t.equals("nais") ||
                    t.equals("круто") || t.equals("прикольно") ||
                    t.equals("гг") || t.equals("лол");
            },
            (msg, l, s, n) -> {
                if (has(l, "хаха", "ахах", "xd")) return pick("После проверки)", "Аник жду");
                if (has(l, ")")) return "Признание уменьшает срок на 35%";
                return pick("Аник жду", "+");
            }
        ));

        // ===== PRIORITY 45: STALLING =====
        rules.add(new ResponseRule("stalling", 45,
            (msg, l, s, n) -> has(l,
                "я в дубае", "расказу", "поговорим",
                "пока прову", "а можно пеперони",
                "забаниш я ночь", "в подушку плакать",
                "мне пизда", "я девка", "по приказу",
                "4 твинк", "чуть чуть"),
            (msg, l, s, n) -> {
                if (has(l, "мне пизда")) {
                    s.offeredConfession = true;
                    return "Признание уменьшает бан на 35%";
                }
                if (has(l, "подушку плакать")) return "Адекватно веди себя";
                return pick("Аник жду", "Бро не тяни время", "Аник или признание");
            }
        ));

        // ===== PRIORITY 44: AUTO CONFESSION OFFER =====
        rules.add(new ResponseRule("auto_confess", 44,
            (msg, l, s, n) -> s.messageCount > 5 && !s.offeredConfession && s.getElapsedMinutes() >= 3,
            (msg, l, s, n) -> {
                s.offeredConfession = true;
                return pick("Признание уменьшает наказание на 35%",
                    "Признание уменьшает срок бана на 35%",
                    "Давай что бы время не тратить ты признаешься и я забаню на 35% меньше");
            }
        ));

        // ===== PRIORITY 43: NO =====
        rules.add(new ResponseRule("no", 43,
            (msg, l, s, n) -> {
                String t = l.trim();
                return t.equals("нет") || t.equals("не") || t.equals("неа") || t.equals("нее") ||
                    t.startsWith("нееее");
            },
            (msg, l, s, n) -> pick("Тогда жду аник", "Скачивай аник", "Аник жду")
        ));

        // ===== PRIORITY 40: TRANSLITERATION =====
        rules.add(new ResponseRule("translit", 40,
            (msg, l, s, n) -> has(l, "vse bani", "i skacat ne mogy", "togda idi v pizdy"),
            (msg, l, s, n) -> {
                if (has(l, "vse bani")) return null;
                if (has(l, "pizdy")) return null;
                if (has(l, "ne mogy")) return "Ты сможешь я в тебя верю";
                return "Скачивай аник";
            }
        ));

        // ===== PRIORITY 38: CONNECTION ISSUES =====
        rules.add(new ResponseRule("connection", 38,
            (msg, l, s, n) -> has(l,
                "клиент не в сети", "не подключается",
                "соединение заверш", "не воркает",
                "ошибка подключ", "кинь еще раз",
                "попробуй еще раз"),
            (msg, l, s, n) -> pick("Скачивай RustDesk", "Переприми", "Переустанови аник")
        ));

        // ===== PRIORITY 35: ENGLISH TEXT =====
        rules.add(new ResponseRule("english", 35,
            (msg, l, s, n) -> has(l, "всё на англ", "все на англ", "на английском"),
            (msg, l, s, n) -> "У тебя " + s.getRemainingMinutes() + " минут осталось"
        ));

        // ===== PRIORITY 30: PLUGIN AD1 =====
        rules.add(new ResponseRule("plugin", 30,
            (msg, l, s, n) -> has(l, "плагин", "plugin", "ad1", "три линии", "полный доступ"),
            (msg, l, s, n) -> "нажать слева сверху на три линии в anydesk, настройкА --> Плагин AD1 --> Активировать!"
        ));

        // ===== PRIORITY 25: DONE / THANKS =====
        rules.add(new ResponseRule("done", 25,
            (msg, l, s, n) -> has(l,
                "я прошел", "я прошёл", "все?", "всё?",
                "спасибо", "спс"),
            (msg, l, s, n) -> pick("Рад помочь", "Пред 1/3 не трогай мышку", "+")
        ));

        // ===== PRIORITY 20: TRYING =====
        rules.add(new ResponseRule("trying", 20,
            (msg, l, s, n) -> has(l,
                "попробую", "постараюсь", "я тут", "я готов",
                "запускаю", "открыл", "открываю", "лан"),
            (msg, l, s, n) -> pick("Жду", s.getRemainingMinutes() + " минут", "+", "Давай")
        ));

        // ===== PRIORITY 10: CATCHALL =====
        rules.add(new ResponseRule("catchall", 10,
            (msg, l, s, n) -> true,
            (msg, l, s, n) -> {
                if (s.messageCount <= 1) {
                    return "Это проверка на читы, у Вас есть 7 мин. чтобы скинуть AnyDesk и пройти проверку! Признание уменьшает наказание!";
                }
                if (s.messageCount > 8 && !s.offeredConfession) {
                    s.offeredConfession = true;
                    return "Признание уменьшает наказание на 35%";
                }
                return pick("Аник жду", "Скачивай аник", "Жду анидеск", "Анидеск жду");
            }
        ));

        // Sort by priority descending
        rules.sort((a, b) -> Integer.compare(b.priority, a.priority));
    }

    // ======================== MAIN METHOD ========================

    public String getResponse(String playerMessage, String playerName) {
        if (playerMessage == null || playerMessage.trim().isEmpty()) return null;

        String lower = playerMessage.toLowerCase().trim();

        PlayerState state = playerStates.computeIfAbsent(playerName, k -> new PlayerState());
        state.messageCount++;
        state.lastMessageTime = System.currentTimeMillis();

        for (ResponseRule rule : rules) {
            try {
                if (rule.matcher.matches(playerMessage, lower, state, playerName)) {
                    String response = rule.responder.respond(playerMessage, lower, state, playerName);
                    state.lastResponseCategory = rule.category;

                    if (response == null) {
                        HolyWorldAutoReply.LOGGER.info("[AutoReply] BAN signal for {} ({}): {}",
                            playerName, rule.category, playerMessage);
                        return null;
                    }

                    HolyWorldAutoReply.LOGGER.info("[AutoReply] [{}] {} -> {}",
                        rule.category, playerMessage, response);
                    return response;
                }
            } catch (Exception e) {
                HolyWorldAutoReply.LOGGER.error("[AutoReply] Error in rule {}: {}",
                    rule.category, e.getMessage());
            }
        }

        return null;
    }

    public void clearPlayerState(String playerName) {
        playerStates.remove(playerName);
    }

    public void clearAllStates() {
        playerStates.clear();
    }
}
