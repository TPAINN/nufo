"""Scores a FoodRecognitionBenchmark run: python scripts/score_food.py <logcat.txt> [--show]
Top-1: the first suggestion names the dish. Top-3: it is among the first three suggestions."""
import re, sys

# Suggestions that count as naming each dish (lowercase). Generic labels like "Food" never count.
ACCEPT = {
    "moussaka": {"moussaka", "μουσακάς"}, "souvlaki": {"souvlaki", "σουβλάκι", "kebab", "shish kebab"},
    "gyro": {"gyro", "gyros", "γύρος", "doner kebab", "shawarma", "pita gyros"},
    "greek_salad": {"greek salad", "χωριάτικη σαλάτα", "χωριάτικη"}, "spanakopita": {"spanakopita", "σπανακόπιτα"},
    "pastitsio": {"pastitsio", "παστίτσιο"}, "dolma": {"dolma", "dolmades", "ντολμάδες", "γεμιστά", "stuffed peppers", "sarma"},
    "fasolada": {"fasolada", "φασολάδα", "bean soup"}, "baklava": {"baklava", "μπακλαβάς"},
    "galaktoboureko": {"galaktoboureko", "γαλακτομπούρεκο"}, "tiropita": {"tiropita", "τυρόπιτα", "börek", "borek"},
    "tzatziki": {"tzatziki", "τζατζίκι", "cacık"}, "pizza": {"pizza", "πίτσα", "pizza margherita", "margherita", "neapolitan pizza", "chicago-style pizza", "new york-style pizza"},
    "hamburger": {"hamburger", "χάμπουργκερ", "cheeseburger", "burger"}, "sushi": {"sushi", "σούσι", "maki", "nigiri", "makizushi"},
    "spaghetti_carbonara": {"spaghetti carbonara", "carbonara", "καρμπονάρα", "spaghetti"},
    "caesar_salad": {"caesar salad", "σαλάτα του καίσαρα", "σαλάτα caesar"}, "pancake": {"pancake", "pancakes", "τηγανίτα", "τηγανίτες"},
    "omelette": {"omelette", "omelet", "ομελέτα", "tortilla de patatas"}, "fried_rice": {"fried rice", "τηγανητό ρύζι", "nasi goreng"},
    "steak": {"steak", "μπριζόλα", "beefsteak", "sirloin steak", "rib eye steak", "rib-eye", "t-bone steak"},
    "french_fries": {"french fries", "πατάτες τηγανητές", "chips", "fries"}, "tiramisu": {"tiramisu", "τιραμισού"},
    "paella": {"paella", "παέγια"}, "ramen": {"ramen", "ράμεν", "noodle soup"},
}

def main(path, show=False):
    rows = []
    for line in open(path, encoding="utf-8", errors="replace"):
        m = re.search(r"NUFO_BENCH\s*:\s*(\S+\.jpg)\|(\d+)\|([^|]*)\|([^|]*)\|(.*)$", line)
        if not m: continue
        file, ms, dishes, generic, text = m.groups()
        labels = dishes if dishes else generic
        dish = file.rsplit("__", 1)[0]
        guesses = [g.rsplit(":", 1)[0].strip().lower() for g in labels.split(";") if g]
        confs = [float(g.rsplit(":", 1)[1].replace(",", ".")) for g in labels.split(";") if g]
        ok = ACCEPT[dish]
        top1 = bool(guesses) and guesses[0] in ok
        top3 = any(g in ok for g in guesses[:3])
        rows.append((file, int(ms), guesses, top1, top3))
        shown = ", ".join("%s %.2f" % (g, c) for g, c in zip(guesses[:4], confs[:4]))
        if show: print(f"{'✓' if top1 else ('~' if top3 else '✗')} {file:32} {int(ms):5}ms  {shown or '(nothing)'}")
    n = len(rows)
    t1 = sum(r[3] for r in rows); t3 = sum(r[4] for r in rows); none = sum(not r[2] for r in rows)
    ms = sorted(r[1] for r in rows)
    print(f"photos {n}  top1 {t1}/{n} = {100*t1/n:.1f}%  top3 {t3}/{n} = {100*t3/n:.1f}%  no suggestion {none}  median {ms[n//2]} ms")

if __name__ == "__main__":
    main(sys.argv[1], "--show" in sys.argv)