"""Render the three sonar glass grids using the mod's fragment shader.

The standalone render supplies a flat depth surface to the same shader used
in game. No glass, terrain texture, or generated illustration is added.
Run from the repository root with Pillow, NumPy and ModernGL installed:
    python tools/render_sonar_glass_effect.py
"""

from __future__ import annotations

import math
from pathlib import Path

import moderngl
import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FRAGMENT_SOURCE = (
    ROOT / "src/main/resources/assets/create_echo_radars/pinwheel/shaders/program/sonar_glass_depth.fsh"
)
OUTPUT = ROOT / "docs/images/sonar_glass_grid_effect.png"
STYLES = (
    (1, OUTPUT),
    (0, ROOT / "docs/images/sonar_glass_triangle_mesh_effect.png"),
    (2, ROOT / "docs/images/sonar_glass_isometric_cubes_effect.png"),
)
SIZE = 2048


def perspective(fov_degrees: float, near: float, far: float) -> np.ndarray:
    f = 1.0 / math.tan(math.radians(fov_degrees) / 2.0)
    return np.array(
        [
            [f, 0, 0, 0],
            [0, f, 0, 0],
            [0, 0, (far + near) / (near - far), 2 * far * near / (near - far)],
            [0, 0, -1, 0],
        ],
        dtype=np.float32,
    )


def main() -> None:
    ctx = moderngl.create_standalone_context()
    ctx.disable(moderngl.DEPTH_TEST)
    output = ctx.texture((SIZE, SIZE), 4)
    framebuffer = ctx.framebuffer(color_attachments=[output])
    framebuffer.use()
    framebuffer.clear(0, 0, 0, 0)

    projection = perspective(60.0, 0.1, 50.0)
    surface = np.array([0, 0, -16, 1], dtype=np.float32)
    clip = projection @ surface
    surface_depth = float((clip[2] / clip[3] + 1) * 0.5)
    depth = ctx.texture((1, 1), 1, np.array([surface_depth], dtype=np.float32).tobytes(), dtype="f4")
    empty_depth = ctx.texture((1, 1), 1, np.array([1.0], dtype=np.float32).tobytes(), dtype="f4")
    depth.use(location=0)
    empty_depth.use(location=1)
    empty_depth.use(location=2)

    shader = "#version 330\n" + FRAGMENT_SOURCE.read_text(encoding="utf-8")
    program = ctx.program(
        vertex_shader="""
            #version 330
            in vec2 Position;
            void main() { gl_Position = vec4(Position, 0.0, 1.0); }
        """,
        fragment_shader=shader,
    )
    quad = ctx.buffer(np.array([-1, -1, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1], dtype="f4").tobytes())
    vertex_array = ctx.vertex_array(program, [(quad, "2f", "Position")])

    for name, value in {
        "DepthSampler": 0,
        "CutoutDepthSampler": 1,
        "SceneDepthSampler": 2,
        "GridStyle": 1,  # WAVY_LINES, the current client default.
        "CurrentType": 0,
        "PreviousType": 0,
    }.items():
        program[name].value = value
    program["InverseViewProjection"].write(np.linalg.inv(projection).T.astype("f4").tobytes())
    for name, value in {
        "CameraPosition": (0, 0, 0),
        "ScreenSize": (SIZE, SIZE),
        "CurrentOrigin": (0, 0, -2),
        "CurrentForward": (0, 0, -1),
        "CurrentRight": (1, 0, 0),
        "CurrentUp": (0, 1, 0),
        "PreviousOrigin": (0, 100, 0),
        "PreviousForward": (0, 0, -1),
        "PreviousRight": (1, 0, 0),
        "PreviousUp": (0, 1, 0),
    }.items():
        program[name].value = value
    for name, value in {
        "MinRenderDistance": 8.0,
        "CycleAge": 100.0,
        "Disconnect": 1.0,
        "CurrentRange": 16.0,
        "CurrentHorizontalSector": 120.0,
        "CurrentVerticalSector": 120.0,
        "PreviousRange": 1.0,
        "PreviousHorizontalSector": 1.0,
        "PreviousVerticalSector": 1.0,
    }.items():
        program[name].value = value

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    for grid_style, path in STYLES:
        framebuffer.clear(0, 0, 0, 0)
        program["GridStyle"].value = grid_style
        vertex_array.render()
        pixels = framebuffer.read(components=4)
        image = Image.frombytes("RGBA", (SIZE, SIZE), pixels).transpose(Image.Transpose.FLIP_TOP_BOTTOM)
        image.save(path)
        print(path)

    vertex_array.release()
    quad.release()
    program.release()
    depth.release()
    empty_depth.release()
    framebuffer.release()
    output.release()
    ctx.release()


if __name__ == "__main__":
    main()
