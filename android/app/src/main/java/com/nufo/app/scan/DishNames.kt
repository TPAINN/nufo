package com.nufo.app.scan

/** Greek names for the classifier's dishes that people in Greece are most likely to photograph. */
object DishNames {
    private val EL = mapOf(
        // Greek cuisine
        "Moussaka" to "Μουσακάς", "Souvlaki" to "Σουβλάκι", "Gyro" to "Γύρος", "Greek salad" to "Χωριάτικη σαλάτα",
        "Spanakopita" to "Σπανακόπιτα", "Tiropita" to "Τυρόπιτα", "Pastitsio" to "Παστίτσιο", "Dolma" to "Ντολμάδες",
        "Stuffed peppers" to "Γεμιστά", "Fasolada" to "Φασολάδα", "Baklava" to "Μπακλαβάς", "Galaktoboureko" to "Γαλακτομπούρεκο",
        "Tzatziki" to "Τζατζίκι", "Meze" to "Μεζέδες", "Giouvetsi" to "Γιουβέτσι", "Avgolemono" to "Αυγολέμονο",
        "Bougatsa" to "Μπουγάτσα", "Melomakarono" to "Μελομακάρονα", "Koulourakia" to "Κουλουράκια", "Kokoretsi" to "Κοκορέτσι",
        "Spanakorizo" to "Σπανακόρυζο", "Tirokafteri" to "Τυροκαυτερή", "Tarama" to "Ταραμοσαλάτα", "Loukaniko" to "Λουκάνικο",
        "Kofta" to "Κεφτέδες", "Börek" to "Μπουρέκι", "Halva" to "Χαλβάς", "Kanafeh" to "Κανταΐφι", "Pilaf" to "Πιλάφι",
        // Everyday international dishes
        "Pizza" to "Πίτσα", "Hamburger" to "Χάμπουργκερ", "Cheeseburger" to "Τσίζμπεργκερ", "Sushi" to "Σούσι",
        "Carbonara" to "Καρμπονάρα", "Spaghetti" to "Σπαγγέτι", "Lasagne" to "Λαζάνια", "Pancake" to "Τηγανίτες",
        "Omelette" to "Ομελέτα", "Fried rice" to "Τηγανητό ρύζι", "Steak" to "Μπριζόλα", "French fries" to "Πατάτες τηγανητές",
        "Paella" to "Παέγια", "Ramen" to "Ράμεν", "Roast beef" to "Ροσμπίφ", "Minestrone" to "Μινεστρόνε", "Risotto" to "Ριζότο",
        "Apple pie" to "Μηλόπιτα", "Cheesecake" to "Τσιζκέικ", "French toast" to "Αυγόφετες", "Waffle" to "Βάφλα",
        "Doughnut" to "Ντόνατ", "Hot dog" to "Χοτ ντογκ", "Club sandwich" to "Κλαμπ σάντουιτς", "Sandwich" to "Σάντουιτς",
        "Burrito" to "Μπουρίτο", "Taco" to "Τάκο", "Shawarma" to "Σαουάρμα", "Hummus" to "Χούμους", "Falafel" to "Φαλάφελ",
        "Potato salad" to "Πατατοσαλάτα", "Pasta salad" to "Μακαρονοσαλάτα", "Mashed potato" to "Πουρές πατάτας",
        "Baked potato" to "Ψητή πατάτα", "Fried chicken" to "Τηγανητό κοτόπουλο", "Parmigiana" to "Παρμιτζιάνα",
        "Couscous" to "Κουσκούς", "Ratatouille" to "Ρατατούι", "Strudel" to "Στρούντελ",
    )

    fun display(label: String, lang: String): String = if (lang == "el") EL[label] ?: label else label
}