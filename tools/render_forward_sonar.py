"""Render the forward sonar from the current Minecraft model and textures.

Run from the repository root:
    python tools/render_forward_sonar.py
"""

from render_mechanical_sonar import ASSETS, OUTPUT, render


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    image = render(
        model_path=ASSETS / "models/block/sonar.json",
        textures={
            "#2": ASSETS / "textures/block/sonar_texture_1.png",
            "#4": ASSETS / "textures/block/sonar_texture_0.png",
        },
        camera=(2.1, 1.7, -2.4),
        target_point=(0.0, 0.42, 0.0),
        key_direction=(0.55, 1.0, -0.8),
        fill_direction=(-0.75, 0.55, 0.55),
    )
    path = OUTPUT / "front_sonar_render.png"
    image.save(path)
    print(path)


if __name__ == "__main__":
    main()
