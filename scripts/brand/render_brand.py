#!/usr/bin/env python3
"""Renders the Play Store icon (512x512) and feature graphic (1024x500) from the same geometry as
app/src/main/res/drawable/ic_launcher_{background,foreground}.xml. Keep the two in sync.

Usage: python3 scripts/brand/render_brand.py [out_dir]   (default: store/)
Needs pycairo.
"""
import math
import os
import sys

import cairo

OUT = sys.argv[1] if len(sys.argv) > 1 else "store"


def background(ctx, w, h):
    lin = cairo.LinearGradient(0, 0, w, h)
    lin.add_color_stop_rgb(0.0, 0x17 / 255, 0x15 / 255, 0x3F / 255)
    lin.add_color_stop_rgb(0.55, 0x4A / 255, 0x4F / 255, 0xE0 / 255)
    lin.add_color_stop_rgb(1.0, 0x9D / 255, 0x4E / 255, 0xDD / 255)
    ctx.rectangle(0, 0, w, h)
    ctx.set_source(lin)
    ctx.fill()
    s = max(w, h) / 108.0
    rad = cairo.RadialGradient(30 * s, 24 * s, 0, 30 * s, 24 * s, 62 * s)
    rad.add_color_stop_rgba(0, 1, 1, 1, 0x2E / 255)
    rad.add_color_stop_rgba(1, 1, 1, 1, 0)
    ctx.rectangle(0, 0, w, h)
    ctx.set_source(rad)
    ctx.fill()


def monogram(ctx):
    """Foreground in the 108-unit icon space (same paths as the vector drawable)."""
    # Viewfinder corners.
    ctx.set_source_rgba(1, 1, 1, 0xB3 / 255)
    ctx.set_line_width(2.4)
    ctx.set_line_cap(cairo.LINE_CAP_ROUND)
    ctx.set_line_join(cairo.LINE_JOIN_ROUND)
    for pts in (((32, 40), (32, 32), (40, 32)), ((32, 68), (32, 76), (40, 76)), ((68, 76), (76, 76), (76, 68))):
        ctx.move_to(*pts[0])
        for p in pts[1:]:
            ctx.line_to(*p)
        ctx.stroke()
    # AI sparkle (quadratic curves converted to cubic).
    def quad(p0, c, p1):
        ctx.curve_to(p0[0] + 2 / 3 * (c[0] - p0[0]), p0[1] + 2 / 3 * (c[1] - p0[1]),
                     p1[0] + 2 / 3 * (c[0] - p1[0]), p1[1] + 2 / 3 * (c[1] - p1[1]), p1[0], p1[1])
    ctx.move_to(73, 29.5)
    quad((73, 29.5), (74, 34), (78.5, 35))
    quad((78.5, 35), (74, 36), (73, 40.5))
    quad((73, 40.5), (72, 36), (67.5, 35))
    quad((67.5, 35), (72, 34), (73, 29.5))
    ctx.close_path()
    gold = cairo.LinearGradient(67.5, 29.5, 78.5, 40.5)
    gold.add_color_stop_rgb(0, 1, 0xF1 / 255, 0xC4 / 255)
    gold.add_color_stop_rgb(1, 1, 0xB9 / 255, 0x38 / 255)
    ctx.set_source(gold)
    ctx.fill()
    # "Fr", scaled 0.78 about the centre.
    ctx.save()
    ctx.translate(54, 54)
    ctx.scale(0.78, 0.78)
    ctx.translate(-54, -54)
    ctx.set_source_rgb(1, 1, 1)
    for poly in ([(35, 34), (73, 34), (73, 41.5), (42.5, 41.5), (42.5, 74), (35, 74)],
                 [(42.5, 50), (55, 50), (55, 57), (42.5, 57)]):
        ctx.move_to(*poly[0])
        for p in poly[1:]:
            ctx.line_to(*p)
        ctx.close_path()
        ctx.fill()
    # r: stem + shoulder (outer arc r=10 centred (68.5,58); inner arc r=2.5 centred (68.5,58)).
    ctx.move_to(58.5, 74)
    ctx.line_to(58.5, 58)
    ctx.arc(68.5, 58, 10, math.pi, 1.5 * math.pi)
    ctx.line_to(73, 48)
    ctx.line_to(73, 55.5)
    ctx.line_to(68.5, 55.5)
    ctx.arc_negative(68.5, 58, 2.5, 1.5 * math.pi, math.pi)
    ctx.line_to(66, 74)
    ctx.close_path()
    ctx.fill()
    ctx.restore()


def icon(path, size=512):
    surf = cairo.ImageSurface(cairo.FORMAT_ARGB32, size, size)
    ctx = cairo.Context(surf)
    # Play shows the full square (it applies its own mask), so show the launcher's visible area:
    # the central 72 of the 108-unit canvas.
    background(ctx, size, size)
    ctx.scale(size / 72.0, size / 72.0)
    ctx.translate(-18, -18)
    monogram(ctx)
    surf.write_to_png(path)


def feature_graphic(path, w=1024, h=500):
    surf = cairo.ImageSurface(cairo.FORMAT_ARGB32, w, h)
    ctx = cairo.Context(surf)
    background(ctx, w, h)
    # Rounded icon tile on the left.
    tile = 300
    x0, y0 = 90, (h - tile) / 2
    ctx.save()
    r = tile * 0.22
    ctx.new_sub_path()
    ctx.arc(x0 + tile - r, y0 + r, r, -math.pi / 2, 0)
    ctx.arc(x0 + tile - r, y0 + tile - r, r, 0, math.pi / 2)
    ctx.arc(x0 + r, y0 + tile - r, r, math.pi / 2, math.pi)
    ctx.arc(x0 + r, y0 + r, r, math.pi, 1.5 * math.pi)
    ctx.close_path()
    ctx.clip()
    ctx.translate(x0, y0)
    background(ctx, tile, tile)
    ctx.scale(tile / 72.0, tile / 72.0)
    ctx.translate(-18, -18)
    monogram(ctx)
    ctx.restore()
    # Wordmark and positioning line.
    ctx.set_source_rgb(1, 1, 1)
    ctx.select_font_face("Sans", cairo.FONT_SLANT_NORMAL, cairo.FONT_WEIGHT_BOLD)
    ctx.set_font_size(112)
    ctx.move_to(450, 245)
    ctx.show_text("Frame")
    ctx.select_font_face("Sans", cairo.FONT_SLANT_NORMAL, cairo.FONT_WEIGHT_NORMAL)
    ctx.set_font_size(38)
    ctx.set_source_rgba(1, 1, 1, 0.92)
    ctx.move_to(455, 310)
    ctx.show_text("AI Video & Audio Editor")
    tagline = "Music  ·  Captions  ·  Beat sync  ·  No watermark"
    size = 26
    ctx.set_font_size(size)
    while ctx.text_extents(tagline).x_advance > w - 455 - 40 and size > 14:
        size -= 1
        ctx.set_font_size(size)
    ctx.set_source_rgba(1, 0.86, 0.55, 1)
    ctx.move_to(455, 360)
    ctx.show_text(tagline)
    surf.write_to_png(path)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    icon(os.path.join(OUT, "play-icon-512.png"))
    icon(os.path.join(OUT, "launcher-preview-192.png"), 192)
    feature_graphic(os.path.join(OUT, "feature-graphic-1024x500.png"))
    print("wrote", OUT)
