"""
生成应用图标。

设计：品牌蓝圆角方块 + 一条白色的遗忘曲线。
曲线形状取自产品真正的机制 —— R(t) = exp(-t/S)，
起笔处一个实心圆点表示记忆形成，向右逐渐趋平并淡出。

使用 4 倍超采样后降采样，得到平滑边缘，不依赖任何第三方图形库。
"""

from __future__ import annotations

import os
from PIL import Image, ImageDraw

# 品牌蓝，与界面里的 --dsw-static-blue-500 一致
BRAND = (57, 100, 254, 255)
WHITE = (255, 255, 255, 255)

SS = 4                     # 超采样倍数
CANVAS = 1024              # 逻辑画布尺寸
STROKE = 62                # 曲线宽度（逻辑像素）
START_R = 54               # 起点球头半径（需大于 STROKE/2 才会露出）

# 遗忘曲线的两段三次贝塞尔，坐标基于 1024 画布。
# 形态：左上起笔后急剧下坠，随后进入长尾、缓慢趋平 —— 正是 R(t)=exp(-t/S) 的形状。
SEGMENTS = [
    ((248, 274), (260, 484), (310, 614), (450, 664)),
    ((450, 664), (620, 708), (740, 718), (822, 726)),
]


def cubic(p0, p1, p2, p3, steps=600):
    """采样三次贝塞尔曲线；采样点足够密，供后续用圆点堆叠成粗线"""
    pts = []
    for i in range(steps + 1):
        t = i / steps
        mt = 1 - t
        x = mt**3 * p0[0] + 3 * mt**2 * t * p1[0] + 3 * mt * t**2 * p2[0] + t**3 * p3[0]
        y = mt**3 * p0[1] + 3 * mt**2 * t * p1[1] + 3 * mt * t**2 * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


def curve_points(scale: float, offset: tuple[float, float] = (0.0, 0.0)):
    """整条曲线的采样点，带缩放与平移"""
    ox, oy = offset
    pts = []
    for seg in SEGMENTS:
        for x, y in cubic(*seg):
            pts.append(((x * scale) + ox, (y * scale) + oy))
    return pts


def draw_curve(draw: ImageDraw.ImageDraw, scale: float, offset, stroke: float, color=WHITE):
    """
    用密集圆点堆叠出粗曲线。
    不用 draw.line 的 width：它在弯折处会留下逐段矩形的接缝，
    放大后表现为一条条横向细缝。圆点堆叠没有接缝，且自带圆头。
    """
    pts = curve_points(scale, offset)
    r = max(1.0, stroke / 2)
    step = max(1.0, r * 0.45)  # 相邻圆心间距小于半径，保证完全覆盖
    last = None
    for point in pts:
        if last is None or (abs(point[0] - last[0]) + abs(point[1] - last[1])) >= step:
            draw.ellipse(
                [point[0] - r, point[1] - r, point[0] + r, point[1] + r], fill=color
            )
            last = point


def render_full(size: int, rounded: bool = True, circular: bool = False) -> Image.Image:
    """完整图标：蓝底 + 白曲线"""
    w = size * SS
    img = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    if circular:
        draw.ellipse([0, 0, w - 1, w - 1], fill=BRAND)
    elif rounded:
        draw.rounded_rectangle([0, 0, w - 1, w - 1], radius=int(size * 0.22 * SS), fill=BRAND)
    else:
        draw.rectangle([0, 0, w - 1, w - 1], fill=BRAND)

    scale = w / CANVAS
    draw_curve(draw, scale, (0, 0), STROKE * scale)
    # 起点实心圆：强调「记忆在这里形成」
    cx, cy, r = 248 * scale, 274 * scale, START_R * scale
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=WHITE)

    return img.resize((size, size), Image.LANCZOS)


def render_foreground(size: int) -> Image.Image:
    """
    Adaptive icon 前景：透明底，内容缩进到中心 66.7% 的安全区。
    系统会用 108dp 的画布裁切，超出安全区的部分可能被切掉。
    """
    w = size * SS
    img = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    safe = 0.66
    scale = (w / CANVAS) * safe
    offset = ((w - CANVAS * scale) / 2, (w - CANVAS * scale) / 2)
    draw_curve(draw, scale, offset, STROKE * scale)
    cx = offset[0] + 248 * scale
    cy = offset[1] + 274 * scale
    r = START_R * scale
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=WHITE)
    return img.resize((size, size), Image.LANCZOS)


# 密度 -> (launcher, foreground)
DENSITIES = {
    "mdpi": (48, 108),
    "hdpi": (72, 162),
    "xhdpi": (96, 216),
    "xxhdpi": (144, 324),
    "xxxhdpi": (192, 432),
}


def main():
    res = os.path.join(os.path.dirname(__file__), "..", "app", "android", "app", "src", "main", "res")
    res = os.path.abspath(res)
    out_dir = os.path.join(os.path.dirname(__file__), "..", "build", "icons")
    os.makedirs(out_dir, exist_ok=True)

    # 各密度都从同一张 1024 主图缩放：渲染一次即可，也保证视觉完全一致
    master = render_full(1024)
    master_round = render_full(1024, circular=True)
    master_fg = render_foreground(1024)
    master.save(os.path.join(out_dir, "icon-1024.png"))

    for density, (launcher, foreground) in DENSITIES.items():
        folder = os.path.join(res, f"mipmap-{density}")
        if not os.path.isdir(folder):
            continue
        master.resize((launcher, launcher), Image.LANCZOS).save(
            os.path.join(folder, "ic_launcher.png")
        )
        master_round.resize((launcher, launcher), Image.LANCZOS).save(
            os.path.join(folder, "ic_launcher_round.png")
        )
        master_fg.resize((foreground, foreground), Image.LANCZOS).save(
            os.path.join(folder, "ic_launcher_foreground.png")
        )
        print(f"{density}: launcher {launcher}px, foreground {foreground}px")

    print("预览图:", os.path.join(out_dir, "icon-1024.png"))


if __name__ == "__main__":
    main()
