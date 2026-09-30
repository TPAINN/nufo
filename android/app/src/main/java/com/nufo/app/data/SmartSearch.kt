package com.nufo.app.data

/**
 * Understands what was typed and ranks what the databases sent back.
 *
 * People search a food app the way they talk: «gala», «giaourti», «γιαουρτη», «yoghurt», «φαγε»,
 * «chiken breast». The databases only answer the exact words they index, and each answers in its
 * own order. This file turns the typed text into the queries worth sending (Greek, English, the
 * brand in both scripts, spelling fixed) and then scores every returned product against what the
 * person meant, so the best answer is first no matter which database it came from.
 *
 * Pure Kotlin, no Android, so all of it is unit-tested.
 */
object SmartSearch {

    /** One word of the query, with every form it may take in a product name. */
    data class Term(val typed: String, val forms: Set<String>)

    /** A brand, its name as printed and every spelling that should find it. */
    data class Brand(val display: String, val aliases: List<String>)

    data class Query(
        val raw: String,
        val terms: List<Term>,
        val brand: Brand?,
        /** Texts to send to Open Food Facts, most specific first. */
        val offQueries: List<String>,
        /** What the search was understood as, when that differs from what was typed (greeklish, typos). */
        val correction: String?,
        /** Words that mark what someone searching this food expects first («κοτόπουλο» → στήθος, φιλέτο). */
        val prefer: List<String> = emptyList(),
        /** The plain food people mean by a bare staple, in USDA's words ("chicken breast raw"). */
        val usdaIntent: String? = null,
        /** Typed in Greek or greeklish: this person shops in Greece and expects Greek products first. */
        val greekUser: Boolean = false,
    ) {
        /** A plain food ("banana", «φέτα») rather than a brand or a specific product. */
        val generic get() = brand == null && terms.size <= 2
    }

    data class Ranked(val hits: List<SearchHit>, val top: SearchHit?)

    // ---------- normalisation ----------

    fun normalize(s: String) = GreekSearch.normalize(s)

    /**
     * Greek as it sounds: ω→ο, η/υ/ει/οι→ι, αι→ε. «ψωμί», «ψομι» and greeklish «psomi» all become
     * «ψομι», which is how typos and greeklish find the right dictionary word.
     */
    fun phonetic(s: String): String = normalize(s)
        .replace("ει", "ι").replace("οι", "ι").replace("αι", "ε").replace("ου", "u")
        .replace('ω', 'ο').replace('η', 'ι').replace('υ', 'ι').replace('u', 'υ')

    private val GREEKLISH = listOf(
        "th" to "θ", "ch" to "χ", "ps" to "ψ", "ks" to "ξ", "ou" to "ου", "mp" to "μπ", "nt" to "ντ",
        "gk" to "γκ", "ts" to "τσ", "tz" to "τζ", "ei" to "ει", "oi" to "οι", "ai" to "αι", "ph" to "φ",
        "a" to "α", "b" to "β", "v" to "β", "g" to "γ", "d" to "δ", "e" to "ε", "z" to "ζ", "h" to "η",
        "i" to "ι", "k" to "κ", "c" to "κ", "q" to "κ", "l" to "λ", "m" to "μ", "n" to "ν", "x" to "χ",
        "o" to "ο", "w" to "ω", "p" to "π", "r" to "ρ", "s" to "σ", "t" to "τ", "y" to "υ", "u" to "ου",
        "f" to "φ", "j" to "τζ",
    )

    /** Latin letters to Greek as greeklish writers mean them: «giaourti» → «γιαουρτι». */
    fun greeklish(latin: String): String {
        var i = 0
        val s = latin.lowercase()
        val out = StringBuilder()
        while (i < s.length) {
            val hit = GREEKLISH.firstOrNull { s.startsWith(it.first, i) }
            if (hit == null) { out.append(s[i]); i++ } else { out.append(hit.second); i += hit.first.length }
        }
        return out.toString()
    }

