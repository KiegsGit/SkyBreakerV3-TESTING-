package com.skybreaker.client;

import com.skybreaker.StrikeItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Status readout shown above the hotbar while a Skybreaker remote is in hand, like the reference video:
 * model code + name, whether a target is in range, and the controls. Pure fills + text, a few draw calls.
 */
final class RemoteHud {
	private static final String[] CODE = {"OA-1", "OA-2", "OA-3", "OA-4"};
	private static final int[] ACCENT = {0xFFFFB030, 0xFFFF3040, 0xFFFF6A20, 0xFFC070FF};

	static void render(GuiGraphicsExtractor g, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || CinematicHud.isActive()) {
			return;
		}
		StrikeItem item = held(mc);
		if (item == null) {
			return;
		}
		int k = item.kind.ordinal();
		var font = mc.font;

		String name = CODE[k] + " " + item.kind.display;
		double d = FxManager.aimDistance;
		boolean locked = d >= 0.0;
		String status = locked ? "TARGET " + (int) d + " m" : "NO TARGET";
		String hint = "Right-click: fire";
		String sep = "  |  ";

		int wName = font.width(name);
		int wStat = font.width(status);
		int wHint = font.width(hint);
		int wSep = font.width(sep);
		int total = wName + wSep + wStat + wSep + wHint;

		int w = g.guiWidth();
		int h = g.guiHeight();
		int left = w / 2 - total / 2;
		int y = h - 62;
		g.fill(left - 6, y - 3, left + total + 6, y + 11, 0x99000000);
		g.fill(left - 6, y - 3, left - 4, y + 11, ACCENT[k]);

		int x = left;
		g.centeredText(font, name, x + wName / 2, y, ACCENT[k]);
		x += wName;
		g.centeredText(font, sep, x + wSep / 2, y, 0xFF707070);
		x += wSep;
		g.centeredText(font, status, x + wStat / 2, y, locked ? 0xFF55FF77 : 0xFFFF6060);
		x += wStat;
		g.centeredText(font, sep, x + wSep / 2, y, 0xFF707070);
		x += wSep;
		g.centeredText(font, hint, x + wHint / 2, y, 0xFFB8B8B8);
	}

	private static StrikeItem held(Minecraft mc) {
		ItemStack main = mc.player.getMainHandItem();
		if (main.getItem() instanceof StrikeItem s) {
			return s;
		}
		ItemStack off = mc.player.getOffhandItem();
		if (off.getItem() instanceof StrikeItem s) {
			return s;
		}
		return null;
	}

	private RemoteHud() {
	}
}
