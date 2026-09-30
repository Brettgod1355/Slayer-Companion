/*
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.slayercompanion.sweep;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.dps.LoadoutOptimizer;
import com.slayercompanion.dps.Recommendation;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Runs the task sweep and writes a report, one PNG per case and contact sheets.
 * Usage: TaskSweepReport outDir [prices.json from prices.runescape.wiki/api/v1/osrs/latest]
 */
public final class TaskSweepReport
{
	public static void main(String[] args) throws Exception
	{
		File outDir = new File(args[0]);
		File cases = new File(outDir, "cases");
		cases.mkdirs();
		Map<Integer, Long> prices = args.length > 1 ? prices(new File(args[1])) : Collections.emptyMap();
		SlayerData data = new SlayerData(new Gson());
		TaskSweep sweep = new TaskSweep(data, prices);
		List<TaskSweep.Case> all = sweep.cases();
		List<TaskSweep.Finding> findings = Collections.synchronizedList(new ArrayList<>());
		Map<String, String> dpsRows = Collections.synchronizedMap(new LinkedHashMap<>());
		List<BufferedImage> images = new ArrayList<>();
		long start = System.currentTimeMillis();
		long slowest = 0;
		String slowestCase = "";
		for (TaskSweep.Case c : all)
		{
			Map<SampleBank, Recommendation> recs = new LinkedHashMap<>();
			for (SampleBank bank : SampleBank.values())
			{
				long t0 = System.currentTimeMillis();
				Recommendation r;
				try
				{
					r = sweep.recommend(c, bank);
				}
				catch (Exception e)
				{
					findings.add(new TaskSweep.Finding("exception", c.label() + " [" + bank + "]", "recommend: " + e));
					continue;
				}
				long took = System.currentTimeMillis() - t0;
				if (took > slowest)
				{
					slowest = took;
					slowestCase = c.label() + " [" + bank + "]";
				}
				recs.put(bank, r);
				TaskSweep.checkRecommendation(c, bank, r, findings);
			}
			dpsRows.put(c.label(), dpsRow(recs));
			Recommendation maxed = recs.get(SampleBank.MAXED);
			try
			{
				BufferedImage img = sweep.render(sweep.model(c, maxed, maxed == null ? null : TaskSweep.loadout(maxed)), c.label(), findings);
				ImageIO.write(img, "png", new File(cases, file(c.label()) + ".png"));
				images.add(label(img, c.label()));
			}
			catch (Exception e)
			{
				findings.add(new TaskSweep.Finding("exception", c.label(), "render: " + e));
				e.printStackTrace();
			}
			// Also without any loadout or recommendation, as the player first sees it.
			try
			{
				sweep.render(sweep.model(c, null, null), c.label() + " (no loadout)", findings);
			}
			catch (Exception e)
			{
				findings.add(new TaskSweep.Finding("exception", c.label() + " (no loadout)", "render: " + e));
			}
		}
		sweep.close();
		long took = System.currentTimeMillis() - start;
		writeSheets(images, outDir);
		writeReport(new File(outDir, "report.md"), all.size(), findings, dpsRows, took, slowest, slowestCase);
		System.out.println("cases " + all.size() + ", findings " + findings.size() + ", " + took + " ms");
	}

	private static String dpsRow(Map<SampleBank, Recommendation> recs)
	{
		List<String> cells = new ArrayList<>();
		for (SampleBank bank : SampleBank.values())
		{
			Recommendation r = recs.get(bank);
			if (r == null)
			{
				cells.add("error");
			}
			else if (r.getUnavailable() != null)
			{
				cells.add("— " + r.getUnavailable());
			}
			else
			{
				LoadoutOptimizer.Option o = r.getOptions().get(0);
				cells.add(String.format(Locale.ROOT, "%.2f %s (%s; max %d, %d%%)", o.getResult().getDps(), o.getKind(),
					o.getGear().get(com.slayercompanion.dps.Gear.WEAPON).getName(), o.getResult().getMaxHit(),
					Math.round(o.getResult().getHitChance() * 100)));
			}
		}
		return String.join(" | ", cells);
	}

