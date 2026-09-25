package com.nufo.app.data

/**
 * Names Nufo shows when the Open Food Facts taxonomy has no translation for a tag.
 * Each entry is (English, Greek). Anything missing here falls back to a prettified tag id.
 */
object TagNames {
    private val names: Map<String, Pair<String, String>> = mapOf(
        // EU's 14 regulated allergens
        "en:gluten" to ("Gluten" to "Γλουτένη"),
        "en:milk" to ("Milk" to "Γάλα"),
        "en:eggs" to ("Eggs" to "Αυγά"),
        "en:nuts" to ("Tree nuts" to "Ξηροί καρποί"),
        "en:peanuts" to ("Peanuts" to "Αράπικα φιστίκια"),
        "en:soybeans" to ("Soy" to "Σόγια"),
        "en:fish" to ("Fish" to "Ψάρι"),
        "en:crustaceans" to ("Crustaceans" to "Οστρακοειδή"),
        "en:molluscs" to ("Molluscs" to "Μαλάκια"),
        "en:sesame-seeds" to ("Sesame" to "Σουσάμι"),
        "en:celery" to ("Celery" to "Σέλινο"),
        "en:mustard" to ("Mustard" to "Μουστάρδα"),
        "en:lupin" to ("Lupin" to "Λούπινο"),
        "en:sulphur-dioxide-and-sulphites" to ("Sulphites" to "Θειώδη"),
        // Common labels on Greek shelves
        "en:organic" to ("Organic" to "Βιολογικό"),
        "en:eu-organic" to ("EU organic" to "Βιολογικό ΕΕ"),
        "en:pdo" to ("PDO" to "ΠΟΠ"),
        "en:pgi" to ("PGI" to "ΠΓΕ"),
        "en:vegan" to ("Vegan" to "Vegan"),
        "en:vegetarian" to ("Vegetarian" to "Χορτοφαγικό"),
        "en:no-gluten" to ("Gluten-free" to "Χωρίς γλουτένη"),
        "en:no-lactose" to ("Lactose-free" to "Χωρίς λακτόζη"),
        "en:no-added-sugar" to ("No added sugar" to "Χωρίς προσθήκη ζάχαρης"),
        "en:no-preservatives" to ("No preservatives" to "Χωρίς συντηρητικά"),
        "en:no-colorings" to ("No colourings" to "Χωρίς χρωστικές"),
        "en:fair-trade" to ("Fair trade" to "Δίκαιο εμπόριο"),
        "en:green-dot" to ("Green Dot" to "Πράσινο σημείο"),
        "en:made-in-greece" to ("Made in Greece" to "Παράγεται στην Ελλάδα"),
        "en:produced-in-greece" to ("Produced in Greece" to "Παράγεται στην Ελλάδα"),
        "en:vegetarian-society-approved" to ("Vegetarian Society approved" to "Εγκεκριμένο από Vegetarian Society"),
        "en:high-protein" to ("High protein" to "Υψηλή πρωτεΐνη"),
        "en:source-of-fibre" to ("Source of fibre" to "Πηγή φυτικών ινών"),
    )

    fun fallback(tag: String, lang: String): String? = names[tag]?.let { if (lang == "el") it.second else it.first }
}

/** Vitamins and minerals Nufo reads, keyed by Open Food Facts nutrient id: (English, Greek). */
object Micronutrients {
    val vitamins = linkedMapOf(
        "vitamin-a" to ("Vitamin A" to "Βιταμίνη A"), "vitamin-d" to ("Vitamin D" to "Βιταμίνη D"),
        "vitamin-e" to ("Vitamin E" to "Βιταμίνη E"), "vitamin-k" to ("Vitamin K" to "Βιταμίνη K"),
        "vitamin-c" to ("Vitamin C" to "Βιταμίνη C"), "vitamin-b1" to ("Vitamin B1" to "Βιταμίνη B1"),
        "vitamin-b2" to ("Vitamin B2" to "Βιταμίνη B2"), "vitamin-pp" to ("Niacin (B3)" to "Νιασίνη (B3)"),
        "vitamin-b6" to ("Vitamin B6" to "Βιταμίνη B6"), "vitamin-b9" to ("Folate (B9)" to "Φυλλικό οξύ (B9)"),
        "vitamin-b12" to ("Vitamin B12" to "Βιταμίνη B12"),
    )
    val minerals = linkedMapOf(
        "calcium" to ("Calcium" to "Ασβέστιο"), "iron" to ("Iron" to "Σίδηρος"), "magnesium" to ("Magnesium" to "Μαγνήσιο"),
        "potassium" to ("Potassium" to "Κάλιο"), "zinc" to ("Zinc" to "Ψευδάργυρος"), "phosphorus" to ("Phosphorus" to "Φώσφορος"),
        "iodine" to ("Iodine" to "Ιώδιο"), "selenium" to ("Selenium" to "Σελήνιο"),
    )

    fun name(id: String, lang: String): String =
        (vitamins[id] ?: minerals[id])?.let { if (lang == "el") it.second else it.first } ?: prettyTag(id)
}