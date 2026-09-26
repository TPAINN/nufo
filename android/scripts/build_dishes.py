"""Builds app/src/main/assets/food/dishes.json: generic nutrition for dishes the photo classifier recognises,
from USDA FNDDS survey foods (public domain, CC0).

Download the survey foods JSON from https://fdc.nal.usda.gov/download-datasets (FoodData_Central_survey_food_json_*.zip),
unzip it, then:  python scripts/build_dishes.py path/to/surveyDownload.json

Each entry is written in the shape of an FDC search result, so the app parses it with UsdaParser.parseFood, plus
"ingredients": the recipe as grams per 100 g of dish, named in English and Greek. FNDDS recipes come from its own
ingredient table; where FNDDS only has an American version of a Greek dish (a lettuce "Greek salad" without olive
oil, black-bean soup for fasolada, pork kabob with onions for souvlaki) a typical Greek home recipe in RECIPES is
used instead, and its nutrition is computed from the FNDDS nutrients of its ingredients.
Dishes FNDDS has no honest match for (moussaka, pastitsio, tiropita...) are deliberately left out: the app
falls back to searching real packaged products for them instead of showing a different recipe.
"""
import json, pathlib, sys

# Classifier dish (family) label -> exact FNDDS description.
DISHES = {
    "Gyro": "Gyro sandwich",
    "Spanakopita": "Spanakopita",
    "Dolma": "Grape leaves stuffed with rice",
    "Stuffed peppers": "Stuffed pepper, with rice and meat",
    "Baklava": "Baklava",
    "Tzatziki": "Tzatziki dip",
    "Pizza": "Pizza, cheese, from restaurant or fast food, NS as to type of crust",
    "Hamburger": "Hamburger, NFS",
    "Cheeseburger": "Cheeseburger, NFS",
    "Sushi": "Sushi, NFS",
    "Ramen": "Ramen bowl, NFS",
    "Paella": "Paella, NFS",
    "Steak": "Beef, steak, NFS",
    "French fries": "Potato, french fries, NFS",
    "Omelette": "Egg omelet or scrambled egg, NS as to fat",
    "Pancake": "Pancakes, plain",
    "Fried rice": "Rice, fried, NFS",
    "Lasagne": "Lasagna with meat",
    "Minestrone": "Soup, minestrone",
    "Couscous": "Couscous, plain, cooked",
    "Ratatouille": "Ratatouille",
    "Falafel": "Falafel",
    "Hummus": "Hummus, plain",
    "Pilaf": "Rice pilaf",
    "Cheesecake": "Cheesecake, plain",
    "French toast": "French toast, NFS",
    "Doughnut": "Doughnut, NFS",
    "Pad thai": "Pad Thai, NFS",
    "Pho": "Soup, pho, with meat",
    "Club sandwich": "Club sandwich on white",
    "Strudel": "Strudel, apple",
    "Tiramisu": "Tiramisu",
    "Caesar salad": "Caesar salad, with romaine, no dressing",
    "Eggplant parmesan": "Eggplant parmesan casserole, regular",
    "Parmigiana": "Eggplant parmesan casserole, regular",
}

# Typical Greek home recipes (grams for one serving) over FNDDS survey foods, for dishes FNDDS only has in an
# American version. Proportions follow the standard recipes: horiatiki has no lettuce and is dressed with olive oil;
# souvlaki is marinated grilled pork alone; fasolada is white beans in tomato with carrot, celery and olive oil.
RECIPES = {
    "Greek salad": ("Horiatiki, typical Greek recipe", [
        ("Tomatoes, raw", 150), ("Cucumber, raw", 80), ("Cheese, Feta", 50), ("Onions, raw", 20),
        ("Peppers, sweet, green, raw", 20), ("Olives, black", 15), ("Olive oil", 15)]),
    "Souvlaki": ("Pork souvlaki, typical Greek recipe", [
        ("Pork, tenderloin", 150), ("Olive oil", 5), ("Lemon juice, 100%, freshly squeezed", 5)]),
    "Fasolada": ("Fasolada, typical Greek recipe", [
        ("White beans, from dried, no added fat", 150), ("Tomatoes, canned, cooked", 60), ("Carrots, cooked, as ingredient", 30),
        ("Celery, cooked", 20), ("Onions, raw", 20), ("Olive oil", 20), ("Water, tap", 50)]),
}

