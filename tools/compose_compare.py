"""把官方截图与我们的渲染并排拼接，便于逐项比对间距与配色。"""

from PIL import Image, ImageDraw
import pathlib

base = pathlib.Path(__file__).resolve().parent.parent / 'build'
official = Image.open(base / 'official-03.png').convert('RGB')
ours = Image.open(base / 'shots' / 'compare-ours.png').convert('RGB')

# 官方是 1080x2374，我们是 1080x2373（360x791 @3x），高度对齐到官方
target_h = official.height
w = official.width
if ours.height != target_h:
    ours = ours.resize((w, target_h), Image.LANCZOS)

gap = 24
canvas = Image.new('RGB', (w * 2 + gap * 3, target_h + 60), (24, 24, 24))
draw = ImageDraw.Draw(canvas)
canvas.paste(official, (gap, 50))
canvas.paste(ours, (gap * 2 + w, 50))
draw.text((gap + 8, 18), 'OFFICIAL', fill=(255, 255, 255))
draw.text((gap * 2 + w + 8, 18), 'OURS', fill=(255, 255, 255))

out = base / 'shots' / 'compare-side-by-side.png'
canvas.resize((canvas.width // 2, canvas.height // 2), Image.LANCZOS).save(out)
print('对比图:', out)
