"""Render the mechanical sonar item directly from its Minecraft model JSON.

Requires Pillow, NumPy and ModernGL. Run from the repository root:
    python tools/render_mechanical_sonar.py
"""

from __future__ import annotations

import json
import math
from pathlib import Path

import moderngl
import numpy as np
from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/create_echo_radars"
MODEL = ASSETS / "models/block/mechanical_scanning_sonar_item.json"
ATLAS = ASSETS / "textures/block/mechanical_scanning_sonar_atlas.png"
OUTPUT = ROOT / "docs/images"
SIZE = 2048
SUPERSAMPLE = 2

VERTEX_SHADER = """
#version 330
in vec3 in_position;
in vec3 in_normal;
in vec2 in_uv;
uniform mat4 mvp;
uniform vec3 camera_position;
out vec3 normal;
out vec3 world_position;
out vec2 uv;
void main() {
    normal = in_normal;
    world_position = in_position;
    uv = in_uv;
    gl_Position = mvp * vec4(in_position, 1.0);
}
"""

FRAGMENT_SHADER = """
#version 330
uniform sampler2D atlas;
uniform vec3 camera_position;
uniform vec3 key_direction;
uniform vec3 fill_direction;
in vec3 normal;
in vec3 world_position;
in vec2 uv;
out vec4 color;
void main() {
    vec4 texel = texture(atlas, uv);
    if (texel.a < 0.5) discard;
    vec3 n = normalize(normal);
    vec3 key = normalize(key_direction);
    vec3 fill = normalize(fill_direction);
    float diffuse = max(dot(n, key), 0.0);
    float soft_fill = max(dot(n, fill), 0.0);
    float light = 0.52 + 0.39 * diffuse + 0.17 * soft_fill;
    vec3 view_direction = normalize(camera_position - world_position);
    vec3 reflected = reflect(-key, n);
    float specular = pow(max(dot(view_direction, reflected), 0.0), 36.0) * 0.08;
    color = vec4(clamp(texel.rgb * light + vec3(specular), 0.0, 1.0), texel.a);
}
"""


def normalized(vector: np.ndarray) -> np.ndarray:
    return vector / np.linalg.norm(vector)


def look_at(eye: np.ndarray, target: np.ndarray) -> np.ndarray:
    forward = normalized(target - eye)
    right = normalized(np.cross(forward, np.array([0.0, 1.0, 0.0])))
    up = np.cross(right, forward)
    matrix = np.eye(4, dtype=np.float32)
    matrix[0, :3] = right
    matrix[1, :3] = up
    matrix[2, :3] = -forward
    matrix[:3, 3] = -matrix[:3, :3] @ eye
    return matrix


def rotated(point: np.ndarray, element: dict) -> np.ndarray:
    spec = element.get("rotation")
    if not spec or not spec.get("angle"):
        return point
    angle = math.radians(spec["angle"])
    c, s = math.cos(angle), math.sin(angle)
    axis = spec["axis"]
    rotation = {
        "x": np.array([[1, 0, 0], [0, c, -s], [0, s, c]]),
        "y": np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
        "z": np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]]),
    }[axis]
    origin = np.array(spec["origin"], dtype=np.float64)
    return origin + rotation @ (point - origin)