    private fun isLatin(s: String) = s.any { it in 'a'..'z' || it in 'A'..'Z' } && !GreekSearch.hasGreek(s)

    /** Edit distance, capped: only small distances matter here. */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > 2) return 3
        var prev = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val cur = IntArray(b.length + 1)
            cur[0] = i + 1
            for (j in b.indices) cur[j + 1] = minOf(prev[j + 1] + 1, cur[j] + 1, prev[j] + if (a[i] == b[j]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    /** How far a word may be from a dictionary word and still be that word. */
    private fun tolerance(len: Int) = when { len >= 8 -> 2; len >= 4 -> 1; else -> 0 }

    // ---------- dictionaries ----------

    private val pairs = GreekSearch.raw.filter { it.second.isNotBlank() }

    /** phonetic Greek → accented Greek word, as it should be shown and sent. */
    private val greekByPhonetic: Map<String, String> = pairs.associate { phonetic(it.first) to it.first }

    /** English → accented Greek. Extra spellings people use are aliases of the dictionary word. */
    private val greekByEnglish: Map<String, String> = pairs.associate { it.second.lowercase() to it.first } + mapOf(
        "yoghurt" to "γιαούρτι", "yogourt" to "γιαούρτι", "greek yogurt" to "στραγγιστό γιαούρτι",
        "aubergine" to "μελιτζάνα", "courgette" to "κολοκυθάκι", "prawns" to "γαρίδες", "mince" to "κιμάς",
        "crisps" to "τσιπς", "biscuit" to "μπισκότο", "cookies" to "μπισκότα", "tomatoes" to "ντομάτες",
        "potatoes" to "πατάτες", "chicken breast" to "κοτόπουλο στήθος", "breast" to "στήθος",
        "greek salad" to "χωριάτικη", "cheese pie" to "τυρόπιτα", "spinach pie" to "σπανακόπιτα",
    )

    val BRANDS: List<Brand> = listOf(
        Brand("ΦΑΓΕ", listOf("fage", "φαγε")),
        Brand("ΔΕΛΤΑ", listOf("delta", "δελτα")),
        Brand("Όλυμπος", listOf("olympos", "olympus", "ολυμπος")),
        Brand("ΜΕΒΓΑΛ", listOf("mevgal", "μεβγαλ")),
        Brand("Κρι Κρι", listOf("kri kri", "κρι κρι", "krikri")),
        Brand("Δωδώνη", listOf("dodoni", "δωδωνη")),
        Brand("ΝΟΥΝΟΥ", listOf("nounou", "νουνου")),
        Brand("Lacta", listOf("lacta", "λακτα")),
        Brand("ΙΟΝ", listOf("ion", "ιον")),
        Brand("Misko", listOf("misko", "μισκο")),
        Brand("Μέλισσα", listOf("melissa", "μελισσα")),
        Brand("Barilla", listOf("barilla", "μπαριλα")),
        Brand("Παπαδοπούλου", listOf("papadopoulou", "παπαδοπουλου")),
        Brand("Αλλατίνη", listOf("allatini", "αλλατινη")),
        Brand("Μινέρβα", listOf("minerva", "μινερβα")),
        Brand("Άλτις", listOf("altis", "αλτισ")),
        Brand("ΑΓΝΟ", listOf("agno", "αγνο")),
        Brand("ΕΨΑ", listOf("epsa", "εψα")),
        Brand("Λουξ", listOf("loux", "λουξ")),
        Brand("Coca-Cola", listOf("coca cola", "coca-cola", "cocacola", "κοκα κολα", "coke")),
        Brand("Nutella", listOf("nutella", "νουτελα")),
        Brand("Kinder", listOf("kinder", "κιντερ")),
        Brand("Pringles", listOf("pringles", "πρινγκλεσ")),
        Brand("Lay's", listOf("lays", "lay's", "λεισ")),
        Brand("Nescafé", listOf("nescafe", "νεσκαφε")),
        Brand("Kellogg's", listOf("kelloggs", "kellogg's", "kellogg", "κελογκσ")),
        Brand("Danone", listOf("danone", "νταννον")),
        Brand("Activia", listOf("activia", "ακτιβια")),
        Brand("Βλάχα", listOf("vlacha", "βλαχα")),
        Brand("Γιώτης", listOf("jotis", "giotis", "γιωτησ")),
        Brand("Κύκνος", listOf("kyknos", "κυκνοσ")),
        Brand("Hellmann's", listOf("hellmanns", "hellmann's", "χελμανσ")),
        Brand("Heinz", listOf("heinz", "χαινζ")),
        Brand("Oreo", listOf("oreo", "ορεο")),
        Brand("Milka", listOf("milka", "μιλκα")),
    )

    private fun brandIn(tokens: List<String>): Pair<Brand, IntRange>? {
        for (size in 2 downTo 1) for (start in 0..tokens.size - size) {
            val chunk = tokens.subList(start, start + size).joinToString(" ")
            val key = if (isLatin(chunk)) chunk.lowercase() else normalize(chunk)
            val b = BRANDS.firstOrNull { br -> br.aliases.any { it == key || phonetic(it) == phonetic(key) } }
            if (b != null) return b to (start until start + size)
        }
        return null
    }

    // ---------- intent ----------

    /**
     * What people mean by a bare staple. Someone typing «κοτόπουλο» wants breast or fillet, not
     * chicken feet, skin or orange chicken; «κιμάς» means minced beef. [prefer] lifts those cuts,
     * [usda] asks USDA for the plain version so it is there to be lifted.
     */
    private class Intent(val prefer: List<String>, val usda: String)

    private val INTENTS: Map<String, Intent> = mapOf(
        "κοτόπουλο" to Intent(listOf("στηθοσ", "στηθοσ", "φιλετο", "breast", "fillet", "ολοκληρο", "whole", "μπουτι", "thigh"), "chicken breast raw"),
        "κιμάς" to Intent(listOf("κιμασ", "ground", "μοσχαρισιοσ", "μοσχαρι", "beef"), "ground beef raw"),
        "μοσχάρι" to Intent(listOf("μπριζολα", "steak", "ground", "κιμασ", "ψαχνο"), "beef steak raw"),
        "χοιρινό" to Intent(listOf("μπριζολα", "chop", "loin", "φιλετο", "tenderloin"), "pork loin raw"),
        "αρνί" to Intent(listOf("μπουτι", "leg", "παιδακια", "chop"), "lamb leg raw"),
        "σολομός" to Intent(listOf("φιλετο", "fillet", "atlantic", "ατλαντικου"), "salmon atlantic raw"),
        "τόνος" to Intent(listOf("νερο", "water", "λαδι", "oil", "canned", "κονσερβα"), "tuna canned water"),
        "αυγά" to Intent(listOf("egg", "whole", "φρεσκα", "large", "medium"), "egg whole raw"),
        "αυγό" to Intent(listOf("egg", "whole", "φρεσκο", "large", "medium"), "egg whole raw"),
        "γάλα" to Intent(listOf("φρεσκο", "fresh", "πληρεσ", "whole", "ελαφρυ", "light"), "milk whole"),
        "γιαούρτι" to Intent(listOf("στραγγιστο", "strained", "greek", "ελληνικο", "plain", "αγελαδοσ", "προβειο"), "greek yogurt plain"),
        "φέτα" to Intent(listOf("ποπ", "pdo", "βαρελισια", "feta"), "feta cheese"),
        "ρύζι" to Intent(listOf("νυχακι", "καρολινα", "λευκο", "white", "basmati", "long"), "rice white raw"),
        "ψωμί" to Intent(listOf("σταρενιο", "ολικησ", "whole", "wheat", "white", "τοστ"), "bread white"),
        "μπανάνα" to Intent(listOf("raw", "ωμη", "φρεσκια"), "bananas raw"),
        "πατάτες" to Intent(listOf("raw", "ωμεσ", "φρεσκεσ"), "potatoes raw"),
        "ελαιόλαδο" to Intent(listOf("παρθενο", "virgin", "εξαιρετικο", "extra"), "olive oil"),
        "ντομάτα" to Intent(listOf("raw", "φρεσκια", "ωμη"), "tomatoes raw"),
        "μήλο" to Intent(listOf("raw", "φρεσκο"), "apples raw"),
    ).mapKeys { normalize(it.key) }

    /** Parts and preparations nobody means by the bare word; they sink unless typed. */
    private val SIDE = Regex("(^|[^\\p{L}])(skin|feet|back|neck|giblets|gizzard|heart|liver|roll|soup|sauce|pudding|nectar|chips|split|pie|nuggets|tube|nfs|dessert|baby food|babyfood|ποδια|πετσα|συκωτι|σουπα|σαλτσα|πουτιγκα|νεκταρ|τσιπσ|πιτα|nuggets|κροκετ|τοφου|tofu|soy|σογια|orange|imitation|breaded|coated|patty|patties|luncheon|style|chili|curry|noodles|noodle|sausage|sausages|λουκανικ\\p{L}*|bites|snack|snacks|πανε|nuggets|burger|burgers|μπιφτεκ\\p{L}*|κεφτεδ\\p{L}*|salad|σαλατα)([^\\p{L}]|$)")

    // Java's \W treats every Greek letter as a non-word character, so boundaries are spelled out.
    private val FRESH = Regex("(^|[^\\p{L}])(raw|ωμ\\p{L}*|fresh|φρεσκ\\p{L}*)([^\\p{L}]|$)")

    // ---------- pictures ----------

    private val DISH_HEADS = setOf("soup", "sauce", "juice", "pudding", "chips", "salad", "pie", "bread", "cake", "nectar")

    /**
     * The food a plain USDA entry is about, to fetch its picture by: "Bananas, raw" → "Bananas",
     * "Soup, chicken, canned" → "chicken soup". USDA publishes no photos, and a photo of the food
     * beats a letter in a box. Null for names too vague to picture.
     */
    fun imageSubject(usdaName: String): String? {
        val parts = usdaName.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() && it != "nfs" }
        val head = parts.firstOrNull() ?: return null
        if (head.length < 3 || head.any(Char::isDigit)) return null
        return if (head in DISH_HEADS && parts.size > 1) "${parts[1]} $head" else head
    }

    /** Wikipedia pages about the food rather than the animal: "Chicken" is a photo of a rooster. */
    private val FOOD_TITLES = mapOf(
        "chicken" to "Chicken as food", "egg" to "Egg as food", "eggs" to "Egg as food", "fish" to "Fish as food",
        "lamb" to "Lamb and mutton", "turkey" to "Turkey meat", "salmon" to "Salmon as food", "pork" to "Pork",
        "beef" to "Beef", "duck" to "Duck as food", "rabbit" to "Rabbit meat", "goat" to "Goat meat",
        "squid" to "Squid as food", "octopus" to "Octopus as food", "shrimp" to "Shrimp and prawn as food",
        "minced meat" to "Ground meat", "meat" to "Meat",
    )

    private fun lead(q: Query) = q.terms.firstOrNull { t -> t.typed.none(Char::isDigit) && q.brand?.display != t.typed }

    /**
     * What to picture for a result that has no photo of its own: the food a USDA entry is about, or
     * for a database product without a photo, the food searched for — a jar of yoghurt with no
     * picture still shows yoghurt, not a letter in a box. Null when the row already has a photo.
     */
    fun pictureSubject(hit: SearchHit, q: Query): String? {
        if (hit.imageUrl != null) return null
        val subject = if (hit.source == UsdaParser.SOURCE) imageSubject(hit.name) else {
            val t = lead(q) ?: return null
            val words = normalize(hit.name).split(SPLIT).filter { it.isNotBlank() }
            if (termScore(t, words, normalize(hit.name)) < 0.85) return null
            t.forms.firstOrNull { greekByEnglish.containsKey(it) }
        } ?: return null
        return FOOD_TITLES[subject] ?: FOOD_TITLES[subject.removeSuffix("s")] ?: subject
    }

    // ---------- understanding ----------

    /** [english] words typed in English are shown back in English when corrected. */
    private data class Word(val typed: String, val greek: String?, val english: String?, val corrected: Boolean, val typedEnglish: Boolean = false, val greeklish: Boolean = false)

    private fun lookupGreek(key: String): String? {
        greekByPhonetic[key]?.let { return it }
        val tol = tolerance(key.length)
        if (tol == 0) return null
        return greekByPhonetic.entries
            .map { it to distance(it.key, key) }
            .filter { it.second <= tol }
            .minByOrNull { it.second }?.first?.value
    }

    private fun understandWord(w: String): Word {
        if (w.any { it.isDigit() }) return Word(w, null, null, false)
        if (GreekSearch.hasGreek(w)) {
            val greek = lookupGreek(phonetic(w))
            val english = greek?.let { GreekSearch.english(it) }
            return Word(w, greek, english, greek != null && normalize(greek) != normalize(w))
        }
        val lw = w.lowercase()
        greekByEnglish[lw]?.let { return Word(w, it, lw, false, typedEnglish = true) }
        greekByPhonetic[phonetic(greeklish(lw))]?.let { return Word(w, it, GreekSearch.english(it), true, greeklish = true) }
        val tol = tolerance(lw.length)
        if (tol > 0) {
            greekByEnglish.entries.filter { ' ' !in it.key }.map { it to distance(it.key, lw) }
                .filter { it.second <= tol }.minByOrNull { it.second }
                ?.let { (e, _) -> return Word(w, e.value, e.key, true, typedEnglish = true) }
            lookupGreek(phonetic(greeklish(lw)))?.let { return Word(w, it, GreekSearch.english(it), true, greeklish = true) }
        }
        return Word(w, null, null, false)
    }

    fun understand(input: String): Query {
        val raw = input.replace(Regex("""[+\-&|!(){}\[\]^"~*?:\\/]"""), " ").trim().replace(Regex("\\s+"), " ")
        if (raw.isEmpty()) return Query(raw, emptyList(), null, emptyList(), null)
        val tokens = raw.split(' ')
        val found = brandIn(tokens)
        val rest = tokens.filterIndexed { i, _ -> found == null || i !in found.second }

        // A whole phrase the dictionary knows («olive oil», «ελαιόλαδο») beats word by word.
        val phraseKey = rest.joinToString(" ").lowercase()
        val phraseGreek = greekByEnglish[phraseKey] ?: greekByPhonetic[phonetic(phraseKey)]?.takeIf { ' ' in phraseKey }
        val words = if (phraseGreek != null && rest.size > 1) {
            listOf(Word(rest.joinToString(" "), phraseGreek, GreekSearch.english(phraseGreek) ?: phraseKey, false))
        } else rest.map(::understandWord)

        val brand = found?.first
        val terms = words.map { w ->
            val forms = buildSet {
                // Greeklish as typed ("kima") is not a word any product uses; «κιμάς» and "ground beef" are.
                if (!w.greeklish) add(normalize(w.typed))
                // Phrases stay whole ("olive oil"): matching either word alone found olive tapenade.
                w.greek?.let { add(normalize(it)) }
                w.english?.let { add(it.lowercase()) }
            }.filter { it.isNotBlank() }.toSet()
            Term(w.typed, forms)
        } + listOfNotNull(brand?.let { b -> Term(b.display, b.aliases.map { if (isLatin(it)) it.lowercase() else normalize(it) }.toSet() + normalize(b.display)) })

        val brandLatin = brand?.aliases?.firstOrNull(::isLatin)
        val brandGreek = brand?.aliases?.firstOrNull { !isLatin(it) }
        val greekText = words.joinToString(" ") { it.greek ?: it.typed }
        val englishText = if (words.all { it.english != null || it.typed.any(Char::isDigit) }) words.joinToString(" ") { it.english ?: it.typed } else null
        fun withBrand(text: String?, b: String?) = listOfNotNull(b, text?.takeIf { it.isNotBlank() }).joinToString(" ").ifBlank { null }

        val off = listOfNotNull(
            raw,
            withBrand(greekText, brand?.display),
            withBrand(englishText, brandLatin),
            brandGreek?.let { withBrand(greekText, it) },
            GreekSearch.stripAccents(raw).takeIf { GreekSearch.hasGreek(raw) },
        ).map { it.trim() }.filter { it.isNotBlank() }.distinctBy { normalize(it) }.take(4)

        val corrected = words.any { it.corrected }
        val intent = if (brand == null && words.size == 1) words[0].greek?.let { INTENTS[normalize(it)] } else null
        val shown = withBrand(words.joinToString(" ") { if (it.typedEnglish) it.english ?: it.typed else it.greek ?: it.typed }, brand?.display)
        return Query(raw, terms, brand, off, shown?.takeIf { corrected && normalize(it) != normalize(raw) }, intent?.prefer.orEmpty(), intent?.usda,
            greekUser = GreekSearch.hasGreek(raw) || words.any { it.greeklish })
    }

    // ---------- ranking ----------

    private val SPLIT = Regex("[^\\p{L}\\p{N}%]+")

    /** Word stem good enough for ranking: «γιαούρτια» and «γιαούρτι», "tomatoes" and "tomato". */
    fun stem(w: String): String {
        var s = w
        if (s.length > 4) s = s.removeSuffix("es").removeSuffix("s")
        while (s.length > 4 && s.last() in "αεηιουωσ") s = s.dropLast(1)
        return s
    }

    private fun termScore(term: Term, words: List<String>, text: String): Double {
        var best = 0.0
        for (f in term.forms) {
            val parts = f.split(' ').filter { it.isNotBlank() }
            val s = if (parts.size > 1) {
                if (text.contains(f)) 1.0 else if (parts.all { p -> words.any { it == p || stem(it) == stem(p) } }) 0.85 else 0.0
            } else when {
                f in words -> 1.0
                words.any { phonetic(it) == phonetic(f) } -> 0.95
                words.any { stem(it) == stem(f) } -> 0.85
                words.any { it.startsWith(f) && f.length >= 3 } -> 0.75
                f.length >= 4 && text.contains(f) -> 0.45
                else -> 0.0
            }
            if (s > best) best = s
        }
        return best
    }

    private val WITH = Regex("(^|\\s)(με|with|γευση|γευσεισ|flavou?r|flavored|flavoured|αρωμα|aroma|sauce for|για)(\\s|$)")

    fun score(hit: SearchHit, q: Query): Double {
        if (q.terms.isEmpty()) return 0.0
        val name = normalize(hit.name)
        val brand = normalize(hit.brand.orEmpty())
        val words = name.split(SPLIT).filter { it.isNotBlank() }
        val all = words + brand.split(SPLIT).filter { it.isNotBlank() }
        val text = "$name $brand"
        val scores = q.terms.map { termScore(it, all, text) }
        val relevance = scores.average() * if (scores.any { it == 0.0 }) 0.35 else 1.0

        var s = relevance
        // The food itself leads the name: "Feta cheese" for «φέτα», not "Salad with feta" or "Tofu yogurt".
        val lead = q.terms.firstOrNull { t -> t.typed.none(Char::isDigit) && q.brand?.display != t.typed }
        fun isLead(w: String) = lead != null && lead.forms.any { f -> w == f || stem(w) == stem(f) || phonetic(w) == phonetic(f) || (' ' in f && w in f.split(' ')) }
        val leads = words.firstOrNull()?.let(::isLead) == true
        when {
            leads -> s += 0.2
            words.getOrNull(1)?.let(::isLead) == true -> s += 0.08
            lead != null && WITH.containsMatchIn(name) -> s -= 0.25
        }
        // What the bare word means («κοτόπουλο» → breast), and what it does not (feet, soup, pudding).
        if (q.generic) {
            val typed = normalize(q.raw)
            if (q.prefer.any { p -> words.any { it == p || stem(it) == stem(p) } || name.contains(p) }) s += 0.3
            // Unless it is the word searched for: «πορτοκάλι» must not sink oranges as "orange".
            SIDE.find(name)?.let { m -> val w = m.groupValues[2]; if (!typed.contains(w) && q.terms.none { w in it.forms }) s -= 0.35 }
            if (FRESH.containsMatchIn(name)) s += 0.06
        }
        // Precise names beat long ones once every word is matched.
        s -= (words.size - q.terms.size - 3).coerceIn(0, 10) * 0.02
        if (hit.soldInGreece) s += if (q.greekUser) 0.5 else 0.15
        s += listOf(hit.imageUrl, hit.caloriesPer100g, hit.nutriscoreGrade).count { it != null } * 0.04
        // A plain USDA entry ("Bananas, raw") is a good answer for a plain food, but only when the
        // food is what it is about, and never ahead of the Greek products people buy.
        val genericEntry = hit.source == UsdaParser.SOURCE && hit.brand == null
        if (q.generic && genericEntry && relevance >= 0.85 && leads) s += 0.1
        if (q.brand != null) {
            val b = q.brand.aliases.any { a -> text.contains(if (isLatin(a)) a.lowercase() else normalize(a)) } ||
                phonetic(brand).contains(phonetic(q.brand.display))
            s += if (b) 0.3 else -0.3
        }
        return s
    }

    /**
     * Best first, one row per product (the same yoghurt from two databases is one answer), and a
     * "top" answer when the best row matches every word of the query.
     */
    fun rank(hits: List<SearchHit>, q: Query): Ranked {
        val scored = hits.map { it to score(it, q) }
            .groupBy { (h, _) -> h.barcode?.trimStart('0') ?: "${normalize(h.name)}|${normalize(h.brand.orEmpty())}" }
            .map { (_, same) -> same.maxBy { it.second } }
            .sortedByDescending { it.second }
        val top = scored.firstOrNull()?.takeIf { (h, s) ->
            val words = normalize(h.name).split(SPLIT) + normalize(h.brand.orEmpty()).split(SPLIT)
            q.terms.all { termScore(it, words.filter(String::isNotBlank), normalize("${h.name} ${h.brand.orEmpty()}")) >= 0.75 } && s >= 1.0
        }?.first
        return Ranked(scored.map { it.first }, top)
    }

    // ---------- suggestions ----------

    private val suggestionPool: List<String> by lazy {
        (pairs.map { it.first } + BRANDS.map { it.display }).distinct()
    }

    /**
     * Completions while typing, in the script the person is writing in: «γιαο» → γιαούρτι,
     * "yog" → yogurt and γιαούρτι, «gal» → γάλα. Shortest first, since they are the common words.
     */
    fun suggest(prefix: String, limit: Int = 6): List<String> {
        val p = prefix.trim()
        if (p.length < 2) return emptyList()
        val out = LinkedHashSet<String>()
        if (isLatin(p)) {
            val lp = p.lowercase()
            greekByEnglish.keys.filter { it.startsWith(lp) }.sortedBy { it.length }.forEach { out += it; out += greekByEnglish.getValue(it) }
            BRANDS.filter { b -> b.aliases.any { it.startsWith(lp) } }.forEach { out += it.display }
            val gp = phonetic(greeklish(lp))
            suggestionPool.filter { phonetic(it).startsWith(gp) }.sortedBy { it.length }.forEach { out += it }
        } else {
            // Plain and phonetic: «γιαο» is half of «ου», which the phonetic key has already merged.
            val np = phonetic(p)
            val nn = normalize(p)
            suggestionPool.filter { normalize(it).startsWith(nn) || phonetic(it).startsWith(np) }.sortedBy { it.length }.forEach { out += it }
        }
        return out.filter { normalize(it) != normalize(p) }.take(limit)
    }
}