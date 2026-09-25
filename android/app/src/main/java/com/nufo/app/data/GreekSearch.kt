package com.nufo.app.data

import java.text.Normalizer

/**
 * Makes Greek searches work against databases that are mostly English and accent-sensitive.
 *
 * Open Food Facts matches "γιαούρτι" and "γιαουρτι" as different words, and many Greek products are
 * catalogued under English names ("Feta cheese (P.D.O.)"), so one query is expanded into a few
 * variants: as typed, without accents, and translated to English when every Greek word is known.
 */
object GreekSearch {
    /** Lowercase, accents removed, final sigma unified: "Γιαούρτι" -> "γιαουρτι". */
    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('ς', 'σ').trim()

    fun hasGreek(s: String) = s.any { it in '\u0370'..'\u03FF' || it in '\u1F00'..'\u1FFF' }

    /** Distinct query variants to run, most specific first. */
    fun variants(query: String): List<String> {
        val typed = query.trim().replace(Regex("\\s+"), " ")
        if (!hasGreek(typed)) return listOf(typed)
        return listOfNotNull(typed, stripAccents(typed), english(typed)).distinctBy { it.lowercase() }
    }

    fun stripAccents(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** English translation when the whole phrase, or every Greek word in it, is in the dictionary. */
    fun english(query: String): String? {
        val norm = normalize(query)
        phrases[norm]?.let { return it }
        val words = norm.split(' ').filter { it.isNotBlank() }
        val out = words.map { w -> if (hasGreek(w)) words_[w] ?: return null else w }
        return out.joinToString(" ").replace(Regex("\\s+"), " ").trim().ifEmpty { null }
    }

    // canonical Greek -> English. Keys are normalized once at load time.
    private val raw = listOf(
        // dairy & eggs
        "γάλα" to "milk", "γιαούρτι" to "yogurt", "στραγγιστό" to "strained", "φέτα" to "feta", "τυρί" to "cheese",
        "κασέρι" to "kasseri", "γραβιέρα" to "graviera", "κεφαλοτύρι" to "kefalotyri", "μυζήθρα" to "mizithra",
        "ανθότυρο" to "anthotyro", "μανούρι" to "manouri", "χαλούμι" to "halloumi", "κεφαλογραβιέρα" to "kefalograviera",
        "βούτυρο" to "butter", "κρέμα γάλακτος" to "cream", "αυγά" to "eggs", "αυγό" to "egg", "κεφίρ" to "kefir", "ξινόγαλο" to "buttermilk",
        // oils, bread, pies
        "ελαιόλαδο" to "olive oil", "λάδι" to "oil", "ελιές" to "olives", "ελιά" to "olive", "ψωμί" to "bread",
        "φρυγανιές" to "rusks", "παξιμάδι" to "rusk", "παξιμάδια" to "rusks", "κουλούρι" to "sesame bread ring", "πίτα" to "pita",
        "τυρόπιτα" to "cheese pie", "σπανακόπιτα" to "spinach pie", "κρουασάν" to "croissant", "φύλλο" to "phyllo",
        // dishes
        "σουβλάκι" to "souvlaki", "γύρος" to "gyros", "μουσακάς" to "moussaka", "παστίτσιο" to "pastitsio",
        "γεμιστά" to "stuffed peppers", "ντολμαδάκια" to "dolmades", "τζατζίκι" to "tzatziki", "ταραμοσαλάτα" to "taramosalata",
        "χούμους" to "hummus", "φάβα" to "fava", "φασολάδα" to "bean soup", "χωριάτικη" to "greek salad", "αυγολέμονο" to "avgolemono",
        "κεφτέδες" to "meatballs", "μπιφτέκι" to "burger patty", "πίτσα" to "pizza", "σαλάτα" to "salad", "σούπα" to "soup",
        // pulses, grains, pasta
        "φασόλια" to "beans", "φακές" to "lentils", "ρεβίθια" to "chickpeas", "ρύζι" to "rice", "μακαρόνια" to "spaghetti",
        "ζυμαρικά" to "pasta", "κριθαράκι" to "orzo", "χυλοπίτες" to "noodles", "τραχανάς" to "trahanas", "πλιγούρι" to "bulgur",
        "αλεύρι" to "flour", "βρώμη" to "oats", "δημητριακά" to "cereal", "μούσλι" to "muesli", "κινόα" to "quinoa",
        // sweets & spreads
        "ζάχαρη" to "sugar", "μέλι" to "honey", "αλάτι" to "salt", "ταχίνι" to "tahini", "χαλβάς" to "halva",
        "λουκούμι" to "turkish delight", "παστέλι" to "sesame bar", "μπακλαβάς" to "baklava", "σοκολάτα" to "chocolate",
        "μπισκότα" to "biscuits", "μπισκότο" to "biscuit", "κράκερ" to "crackers", "παγωτό" to "ice cream", "μαρμελάδα" to "jam",
        "πραλίνα" to "hazelnut spread", "φυστικοβούτυρο" to "peanut butter", "κέικ" to "cake", "τσιπς" to "chips", "ποπκόρν" to "popcorn",
        "γλυκό του κουταλιού" to "spoon sweet", "μελομακάρονα" to "melomakarona", "κουραμπιέδες" to "kourabiedes",
        // drinks
        "καφές" to "coffee", "τσάι" to "tea", "χυμός" to "juice", "πορτοκαλάδα" to "orangeade", "λεμονάδα" to "lemonade",
        "νερό" to "water", "μπύρα" to "beer", "κρασί" to "wine", "ούζο" to "ouzo", "τσίπουρο" to "tsipouro",
        "αναψυκτικό" to "soft drink", "κόλα" to "cola", "σόδα" to "soda",
        // fruit & veg
        "μήλο" to "apple", "μήλα" to "apples", "μπανάνα" to "banana", "πορτοκάλι" to "orange", "λεμόνι" to "lemon",
        "σταφύλι" to "grapes", "σταφίδες" to "raisins", "σύκα" to "figs", "ντομάτα" to "tomato", "ντομάτες" to "tomatoes",
        "αγγούρι" to "cucumber", "πατάτα" to "potato", "πατάτες" to "potatoes", "κρεμμύδι" to "onion", "σκόρδο" to "garlic",
        "μελιτζάνα" to "eggplant", "κολοκυθάκι" to "zucchini", "σπανάκι" to "spinach", "καρότο" to "carrot", "πιπεριά" to "pepper",
        "μαρούλι" to "lettuce", "φράουλα" to "strawberry", "καρπούζι" to "watermelon", "πεπόνι" to "melon", "ροδάκινο" to "peach",
        "κεράσια" to "cherries", "αχλάδι" to "pear", "ακτινίδιο" to "kiwi", "αβοκάντο" to "avocado", "ρόδι" to "pomegranate",
        "αμύγδαλα" to "almonds", "καρύδια" to "walnuts", "φιστίκια" to "pistachios", "φουντούκια" to "hazelnuts", "ξηροί καρποί" to "nuts",
        // meat & fish
        "κοτόπουλο" to "chicken", "κρέας" to "meat", "μοσχάρι" to "beef", "χοιρινό" to "pork", "αρνί" to "lamb",
        "κιμάς" to "minced meat", "λουκάνικο" to "sausage", "λουκάνικα" to "sausages", "μπέικον" to "bacon", "ζαμπόν" to "ham",
        "γαλοπούλα" to "turkey", "σαλάμι" to "salami", "ψάρι" to "fish", "τόνος" to "tuna", "σολομός" to "salmon",
        "σαρδέλες" to "sardines", "γαύρος" to "anchovies", "καλαμάρι" to "squid", "χταπόδι" to "octopus", "γαρίδες" to "shrimp",
        "μύδια" to "mussels", "μπακαλιάρος" to "cod",
        // condiments
        "μαγιονέζα" to "mayonnaise", "κέτσαπ" to "ketchup", "μουστάρδα" to "mustard", "σάλτσα" to "sauce", "πελτές" to "tomato paste",
        "ξύδι" to "vinegar", "ρίγανη" to "oregano",
        // descriptors
        "ελαφρύ" to "light", "ελαφριά" to "light", "πλήρες" to "whole", "άπαχο" to "fat free", "βιολογικό" to "organic",
        "κατσικίσιο" to "goat", "πρόβειο" to "sheep", "αγελαδινό" to "cow", "φρέσκο" to "fresh", "χωρίς" to "without",
        "λακτόζη" to "lactose", "ζάχαρης" to "sugar", "σοκολατούχο" to "chocolate", "ολικής" to "wholegrain", "άλεσης" to "",
    )

    private val phrases: Map<String, String> = raw.filter { ' ' in it.first }.associate { normalize(it.first) to it.second }
    private val words_: Map<String, String> = raw.filter { ' ' !in it.first }.associate { normalize(it.first) to it.second }
}