def face_corners(lower: np.ndarray, upper: np.ndarray, direction: str) -> list[np.ndarray]:
    x0, y0, z0 = lower
    x1, y1, z1 = upper
    # Top-left, top-right, bottom-right, bottom-left from outside the block.
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z0), (x1, y1, z1), (x1, y0, z1), (x1, y0, z0)],
        "west": [(x0, y1, z1), (x0, y1, z0), (x0, y0, z0), (x0, y0, z1)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[direction]


def geometry(model: dict, texture_key: str) -> np.ndarray:
    vertices: list[list[float]] = []
    for element in model["elements"]:
        lower = np.minimum(element["from"], element["to"])
        upper = np.maximum(element["from"], element["to"])
        for direction, face in element["faces"].items():
            if face.get("texture") != texture_key:
                continue
            points = np.array([rotated(np.array(p), element) for p in face_corners(lower, upper, direction)])
            points = (points - [8, 0, 8]) / 16.0
            normal = normalized(np.cross(points[2] - points[0], points[1] - points[0]))
            u0, v0, u1, v1 = face["uv"]
            uvs = np.array([(u0, v0), (u1, v0), (u1, v1), (u0, v1)], dtype=np.float64)
            uvs /= 16.0
            turns = (face.get("rotation", 0) // 90) % 4
            uvs = np.roll(uvs, turns, axis=0)
            for index in (0, 1, 2, 0, 2, 3):
                vertices.append([*points[index], *normal, *uvs[index]])
    return np.array(vertices, dtype=np.float32)


def render(
    model_path: Path = MODEL,
    textures: dict[str, Path] | None = None,
    camera: tuple[float, float, float] = (2.1, 1.7, 2.4),
    target_point: tuple[float, float, float] = (0.0, 0.43, 0.0),
    key_direction: tuple[float, float, float] = (-0.55, 1.0, 0.8),
    fill_direction: tuple[float, float, float] = (0.75, 0.55, -0.55),
) -> Image.Image:
    model = json.loads(model_path.read_text(encoding="utf-8"))
    if textures is None:
        textures = {"#2": ATLAS}
    used_textures = {face["texture"] for element in model["elements"] for face in element["faces"].values()}
    if used_textures != textures.keys():
        raise ValueError(f"Texture mismatch: model={used_textures}, provided={set(textures)}")
    ctx = moderngl.create_standalone_context()
    resolution = SIZE * SUPERSAMPLE
    color_texture = ctx.texture((resolution, resolution), 4)
    depth = ctx.depth_renderbuffer((resolution, resolution))
    framebuffer = ctx.framebuffer(color_attachments=[color_texture], depth_attachment=depth)
    framebuffer.use()
    framebuffer.clear(0.0, 0.0, 0.0, 0.0, depth=1.0)
    ctx.enable(moderngl.DEPTH_TEST)

    program = ctx.program(vertex_shader=VERTEX_SHADER, fragment_shader=FRAGMENT_SHADER)
    eye = np.array(camera, dtype=np.float32)
    target = np.array(target_point, dtype=np.float32)
    view = look_at(eye, target)
    scale = 1.05
    projection = np.array([
        [1 / scale, 0, 0, 0],
        [0, 1 / scale, 0, 0],
        [0, 0, -0.25, 0],
        [0, 0, 0, 1],
    ], dtype=np.float32)
    program["mvp"].write((projection @ view).T.astype("f4").tobytes())
    program["camera_position"].value = tuple(eye)
    program["key_direction"].value = key_direction
    program["fill_direction"].value = fill_direction
    program["atlas"].value = 0
    for texture_key, path in textures.items():
        texture_image = Image.open(path).convert("RGBA")
        atlas = ctx.texture(texture_image.size, 4, texture_image.tobytes())
        atlas.filter = (moderngl.NEAREST, moderngl.NEAREST)
        atlas.repeat_x = False
        atlas.repeat_y = False
        atlas.use(location=0)
        buffer = ctx.buffer(geometry(model, texture_key).tobytes())
        vertex_array = ctx.vertex_array(program, [(buffer, "3f 3f 2f", "in_position", "in_normal", "in_uv")])
        vertex_array.render()
        vertex_array.release()
        buffer.release()
        atlas.release()

    result = Image.frombytes("RGBA", (resolution, resolution), framebuffer.read(components=4))
    result = result.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
    result = result.resize((SIZE, SIZE), Image.Resampling.LANCZOS)
    framebuffer.release()
    color_texture.release()
    depth.release()
    ctx.release()
    return result


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    rendered = render()
    rendered.save(OUTPUT / "mechanical_scanning_sonar_render.png")

    # A second, ready-to-share preview keeps the source render untouched.
    backdrop = Image.new("RGBA", rendered.size, (28, 35, 43, 255))
    shadow = Image.new("RGBA", rendered.size)
    draw = ImageDraw.Draw(shadow)
    draw.ellipse((390, 1480, 1658, 1770), fill=(0, 0, 0, 115))
    shadow = shadow.filter(ImageFilter.GaussianBlur(65))
    backdrop = Image.alpha_composite(backdrop, shadow)
    backdrop = Image.alpha_composite(backdrop, rendered)
    backdrop.convert("RGB").save(OUTPUT / "mechanical_scanning_sonar_preview.png", quality=95)
    print(OUTPUT / "mechanical_scanning_sonar_render.png")
    print(OUTPUT / "mechanical_scanning_sonar_preview.png")


if __name__ == "__main__":
    main()
