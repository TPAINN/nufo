"""Builds app/src/main/assets/food/dishes.json: generic nutrition for dishes the photo classifier recognises,
from USDA FNDDS survey foods (public domain, CC0).

Download the survey foods JSON from https://fdc.nal.usda.gov/download-datasets (FoodData_Central_survey_food_json_*.zip),
unzip it, then:  python scripts/build_dishes.py path/to/surveyDownload.json

Each entry is written in the shape of an FDC search result, so the app parses it with UsdaParser.parseFood.
Dishes FNDDS has no honest match for (moussaka, pastitsio, tiropita...) are deliberately left out: the app
falls back to searching real packaged products for them instead of showing a different recipe.
"""
import json, pathlib, sys

# Classifier dish (family) label -> exact FNDDS description.
DISHES = {
    "Greek salad": "Greek Salad, no dressing",
    "Gyro": "Gyro sandwich",
    "Souvlaki": "Pork shish kabob with vegetables, excluding potatoes",
    "Spanakopita": "Spanakopita",
    "Dolma": "Grape leaves stuffed with rice",
    "Stuffed peppers": "Stuffed pepper, with rice and meat",
    "Fasolada": "Soup, bean",
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

def main(src):
    foods = {f["description"]: f for f in json.load(open(src, encoding="utf-8"))["SurveyFoods"]}
    missing = [d for d in DISHES.values() if d not in foods]
    if missing:
        sys.exit("Not in FNDDS: " + "; ".join(missing))
    out = {}
    for dish, desc in DISHES.items():
        f = foods[desc]
        nutrients = [{"nutrientNumber": n["nutrient"]["number"], "value": n["amount"]}
                     for n in f.get("foodNutrients", []) if "amount" in n and n.get("nutrient", {}).get("number")]
        portions = sorted((p for p in f.get("foodPortions", []) if p.get("gramWeight") and p.get("portionDescription", "").startswith("1 ")),
                          key=lambda p: p.get("sequenceNumber", 99))
        entry = {"description": desc, "foodNutrients": nutrients, "publishedDate": f.get("publicationDate"), "fdcId": f.get("fdcId")}
        if portions:
            entry.update(servingSize=round(portions[0]["gramWeight"], 1), servingSizeUnit="g", servingDescription=portions[0]["portionDescription"])
        out[dish] = entry
    dst = pathlib.Path(__file__).parent.parent / "app/src/main/assets/food/dishes.json"
    dst.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"{len(out)} dishes -> {dst} ({dst.stat().st_size // 1024} KB)")

if __name__ == "__main__":
    main(sys.argv[1])