# Ingredient names shown in the app, by the first pattern found in the FNDDS description (lowercase).
# None hides the line (water, salt, seasonings); an ingredient no pattern matches stops the build.
NAMES = [
    ("water", None), ("salt, table", None), ("spices", None), ("vanilla", None), ("leavening", None), ("soy sauce", None),
    ("vinegar", None), ("coffee", None),
    ("tomato products, canned, sauce", ("Tomato sauce", "Σάλτσα ντομάτας")), ("tomato chili sauce", ("Chili sauce", "Σάλτσα τσίλι")),
    ("tomato", ("Tomato", "Ντομάτα")), ("cucumber", ("Cucumber", "Αγγούρι")), ("feta", ("Feta", "Φέτα")),
    ("onions, spring", ("Spring onion", "Φρέσκο κρεμμύδι")), ("onion", ("Onion", "Κρεμμύδι")),
    ("peppers, sweet, green", ("Green pepper", "Πράσινη πιπεριά")), ("peppers, bell, green", ("Green pepper", "Πράσινη πιπεριά")),
    ("red pepper", ("Red pepper", "Κόκκινη πιπεριά")), ("peppers, bell, red", ("Red pepper", "Κόκκινη πιπεριά")),
    ("peppers", ("Pepper", "Πιπεριά")),
    ("olive oil", ("Olive oil", "Ελαιόλαδο")), ("olives", ("Olives", "Ελιές")),
    ("pork, tenderloin", ("Pork", "Χοιρινό")), ("pork, fresh", ("Pork", "Χοιρινό")), ("bacon", ("Bacon", "Μπέικον")),
    ("ham, sliced", ("Ham", "Ζαμπόν")), ("salami", ("Salami", "Σαλάμι")), ("turkey", ("Turkey", "Γαλοπούλα")),
    ("chicken broth", ("Chicken broth", "Ζωμός κότας")), ("chicken", ("Chicken", "Κοτόπουλο")),
    ("lamb", ("Lamb", "Αρνί")), ("beef", ("Beef", "Μοσχάρι")),
    ("lemon", ("Lemon juice", "Χυμός λεμονιού")), ("lime", ("Lime juice", "Χυμός λάιμ")),
    ("white beans", ("White beans", "Φασόλια")), ("beans, black", ("Black beans", "Μαύρα φασόλια")),
    ("beans, kidney", ("Kidney beans", "Κόκκινα φασόλια")), ("mung beans", ("Bean sprouts", "Φύτρες φασολιών")),
    ("chickpeas", ("Chickpeas", "Ρεβίθια")), ("carrot", ("Carrot", "Καρότο")), ("celery", ("Celery", "Σέλινο")),
    ("mirepoix", ("Onion, carrot and celery", "Κρεμμύδι, καρότο, σέλινο")),
    ("vegetables as ingredient", ("Mixed vegetables", "Ανάμεικτα λαχανικά")),
    ("lettuce", ("Romaine lettuce", "Μαρούλι")), ("radish", ("Radish", "Ραπανάκι")), ("anchovy", ("Anchovy", "Αντσούγια")),
    ("spinach", ("Spinach", "Σπανάκι")), ("parsley", ("Parsley", "Μαϊντανός")), ("coriander", ("Coriander", "Κόλιαντρο")),
    ("basil", ("Basil", "Βασιλικός")), ("garlic", ("Garlic", "Σκόρδο")), ("grape leaves", ("Vine leaves", "Αμπελόφυλλα")),
    ("eggplant", ("Aubergine", "Μελιτζάνα")), ("summer squash", ("Courgette", "Κολοκυθάκι")), ("mushroom", ("Mushrooms", "Μανιτάρια")),
    ("peas", ("Peas", "Αρακάς")), ("avocado", ("Avocado", "Αβοκάντο")), ("seaweed", ("Nori seaweed", "Φύκια νόρι")),
    ("applesauce", ("Apple", "Μήλο")),
    ("ricotta", ("Ricotta", "Ρικότα")), ("mozzarella", ("Mozzarella", "Μοτσαρέλα")), ("parmesan", ("Parmesan", "Παρμεζάνα")),
    ("cheddar", ("Cheddar", "Τσένταρ")), ("cream cheese", ("Cream cheese", "Τυρί κρέμα")), ("cream, heavy", ("Cream", "Κρέμα γάλακτος")),
    ("yogurt", ("Greek yogurt", "Στραγγιστό γιαούρτι")), ("tzatziki", ("Tzatziki", "Τζατζίκι")),
    ("milk, dry", ("Milk powder", "Γάλα σε σκόνη")), ("milk", ("Milk", "Γάλα")),
    ("butter", ("Butter", "Βούτυρο")), ("egg", ("Egg", "Αυγό")),
    ("oil or table fat", ("Oil", "Λάδι")), ("vegetable oil", ("Vegetable oil", "Φυτικό λάδι")),
    ("phyllo", ("Filo pastry", "Φύλλο κρούστας")), ("pita", ("Pitta bread", "Πίτα")), ("bread, white", ("White bread", "Λευκό ψωμί")),
    ("croutons", ("Croutons", "Κρουτόν")), ("flour", ("Flour", "Αλεύρι")), ("pancakes, plain, dry mix", ("Pancake mix", "Μείγμα για τηγανίτες")),
    ("graham", ("Biscuits", "Μπισκότα")), ("ladyfingers", ("Ladyfingers", "Σαβαγιάρ")), ("cocoa", ("Cocoa", "Κακάο")),
    ("sugar", ("Sugar", "Ζάχαρη")),
    ("rice noodles", ("Rice noodles", "Νουντλς ρυζιού")), ("rice and vermicelli", ("Rice and vermicelli", "Ρύζι με φιδέ")),
    ("rice", ("Rice", "Ρύζι")), ("pasta", ("Pasta", "Ζυμαρικά")), ("couscous", ("Couscous", "Κουσκούς")),
    ("almonds", ("Almonds", "Αμύγδαλα")), ("pistachio", ("Pistachios", "Φιστίκια")), ("walnuts", ("Walnuts", "Καρύδια")),
    ("crab", ("Imitation crab", "Καβουροψίχα")), ("shrimp", ("Shrimp", "Γαρίδες")), ("clam", ("Clams", "Αχιβάδες")),
    ("doughnut, cake", ("Cake doughnut", "Ντόνατ κέικ")), ("doughnut, yeast", ("Yeast doughnut", "Ντόνατ")),
]


