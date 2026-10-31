package com.skybreaker.client;

import com.skybreaker.SkybreakerMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Orbital strike view shown during every weapon's countdown.
 *
 * The photoreal parts (lit Earth with clouds, night-side city lights, atmosphere glow, Milky Way, stars, bloom, and
 * the three satellite models) are rendered offline into PNG textures that ship with the mod, so a frame is just a
 * few texture blits plus the animated parts (beam / rod / meteors / black hole / HUD). No per-pixel work at runtime.
 */
final class OrbitalView {
	private static final String[] WARN = {"", "KINETIC BOMBARDMENT IMMINENT", "PLANETARY STRIKE IMMINENT", "METEOR BARRAGE INBOUND", "SPACETIME COLLAPSE IMMINENT"};
	private static final String[] ARM = {"", "TUNGSTEN ROD", "SATELLITE LASER", "KINETIC DEBRIS", "SINGULARITY CANNON"};
	private static final String[] ALIGN = {"", "TRAJECTORY LOCK", "BEAM ALIGNMENT", "IMPACT VECTOR", "SINGULARITY SYNC"};
	private static final int[] ACCENT = {0, 0xFFB030, 0xFF3040, 0xFF6A20, 0xC070FF};

	// ---- pre-rendered textures (see assets/skybreaker/textures/gui/orbital) ----
	private static final Identifier EARTH = SkybreakerMod.id("textures/gui/orbital/earth.png");
	private static final int EARTH_W = 1600, EARTH_H = 900;
	private static final Identifier[] SAT = {null, SkybreakerMod.id("textures/gui/orbital/sat_1.png"),
		SkybreakerMod.id("textures/gui/orbital/sat_2.png"), null, SkybreakerMod.id("textures/gui/orbital/sat_4.png")};
	private static final int SAT_TEX = 640;
	/** The sprite spans +-SAT_EXT model units; the emitter tip offset is in the same units (x right, y down). */
	private static final float SAT_EXT = 6.4f;
	private static final float[] TIP_X = {0f, -3.1201f, -1.0953f, 0f, -1.3941f};
	private static final float[] TIP_Y = {0f, 2.5140f, 0.8826f, 0f, 1.1233f};


	private static int argb(int a, int rgb) {
		return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
	}

	private static float cl(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}

	private static float sm(float a, float b, float x) {
		float t = cl((x - a) / (b - a));
		return t * t * (3f - 2f * t);
	}

