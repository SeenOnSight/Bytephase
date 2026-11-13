"""Create bundled static faces: uv run --with fonttools tools/generate_fonts.py."""

from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont


ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "tools/font-sources"
OUTPUT = ROOT / "src/main/resources/assets/bytephase/font"

for family, weight in (("inter", 500), ("outfit", 500), ("jetbrains_mono", 400)):
    for suffix, face_weight in (("", weight), ("_semibold", 600)):
        source = TTFont(SOURCES / f"{family}.ttf")
        axes = {axis.axisTag: axis.defaultValue for axis in source["fvar"].axes}
        axes["wght"] = face_weight
        face = instantiateVariableFont(source, axes, inplace=True)
        family_name = face["name"].getDebugName(16) or face["name"].getDebugName(1)
        style_name = "Semibold" if suffix else "Medium" if face_weight == 500 else "Regular"
        full_name = f"{family_name} {style_name}"
        postscript_name = f"{family_name.replace(' ', '')}-{style_name}"
        names = {1: family_name, 2: style_name, 3: postscript_name, 4: full_name,
                 6: postscript_name, 16: family_name, 17: style_name}
        for record in face["name"].names:
            if record.nameID in names:
                record.string = names[record.nameID]
        destination = OUTPUT / f"{family}{suffix}.ttf"
        face.save(destination)
        print(f"{destination.name}: weight {face_weight}")