def name_of(desc):
    low = desc.lower()
    for pattern, names in NAMES:
        if pattern in low:
            return names
    sys.exit(f"No display name for ingredient: {desc}")


def ingredients(parts):
    """[(description, grams)] -> grams per 100 g of dish, largest first; same names merged, hidden lines dropped."""
    total = sum(g for _, g in parts)
    merged = {}
    for desc, g in parts:
        names = name_of(desc)
        if names:
            merged[names] = merged.get(names, 0) + g * 100 / total
    return [{"en": en, "el": el, "g": round(g, 1)} for (en, el), g in sorted(merged.items(), key=lambda kv: -kv[1]) if g >= 0.5]


def main(src):
    foods = {f["description"]: f for f in json.load(open(src, encoding="utf-8"))["SurveyFoods"]}
    missing = [d for d in DISHES.values() if d not in foods] + [i for _, parts in RECIPES.values() for i, _ in parts if i not in foods]
    if missing:
        sys.exit("Not in FNDDS: " + "; ".join(missing))
    out = {}
    for dish, desc in DISHES.items():
        f = foods[desc]
        nutrients = [{"nutrientNumber": n["nutrient"]["number"], "value": n["amount"]}
                     for n in f.get("foodNutrients", []) if "amount" in n and n.get("nutrient", {}).get("number")]
        # "Quantity not specified" is FNDDS's typical amount eaten in one go: the right default for a photographed plate.
        portions = sorted((p for p in f.get("foodPortions", []) if p.get("gramWeight")
                           and (p.get("portionDescription", "").startswith("1 ") or p.get("portionDescription") == "Quantity not specified")),
                          key=lambda p: (p.get("portionDescription") != "Quantity not specified", p.get("sequenceNumber", 99)))
        entry = {"description": desc, "foodNutrients": nutrients, "publishedDate": f.get("publicationDate"), "fdcId": f.get("fdcId")}
        if portions:
            entry.update(servingSize=round(portions[0]["gramWeight"], 1), servingSizeUnit="g", servingDescription=portions[0]["portionDescription"])
        inputs = f.get("inputFoods", [])
        if len(inputs) > 1:  # a single input is a composite (a whole pizza), not a recipe
            entry["ingredients"] = ingredients([(i["ingredientDescription"], i["ingredientWeight"]) for i in inputs])
        out[dish] = entry
    for dish, (desc, parts) in RECIPES.items():
        total = sum(g for _, g in parts)
        per100 = {}
        for item, g in parts:
            for n in foods[item].get("foodNutrients", []):
                num = n.get("nutrient", {}).get("number")
                if num and "amount" in n:
                    per100[num] = per100.get(num, 0) + n["amount"] * g / total
        out[dish] = {
            "description": desc, "publishedDate": max(foods[i].get("publicationDate") or "" for i, _ in parts),
            "foodNutrients": [{"nutrientNumber": k, "value": round(v, 3)} for k, v in per100.items()],
            "servingSize": total, "servingSizeUnit": "g", "servingDescription": "1 serving",
            "ingredients": ingredients(parts),
        }
    dst = pathlib.Path(__file__).parent.parent / "app/src/main/assets/food/dishes.json"
    dst.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"{len(out)} dishes -> {dst} ({dst.stat().st_size // 1024} KB)")

if __name__ == "__main__":
    main(sys.argv[1])