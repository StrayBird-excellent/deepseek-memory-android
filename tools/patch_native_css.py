#!/usr/bin/env python3
"""
把每个 CSS 文件里的 `@media not all and (min-width: 768px)` 移动端规则，
复制一份并加 `html.native-shell` 前缀，使原生壳在平板/折叠屏/横屏（宽度>768）
下仍强制走移动布局，与 JS 的 useIsMobile 返回值保持一致。

- CSS Module (.module.css): 前缀用 `:global(html.native-shell)`
- 普通 CSS (global.css):     前缀用 `html.native-shell`

脚本幂等：已处理过的块会跳过（检测紧跟其后的 native-shell 规则）。
"""
import re
import sys
from pathlib import Path

MEDIA = re.compile(r"@media\s+not\s+all\s+and\s*\(min-width:\s*768px\)\s*\{")
MARKER = "/* native-shell mobile layout (auto-generated) */"


def find_block(text: str, start: int):
    """从 `{` 位置开始，用花括号计数找到匹配的结束位置（返回闭括号索引+1）"""
    depth = 0
    for i in range(start, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return i + 1
    raise ValueError("unbalanced braces")


def split_rules(body: str):
    """把媒体查询块内容切成 (selector, declaration) 列表，仅处理顶层规则"""
    rules = []
    i = 0
    n = len(body)
    while i < n:
        # 跳过空白与注释
        if body[i].isspace():
            i += 1
            continue
        if body.startswith("/*", i):
            end = body.find("*/", i + 2)
            i = n if end < 0 else end + 2
            continue
        # 选择器：读到第一个 {
        brace = body.find("{", i)
        if brace < 0:
            break
        selector = body[i:brace].strip()
        depth = 0
        end = brace
        for j in range(brace, n):
            if body[j] == "{":
                depth += 1
            elif body[j] == "}":
                depth -= 1
                if depth == 0:
                    end = j + 1
                    break
        declaration = body[brace:end]  # 含花括号
        if selector:
            rules.append((selector, declaration))
        i = end
    return rules


def process(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    changed = False
    out = []
    pos = 0

    while True:
        m = MEDIA.search(text, pos)
        if not m:
            out.append(text[pos:])
            break

        block_start = m.start()
        brace_open = m.end() - 1
        block_end = find_block(text, brace_open)
        block = text[block_start:block_end]

        out.append(text[pos:block_start])
        out.append(block)

        # 幂等检查：块后紧跟标记则跳过
        tail = text[block_end:block_end + len(MARKER) + 4]
        if MARKER in tail:
            pos = block_end
            continue

        body = text[brace_open + 1:block_end - 1]
        rules = split_rules(body)
        prefix = ":global(html.native-shell) " if path.name.endswith(".module.css") else "html.native-shell "

        generated = []
        for selector, declaration in rules:
            # 多选择器用逗号分隔，逐个加前缀
            sels = [s.strip() for s in selector.split(",")]
            prefixed_parts = []
            for s in sels:
                if s == ":root" or s == "html":
                    # 根元素自身带 native-shell 类，直接选中它
                    prefixed_parts.append("html.native-shell")
                elif s.startswith(":root "):
                    prefixed_parts.append("html.native-shell " + s[len(":root "):])
                else:
                    prefixed_parts.append(prefix + s)
            generated.append(f"{', '.join(prefixed_parts)} {declaration}")

        if generated:
            out.append("\n\n" + MARKER + "\n" + "\n".join(generated) + "\n")
            changed = True

        pos = block_end

    if changed:
        path.write_text("".join(out), encoding="utf-8")
    return changed


def main():
    root = Path(__file__).resolve().parent.parent / "app" / "src"
    files = [p for p in root.rglob("*.css") if MEDIA.search(p.read_text(encoding="utf-8"))]
    for p in sorted(files):
        ok = process(p)
        print(("PATCHED " if ok else "skip    ") + str(p.relative_to(root)))
    print(f"\n共 {len(files)} 个文件")


if __name__ == "__main__":
    sys.exit(main())