	private static Map<Integer, Long> prices(File f) throws Exception
	{
		JsonObject root = new Gson().fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), JsonObject.class);
		Map<Integer, Long> out = new HashMap<>();
		for (Map.Entry<String, com.google.gson.JsonElement> e : root.getAsJsonObject("data").entrySet())
		{
			JsonObject p = e.getValue().getAsJsonObject();
			long high = p.has("high") && !p.get("high").isJsonNull() ? p.get("high").getAsLong() : 0;
			long low = p.has("low") && !p.get("low").isJsonNull() ? p.get("low").getAsLong() : 0;
			long price = high > 0 && low > 0 ? (high + low) / 2 : Math.max(high, low);
			out.put(Integer.parseInt(e.getKey()), price);
		}
		return out;
	}

	private static String file(String label)
	{
		return label.replaceAll("[^A-Za-z0-9]+", "_");
	}

	private static BufferedImage label(BufferedImage img, String text)
	{
		BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight() + 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, out.getWidth(), 16);
		g.setColor(Color.YELLOW);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.drawString(text, 3, 12);
		g.drawImage(img, 0, 16, null);
		g.dispose();
		return out;
	}

	/** Eight cases side by side per sheet at half size, cut at 1,600 px (full size) tall. */
	private static void writeSheets(List<BufferedImage> images, File outDir) throws Exception
	{
		int perSheet = 8;
		int maxH = 1600;
		for (int s = 0; s * perSheet < images.size(); s++)
		{
			List<BufferedImage> part = images.subList(s * perSheet, Math.min(images.size(), (s + 1) * perSheet));
			int w = 0;
			int h = 0;
			for (BufferedImage i : part)
			{
				w += i.getWidth() + 6;
				h = Math.max(h, Math.min(maxH, i.getHeight()));
			}
			BufferedImage sheet = new BufferedImage(w / 2, h / 2, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = sheet.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setColor(new Color(20, 20, 20));
			g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
			int x = 0;
			for (BufferedImage i : part)
			{
				int ih = Math.min(maxH, i.getHeight());
				g.drawImage(i, x / 2, 0, (x + i.getWidth()) / 2, ih / 2, 0, 0, i.getWidth(), ih, null);
				x += i.getWidth() + 6;
			}
			g.dispose();
			ImageIO.write(sheet, "png", new File(outDir, String.format(Locale.ROOT, "sheet-%02d.png", s + 1)));
		}
	}

	private static void writeReport(File f, int cases, List<TaskSweep.Finding> findings, Map<String, String> dps, long took,
		long slowest, String slowestCase) throws Exception
	{
		Map<String, List<TaskSweep.Finding>> byCheck = new LinkedHashMap<>();
		for (TaskSweep.Finding x : findings)
		{
			byCheck.computeIfAbsent(x.check, k -> new ArrayList<>()).add(x);
		}
		try (PrintWriter w = new PrintWriter(f, "UTF-8"))
		{
			w.println("# Task sweep");
			w.println();
			w.println(cases + " cases (every task, once per variant), 3 sample accounts, " + took / 1000 + " s. Slowest recommendation: "
				+ slowest + " ms (" + slowestCase + ").");
			w.println();
			for (Map.Entry<String, List<TaskSweep.Finding>> e : byCheck.entrySet())
			{
				w.println("## " + e.getKey() + " (" + e.getValue().size() + ")");
				for (TaskSweep.Finding x : e.getValue())
				{
					w.println("- " + x.where + ": " + x.detail);
				}
				w.println();
			}
			w.println("## Best option per account");
			w.println();
			w.println("| Case | Low | Mid | Maxed |");
			w.println("|---|---|---|---|");
			for (Map.Entry<String, String> e : dps.entrySet())
			{
				w.println("| " + e.getKey() + " | " + e.getValue() + " |");
			}
		}
	}
}