	private static int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) * (1f - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1f - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1f - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static int rgbf(float r, float g, float b) {
		int ri = (int) Math.max(0, Math.min(255, r));
		int gi = (int) Math.max(0, Math.min(255, g));
		int bi = (int) Math.max(0, Math.min(255, b));
		return (ri << 16) | (gi << 8) | bi;
	}

	private static int hash(int n) {
		n = (n ^ 61) ^ (n >>> 16);
		n *= 9;
		n ^= n >>> 4;
		n *= 0x27d4eb2d;
		n ^= n >>> 15;
		return n & 0x7FFFFFFF;
	}

	// ------------------------------------------------------------------------------------------------ drawing helpers

	/** One rotated rectangle instead of hundreds of tiny squares. */
	private static void bar(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int thick, int color) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		int len = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy));
		if (len < 1) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x0, y0);
		g.pose().rotate((float) Math.atan2(dy, dx));
		g.fill(0, -thick / 2, len, thick - thick / 2, color);
		g.pose().popMatrix();
	}

	private static void glowDot(GuiGraphicsExtractor g, float x, float y, int r, int rgb, float oa, float k) {
		g.fill((int) x - r - 4, (int) y - r - 4, (int) x + r + 4, (int) y + r + 4, argb((int) (40 * oa * k), rgb));
		g.fill((int) x - r - 2, (int) y - r - 2, (int) x + r + 2, (int) y + r + 2, argb((int) (90 * oa * k), rgb));
		g.fill((int) x - r, (int) y - r, (int) x + r, (int) y + r, argb((int) (170 * oa * k), mix(rgb, 0xFFFFFF, 0.5f)));
		g.fill((int) x - r / 2, (int) y - r / 2, (int) x + r / 2 + 1, (int) y + r / 2 + 1, argb((int) (255 * oa * k), 0xFFFFFF));
	}

	private static void squareRing(GuiGraphicsExtractor g, float cx, float cy, int r, int thick, int color) {
		g.fill((int) cx - r, (int) cy - r, (int) cx + r, (int) cy - r + thick, color);
		g.fill((int) cx - r, (int) cy + r - thick, (int) cx + r, (int) cy + r, color);
		g.fill((int) cx - r, (int) cy - r, (int) cx - r + thick, (int) cy + r, color);
		g.fill((int) cx + r - thick, (int) cy - r, (int) cx + r, (int) cy + r, color);
	}

	private static void discFill(GuiGraphicsExtractor g, float cx, float cy, float r, int step, int color) {
		for (int y = (int) -r; y < (int) r; y += step) {
			float yy = y + step * 0.5f;
			int hw = (int) Math.sqrt(Math.max(0f, r * r - yy * yy));
			if (hw > 0) {
				g.fill((int) cx - hw, (int) cy + y, (int) cx + hw, (int) cy + y + step, color);
			}
		}
	}

	/** Cheap lit rock: three shaded discs instead of a per-pixel sphere. */
	private static void rock(GuiGraphicsExtractor g, float cx, float cy, float rad, float oa, float heat) {
		int step = Math.max(2, (int) (rad / 3f));
		discFill(g, cx, cy, rad, step, argb((int) (255 * oa), mix(0x4A3E36, 0xB04A18, heat * 0.5f)));
		discFill(g, cx - rad * 0.12f, cy - rad * 0.12f, rad * 0.78f, step, argb((int) (255 * oa), 0x7A6A5A));
		discFill(g, cx - rad * 0.28f, cy - rad * 0.3f, rad * 0.38f, step, argb((int) (255 * oa), 0xB4A48E));
	}

	/** Accretion disk drawn as tilted rows (one pose rotation, a few dozen fills). */
	private static void disk(GuiGraphicsExtractor g, float cx, float cy, float r, float oa, int age, boolean front) {
		float ro = 3.6f * r;
		float ri = 1.35f * r;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate(-0.22f);
		int step = Math.max(2, (int) (r / 9f));
		for (int y = (int) (-0.28f * ro); y < (int) (0.28f * ro); y += step) {
			boolean isFront = y >= 0;
			if (isFront != front) {
				continue;
			}
			float yy = y + step * 0.5f;
			float fo = yy / (0.28f * ro);
			float xo = ro * (float) Math.sqrt(Math.max(0f, 1f - fo * fo));
			float fi = yy / (0.28f * ri);
			float xi = Math.abs(fi) < 1f ? ri * (float) Math.sqrt(1f - fi * fi) : 0f;
			if (xo <= xi + 1f) {
				continue;
			}
			float swirl = 0.7f + 0.3f * (float) Math.sin(y * 0.4f + age * 0.3f);
			float span = xo - xi;
			for (int side = -1; side <= 1; side += 2) {
				float dop = side < 0 ? 1f : 0.7f;
				int a1 = (int) (235 * swirl * dop * oa);
				int a2 = (int) (190 * swirl * dop * oa);
				int a3 = (int) (110 * swirl * dop * oa);
				float p0 = xi, p1 = xi + span * 0.3f, p2 = xi + span * 0.65f, p3 = xo;
				g.fill((int) (side * p0 - (side < 0 ? (p1 - p0) : 0)), y, (int) (side * p0 + (side > 0 ? (p1 - p0) : 0)), y + step, argb(a1, 0xFFF0C8));
				g.fill((int) (side * p1 - (side < 0 ? (p2 - p1) : 0)), y, (int) (side * p1 + (side > 0 ? (p2 - p1) : 0)), y + step, argb(a2, 0xFF9030));
				g.fill((int) (side * p2 - (side < 0 ? (p3 - p2) : 0)), y, (int) (side * p2 + (side > 0 ? (p3 - p2) : 0)), y + step, argb(a3, 0x8A2070));
			}
		}
		g.pose().popMatrix();
	}

	private static void blackHole(GuiGraphicsExtractor g, float cx, float cy, float r, float oa, int age) {
		if (r < 2f) {
			return;
		}
		disk(g, cx, cy, r, oa, age, false);
		discFill(g, cx, cy, r, 2, argb((int) (255 * oa), 0x000000));
		// photon ring + lensed arc over the top
		float pr = 1.12f * r;
		for (int y = (int) -pr; y < (int) pr; y += 2) {
			float yy = y + 1f;
			int hw = (int) Math.sqrt(Math.max(0f, pr * pr - yy * yy));
			int a = (int) (230 * oa);
			g.fill((int) cx - hw - 1, (int) cy + y, (int) cx - hw + 2, (int) cy + y + 2, argb(a, 0xFFB060));
			g.fill((int) cx + hw - 2, (int) cy + y, (int) cx + hw + 1, (int) cy + y + 2, argb(a, 0xFFB060));
		}
		float lr = 1.45f * r;
		for (int y = (int) -lr; y < (int) (-0.35f * r); y += 2) {
			float yy = y + 1f;
			int hw = (int) Math.sqrt(Math.max(0f, lr * lr - yy * yy));
			int a = (int) (130 * oa);
			g.fill((int) cx - hw - 1, (int) cy + y, (int) cx - hw + 2, (int) cy + y + 2, argb(a, 0xFF9A40));
			g.fill((int) cx + hw - 2, (int) cy + y, (int) cx + hw + 1, (int) cy + y + 2, argb(a, 0xFF9A40));
		}
		g.fill((int) (cx - lr * 0.55f), (int) (cy - lr) - 1, (int) (cx + lr * 0.55f), (int) (cy - lr) + 2, argb((int) (150 * oa), 0xFFB060));
		disk(g, cx, cy, r, oa, age, true);
	}

	private static void textL(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		var f = Minecraft.getInstance().font;
		g.centeredText(f, s, x + f.width(s) / 2, y, color);
	}

	private static void textR(GuiGraphicsExtractor g, String s, int xRight, int y, int color) {
		var f = Minecraft.getInstance().font;
		g.centeredText(f, s, xRight - f.width(s) / 2, y, color);
	}

	private static void textScaledL(GuiGraphicsExtractor g, String s, int x, int y, float scale, int color) {
		var f = Minecraft.getInstance().font;
		g.pose().pushMatrix();
		g.pose().translate(x + f.width(s) * scale / 2f, y);
		g.pose().scale(scale);
		g.centeredText(f, s, 0, 0, color);
		g.pose().popMatrix();
	}

	private static void bracket(GuiGraphicsExtractor g, int x, int y, int len, int dx, int dy, int color) {
		g.fill(Math.min(x, x + dx * len), Math.min(y, y + dy * 2), Math.max(x, x + dx * len), Math.max(y, y + dy * 2), color);
		g.fill(Math.min(x, x + dx * 2), Math.min(y, y + dy * len), Math.max(x, x + dx * 2), Math.max(y, y + dy * len), color);
	}

	// ------------------------------------------------------------------------------------------------ main
	/** Draws a whole texture (optionally faded) scaled onto the given rectangle. */
	private static void tex(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, int texW, int texH, float alpha) {
		int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
		if (a <= 0) {
			return;
		}
		g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, texW, texH, texW, texH, (a << 24) | 0xFFFFFF);
	}

	// ------------------------------------------------------------------------------------------------ main

	static void render(GuiGraphicsExtractor g, int w, int h, int weapon, int age, int total, int tgx, int tgy, int tgz, int distance) {
		weapon = Math.max(1, Math.min(4, weapon));
		var font = Minecraft.getInstance().font;
		float prog = cl(age / (float) total);
		float oa = Math.min(cl(age / 8f), cl((total - age) / 10f));
		if (oa <= 0f) {
			return;
		}
		int remaining = Math.max(0, total - age);
		int accent = ACCENT[weapon];

		// layout (fractions of the screen; must match the baked textures)
		float tX = w * 0.47f;
		float tY = h * 0.70f;
		int sx = (int) (w * 0.84f);
		int sy0 = (int) (h * 0.17f);
		float s = h * 0.038f;
		float pulse = 0.75f + 0.25f * (float) Math.sin(age * 1.3);

		// ---- everything in the "world" layer slowly pushes in toward the target (target stays fixed) ----
		float ease = prog * prog * (3f - 2f * prog);
		float zoom = 1f + 0.08f * ease;
		g.pose().pushMatrix();
		g.pose().translate(tX, tY);
		g.pose().scale(zoom);
		g.pose().translate(-tX, -tY);

		// Earth, atmosphere, stars, milky way, orbit tracks: one blit
		tex(g, EARTH, 0, 0, w, h, EARTH_W, EARTH_H, oa);

		// a few animated star twinkles over the dark upper-right
		for (int i = 0; i < 10; i++) {
			int stx = (int) (w * 0.5f) + hash(i * 5) % Math.max(1, (int) (w * 0.5f));
			int sty = hash(i * 5 + 1) % Math.max(1, (int) (h * 0.4f));
			float tw = 0.5f + 0.5f * (float) Math.sin(age * 0.25 + i * 1.9);
			int a = (int) (200 * tw * oa);
			g.fill(stx - 2, sty, stx + 3, sty + 1, argb(a / 2, 0xCFE4FF));
			g.fill(stx, sty - 2, stx + 1, sty + 3, argb(a / 2, 0xCFE4FF));
			g.fill(stx, sty, stx + 1, sty + 1, argb(a, 0xFFFFFF));
		}

		// ---- tactical grid over the target area ----
		float gw = h * 0.20f, gh = h * 0.14f;
		int cells = 6;
		int gridA = (int) ((46 + 30 * Math.sin(age * 0.2)) * oa);
		for (int c = 0; c <= cells; c++) {
			int gx = (int) (tX - gw + c * (2f * gw / cells));
			g.fill(gx, (int) (tY - gh), gx + 1, (int) (tY + gh), argb(gridA, 0xA0F0FF));
		}
		for (int c = 0; c <= 4; c++) {
			int gy = (int) (tY - gh + c * (2f * gh / 4));
			g.fill((int) (tX - gw), gy, (int) (tX + gw), gy + 1, argb(gridA, 0xA0F0FF));
		}
		int sweep = (int) (tX - gw + ((age * 3) % 100) / 100f * 2f * gw);
		g.fill(sweep, (int) (tY - gh), sweep + 2, (int) (tY + gh), argb((int) (70 * oa), 0xC8FFFF));
		squareRing(g, tX, tY, (int) (h * 0.12f + 3 * pulse), 1, argb((int) (110 * oa), 0x7FE8FF));

		// ---- weapon scene ----
		if (weapon == 3) {
			meteors(g, w, h, tX, tY, prog, oa);
		} else {
			int sy = sy0 + (int) Math.round(Math.sin(age * 0.1) * 1.5);
			int size = Math.round(2f * SAT_EXT * s);
			tex(g, SAT[weapon], Math.round(sx - size / 2f), Math.round(sy - size / 2f), size, size, SAT_TEX, SAT_TEX, oa);
			float ex = sx + TIP_X[weapon] * s;
			float ey = sy + TIP_Y[weapon] * s;

			if (weapon == 2 || weapon == 4) {
				float beamP = cl((prog - 0.18f) / 0.30f);
				float hbX = tX + w * 0.015f, hbY = tY - h * 0.16f;
				float destX = weapon == 4 ? hbX : tX;
				float destY = weapon == 4 ? hbY : tY;
				int bc = weapon == 4 ? 0xB060FF : 0xFF3040;
				int bc2 = weapon == 4 ? 0xD8A8FF : 0xFF6070;
				if (beamP > 0f) {
					float bx = ex + (destX - ex) * beamP;
					float by = ey + (destY - ey) * beamP;
					bar(g, ex, ey, bx, by, 9, argb((int) (40 * oa * pulse), bc));
					bar(g, ex, ey, bx, by, 5, argb((int) (110 * oa * pulse), bc));
					bar(g, ex, ey, bx, by, 3, argb((int) (230 * oa), bc2));
					bar(g, ex, ey, bx, by, 1, argb((int) (255 * oa), 0xFFF0F0));
				}
				glowDot(g, ex, ey, 3 + (int) (5 * prog), bc, oa, pulse);
				if (weapon == 2 && beamP >= 1f) {
					glowDot(g, tX, tY, (int) (5 + 5 * pulse + 6 * (prog - 0.48f)), 0xFF3040, oa, 1f);
				}
				if (weapon == 4) {
					float rr = h * 0.085f * (1f - (float) Math.pow(1f - cl(prog / 0.75f), 3.0));
					blackHole(g, hbX, hbY, rr, oa, age);
				}
			} else {
				// orbital railgun: charge, fire a tungsten rod, impact
				float charge = cl(prog / 0.45f);
				glowDot(g, ex, ey, 2 + (int) (6 * charge), 0xFFB030, oa, pulse);
				int arcs = (int) (charge * 4);
				for (int i = 0; i < arcs; i++) {
					float an = hash(age * 13 + i) % 628 / 100f;
					float l = 6 + hash(age * 7 + i) % 14;
					bar(g, ex, ey, ex + (float) Math.cos(an) * l, ey + (float) Math.sin(an) * l, 1, argb((int) (220 * oa), 0xFFE9A0));
				}
				float e = cl((prog - 0.45f) / 0.45f);
				if (prog >= 0.45f && e < 1f) {
					float ee = e * e;
					float px = ex + (tX - ex) * ee;
					float py = ey + (tY - ey) * ee;
					float dl = Math.max(1f, (float) Math.hypot(tX - ex, tY - ey));
					float dx = (tX - ex) / dl;
					float dy = (tY - ey) / dl;
					float trail = 24f + 90f * e;
					bar(g, px - dx * trail, py - dy * trail, px, py, 7, argb((int) (70 * oa), 0xFF7A20));
					bar(g, px - dx * trail * 0.6f, py - dy * trail * 0.6f, px, py, 4, argb((int) (130 * oa), 0xFF9A30));
					bar(g, px - dx * 14f, py - dy * 14f, px, py, 3, argb((int) (240 * oa), 0xFFD890));
					bar(g, px - dx * 14f, py - dy * 14f, px, py, 1, argb((int) (255 * oa), 0xFFFFFF));
					glowDot(g, px, py, 3, 0xFFB030, oa, 1f);
					if (e < 0.08f) {
						glowDot(g, ex, ey, 14, 0xFFE0A0, oa, 1f - e / 0.08f);
					}
				} else if (e >= 1f) {
					float k = cl((prog - 0.9f) / 0.1f);
					glowDot(g, tX, tY, (int) (6 + 10 * k), 0xFFB030, oa, 1f - 0.4f * k);
					squareRing(g, tX, tY, (int) (6 + 40 * k), 2, argb((int) (220 * (1f - k) * oa), 0xFFD890));
				}
			}
		}

		g.pose().popMatrix();

		// ---- reticle ----
		int rs = (int) (46 * (1f - prog) + 9);
		int rc = argb((int) (230 * oa), prog > 0.8f ? 0xFF4050 : 0x7FE8FF);
		bracket(g, (int) tX - rs, (int) tY - rs, 7, 1, 1, rc);
		bracket(g, (int) tX + rs, (int) tY - rs, 7, -1, 1, rc);
		bracket(g, (int) tX - rs, (int) tY + rs, 7, 1, -1, rc);
		bracket(g, (int) tX + rs, (int) tY + rs, 7, -1, -1, rc);
		textL(g, "TGT", (int) tX + rs + 6, (int) tY - rs - 2, argb((int) (220 * oa), 0xFFFFFF));

		// ---- side HUD arcs (dashed segments) ----
		int arcTop = (int) (h * 0.12f), arcBot = (int) (h * 0.88f);
		int arcCol = argb((int) (200 * oa), 0x40D8F0);
		for (int y = arcTop; y < arcBot; y += 8) {
			if ((y / 8) % 6 == 5) {
				continue;
			}
			float u = (y - h / 2f) / (h * 0.38f);
			int ax = (int) (w * 0.045f + u * u * w * 0.035f);
			g.fill(ax, y, ax + 2, y + 8, arcCol);
			g.fill(w - ax - 2, y, w - ax, y + 8, arcCol);
		}
		for (int k = 0; k < 4; k++) {
			int yy = (int) (h * 0.30f) + k * 7;
			g.fill((int) (w * 0.06f), yy, (int) (w * 0.06f) + 10, yy + 4, argb((int) ((120 + 100 * ((age / 4 + k) % 2)) * oa), 0x50E8FF));
			g.fill(w - (int) (w * 0.06f) - 10, yy, w - (int) (w * 0.06f), yy + 4, argb((int) ((120 + 100 * ((age / 4 + k + 1) % 2)) * oa), 0x50E8FF));
		}

		// ---- readouts ----
		int cyan = argb((int) (240 * oa), 0xBFF2FF);
		int white = argb((int) (240 * oa), 0xFFFFFF);
		String align = String.format(Locale.ROOT, "%.1f%%", 99.7f * (float) Math.pow(prog, 0.7));
		String status = prog < 0.35f ? "ACQUIRING" : (prog < 0.8f ? "TRACKING" : "LOCK-ON");
		int lx = (int) (w * 0.08f);
		int ly = h - 44;
		textL(g, "SYSTEM: SKYBREAKER ORBITAL PLATFORM", lx, ly, cyan);
		textL(g, "POWER: " + (int) (60 + 38 * prog) + "%", lx, ly + 11, cyan);
		textL(g, "ARMAMENT: " + ARM[weapon] + " - " + (prog > 0.9f ? "READY" : "CHARGING"), lx, ly + 22, cyan);
		int rx = (int) (w * 0.93f);
		textR(g, ALIGN[weapon] + ": " + align, rx, (int) (h * 0.30f), cyan);
		g.fill(rx - 120, (int) (h * 0.30f) + 11, rx, (int) (h * 0.30f) + 12, argb((int) (160 * oa), accent));
		textR(g, "IMPACT: T-MINUS " + String.format(Locale.ROOT, "%.1f", remaining / 20f) + " SECONDS", rx, h - 22, white);

		// ---- warning panel ----
		int pw = (int) Math.max(150, w * 0.40f);
		int ph = 66;
		int px = (int) (w * 0.58f);
		if (px + pw > w - 6) {
			px = w - 6 - pw;
		}
		int py = (int) (h * 0.50f);
		boolean blink = (age / 5) % 2 == 0 || prog < 0.12f;
		int red = argb((int) (255 * oa), blink ? mix(accent, 0xFFFFFF, 0.15f) : mix(accent, 0x000000, 0.25f));
		g.fill(px, py, px + pw, py + ph, argb((int) (170 * oa), 0x101820));
		g.fill(px - 2, py - 2, px + 14, py - 1, red);
		g.fill(px - 2, py - 2, px - 1, py + 14, red);
		g.fill(px + pw - 14, py + ph + 1, px + pw + 2, py + ph + 2, red);
		g.fill(px + pw + 1, py + ph - 14, px + pw + 2, py + ph + 2, red);
		textScaledL(g, "WARNING:", px + 8, py + 6, 1.2f, red);
		String big = WARN[weapon];
		float bigScale = Math.min(1.5f, (pw - 16) / (float) Math.max(1, font.width(big)));
		textScaledL(g, big, px + 8, py + 18, bigScale, red);
		int ul = py + 18 + (int) (9 * bigScale) + 2;
		g.fill(px + 8, ul, px + pw - 8, ul + 1, argb((int) (255 * oa), 0xFF9A40));
		textL(g, "TARGET: " + tgx + " " + tgy + " " + tgz + "  RNG " + distance + "m", px + 8, ul + 6, white);
		textL(g, "STATUS: " + status, px + 8, ul + 17, prog >= 0.8f ? red : cyan);

		// ---- glitch at the cut-in ----
		if (age < 12 && age % 2 == 1) {
			int gy = hash(age * 11) % Math.max(1, h - 10);
			g.fill(0, gy, w, gy + 3 + hash(age) % 6, argb((int) (60 * oa), 0x7FE8FF));
		}
	}

	private static void meteors(GuiGraphicsExtractor g, int w, int h, float tX, float tY, float prog, float oa) {
		for (int i = 0; i < 6; i++) {
			float t0 = 0.04f + i * 0.095f;
			float q = (prog - t0) / 0.55f;
			if (q <= 0f) {
				continue;
			}
			float ex = tX + (hash(i * 9) % 90 - 45);
			float ey = tY + (hash(i * 9 + 1) % 50 - 25);
			float sx = w * (0.98f + 0.02f * i);
			float sy = -h * 0.1f - i * 8f;
			float rad0 = 3f + (hash(i * 9 + 2) % 4);
			if (q >= 1f) {
				float k = cl((q - 1f) / 0.25f);
				if (k < 1f) {
					glowDot(g, ex, ey, (int) (4 + 8 * k), 0xFF6A20, oa, 1f - k);
					squareRing(g, ex, ey, (int) (4 + 26 * k), 2, argb((int) (200 * (1f - k) * oa), 0xFFB070));
				}
				continue;
			}
			float px = sx + (ex - sx) * q;
			float py = sy + (ey - sy) * q;
			float len = (float) Math.max(1.0, Math.hypot(ex - sx, ey - sy));
			float dx = (ex - sx) / len;
			float dy = (ey - sy) / len;
			float rad = rad0 * (0.5f + 1.4f * q);
			float trail = 40f + 140f * q;
			bar(g, px - dx * trail, py - dy * trail, px, py, Math.max(3, (int) (rad * 1.6f)), argb((int) (60 * oa), 0xFF4A18));
			bar(g, px - dx * trail * 0.65f, py - dy * trail * 0.65f, px, py, Math.max(2, (int) (rad * 1.1f)), argb((int) (110 * oa), 0xFF7A20));
			bar(g, px - dx * trail * 0.3f, py - dy * trail * 0.3f, px, py, Math.max(1, (int) (rad * 0.6f)), argb((int) (190 * oa), 0xFFC070));
			glowDot(g, px, py, (int) (rad * 1.3f), 0xFF7A20, oa, 0.7f);
			rock(g, px, py, rad, oa, 0.9f);
		}
	}

	private OrbitalView() {
	}
}
