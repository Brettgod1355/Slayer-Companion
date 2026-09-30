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

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.when;

import com.slayercompanion.SlayerCompanionConfig;
import com.slayercompanion.data.ItemStats;
import com.slayercompanion.data.MasterAssignmentInfo;
import com.slayercompanion.data.MonsterInfo;
import com.slayercompanion.data.RequiredGear;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.data.TaskInfo;
import com.slayercompanion.data.TaskLocation;
import com.slayercompanion.dps.Gear;
import com.slayercompanion.dps.LoadoutOptimizer;
import com.slayercompanion.dps.LoadoutRecommender;
import com.slayercompanion.dps.Recommendation;
import com.slayercompanion.gear.LoadoutDisplay;
import com.slayercompanion.gear.OwnedItems;
import com.slayercompanion.location.LocationService;
import com.slayercompanion.location.LocationServiceAccess;
import com.slayercompanion.task.CurrentTask;
import com.slayercompanion.task.SlayerMaster;
import com.slayercompanion.ui.PanelActions;
import com.slayercompanion.ui.PanelModel;
import com.slayercompanion.ui.SlayerCompanionPanel;
import com.slayercompanion.worth.LootEstimator;
import com.slayercompanion.worth.VerdictAdvisor;
import com.slayercompanion.worth.WorthAccess;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.laf.RuneLiteLAF;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Builds the Task view for every bundled task and variant the way the plugin does, renders it
 * headlessly with RuneLite's look and feel, runs "Recommend from my bank" for sample accounts and
 * lists anything odd: text Swing cuts off, content wider than the panel, empty cards, leftover wiki
 * markup, and recommendations that make no sense. Test code only; not part of the plugin jar.
 */
public final class TaskSweep
{
	static final class Case
	{
		final TaskInfo info;
		@Nullable
		final String variant;
		final SlayerMaster master;

		Case(TaskInfo info, @Nullable String variant, SlayerMaster master)
		{
			this.info = info;
			this.variant = variant;
			this.master = master;
		}

		String label()
		{
			return info.getTask() + (variant == null || variant.equalsIgnoreCase(info.getTask()) ? "" : " / " + variant);
		}
	}

	static final class Finding
	{
		final String check;
		final String where;
		final String detail;

		Finding(String check, String where, String detail)
		{
			this.check = check;
			this.where = where;
			this.detail = detail;
		}

		@Override
		public String toString()
		{
			return check + " | " + where + " | " + detail;
		}
	}

	private static final Pattern BAD_TEXT = Pattern.compile(
		"\\bnull\\b|\\bNaN\\b|Infinity|\\[\\[|]]|\\{\\{|}}|''|&amp;|&lt;|&gt;|<ref|(^|\\s)-1(\\s|$)");

	final SlayerData data;
	final SlayerCompanionConfig config;
	private final LocationService locations;
	private final LootEstimator loot;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private final Map<String, ItemStats> byName = new HashMap<>();
	private final Map<SampleBank, LoadoutRecommender> recommenders = new EnumMap<>(SampleBank.class);

	static
	{
		System.setProperty("java.awt.headless", "true");
		RuneLiteLAF.setup();
	}

	TaskSweep(SlayerData data, Map<Integer, Long> prices)
	{
		this.data = data;
		for (ItemStats s : data.itemStats().values())
		{
			byName.putIfAbsent(s.getName().toLowerCase(Locale.ROOT), s);
		}
		config = mock(SlayerCompanionConfig.class, withSettings().stubOnly());
		when(config.dpsAssumePotions()).thenReturn(true);
		when(config.dpsAssumePrayers()).thenReturn(true);
		when(config.showWildernessTab()).thenReturn(true);
		itemManager = mock(ItemManager.class, withSettings().stubOnly());
		when(itemManager.getItemPrice(anyInt())).thenAnswer(inv -> prices.getOrDefault((Integer) inv.getArgument(0), 1000L));
		when(itemManager.canonicalize(anyInt())).thenAnswer(inv -> inv.getArgument(0));
		ClientThread clientThread = mock(ClientThread.class, withSettings().stubOnly());
		when(itemManager.getImage(anyInt(), anyInt(), anyBoolean()))
			.thenAnswer(inv -> new AsyncBufferedImage(clientThread, 36, 32, BufferedImage.TYPE_INT_ARGB));
		spriteManager = mock(SpriteManager.class, withSettings().stubOnly());
		locations = LocationServiceAccess.create(mock(ConfigManager.class, withSettings().stubOnly()));
		loot = WorthAccess.lootEstimator(data, itemManager);
	}

	void close()
	{
		executor.shutdownNow();
	}

	/** Every task, once per variant the player can pick (once when there is no choice). */
	List<Case> cases()
	{
		List<Case> out = new ArrayList<>();
		for (TaskInfo info : data.tasks())
		{
			SlayerMaster master = masterFor(info);
			List<MonsterInfo> variants = info.variants();
			if (variants.isEmpty())
			{
				out.add(new Case(info, null, master));
			}
			for (MonsterInfo v : variants)
			{
				out.add(new Case(info, v.getName(), master));
			}
		}
		return out;
	}

	static SlayerMaster masterFor(TaskInfo info)
	{
		if (info.getMasters() != null)
		{
			for (String id : info.getMasters().keySet())
			{
				SlayerMaster m = SlayerMaster.fromDataId(id);
				if (m != null)
				{
					return m;
				}
			}
		}
		return SlayerMaster.DURADEL;
	}

	/** The model the plugin would build for this task, logged in with 480 points and the bank seen. */
	PanelModel model(Case c, @Nullable Recommendation rec, @Nullable LoadoutDisplay loadout)
	{
		TaskInfo info = c.info;
		int initial = 150;
		MasterAssignmentInfo a = info.getMasters() == null ? null : info.getMasters().get(c.master.getDataId());
		if (a != null && a.getMin() != null && a.getMax() != null)
		{
			initial = Math.max(1, (a.getMin() + a.getMax()) / 2);
		}
		int remaining = Math.max(1, initial * 3 / 4);
		int points = 480;
		CurrentTask task = new CurrentTask(info.getTask(), remaining, initial, null, c.master, info.isBossTask(), points, 37);
		List<TaskLocation> locs = locations.forTask(info, c.variant);
		List<String> variantNames = new ArrayList<>();
		for (MonsterInfo m : info.variants())
		{
			variantNames.add(m.getName());
		}
		return PanelModel.builder()
			.loggedIn(true)
			.task(task)
			.info(info)
			.locations(locs)
			.variants(variantNames)
			.selectedVariant(c.variant)
			.xpPerKill(info.xpPerKillFor(c.variant))
			.shortestPathAvailable(true)
			.bankKnown(true)
			.missingRequiredItems(Collections.emptyList())
			.nonItemRequirements(Collections.emptyList())
			.loadout(loadout)
			.inventorySetups(Collections.emptyList())
			.recommendation(rec)
			.recommendationIndex(0)
			.canUndoRecommendation(rec != null)
			.bankOpen(true)
			.masterRoutes(Collections.emptyList())
			.verdict(VerdictAdvisor.verdict(info, data.master(c.master).orElse(null), points))
			.lootEstimate(loot.estimate(info, c.variant, remaining).orElse(null))
			.historyExpected(Collections.emptyList())
			.completedAchievements(Collections.emptySet())
			.sharedStreak(37)
			.points(points)
			.history(Collections.emptyList())
			.unlocks(Collections.emptyList())
			.itemNames(Collections.emptyMap())
			.locks(Collections.emptyMap())
			.build();
	}

	/** What "Recommend from my bank" gives this account for the case; waits for the executor. */
	Recommendation recommend(Case c, SampleBank bank) throws Exception
	{
		LoadoutRecommender r = recommenders.computeIfAbsent(bank, this::recommender);
		CompletableFuture<Recommendation> f = new CompletableFuture<>();
		r.recommend(c.info, c.variant, f::complete);
		return f.get(120, TimeUnit.SECONDS);
	}

	private LoadoutRecommender recommender(SampleBank bank)
	{
		Client client = mock(Client.class, withSettings().stubOnly());
		when(client.getRealSkillLevel(Skill.ATTACK)).thenReturn(bank.attack);
		when(client.getRealSkillLevel(Skill.STRENGTH)).thenReturn(bank.strength);
		when(client.getRealSkillLevel(Skill.DEFENCE)).thenReturn(bank.defence);
		when(client.getRealSkillLevel(Skill.RANGED)).thenReturn(bank.ranged);
		when(client.getRealSkillLevel(Skill.MAGIC)).thenReturn(bank.magic);
		when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(bank.prayer);
		when(client.getVarbitValue(VarbitID.PRAYER_RIGOUR_UNLOCKED)).thenReturn(bank.rigour ? 1 : 0);
		when(client.getVarbitValue(VarbitID.PRAYER_AUGURY_UNLOCKED)).thenReturn(bank.augury ? 1 : 0);
		when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(bank.spellbook);
		OwnedItems owned = mock(OwnedItems.class, withSettings().stubOnly());
		when(owned.isBankKnown()).thenReturn(true);
		when(owned.bank()).thenReturn(ids(bank.items));
		when(owned.inventory()).thenReturn(Collections.emptyMap());
		when(owned.equipment()).thenReturn(ids(bank.worn));
		return new LoadoutRecommender(client, data, owned, config, executor);
	}

	private Map<Integer, Integer> ids(List<String> names)
	{
		Map<Integer, Integer> out = new LinkedHashMap<>();
		for (String n : names)
		{
			ItemStats s = byName.get(n.toLowerCase(Locale.ROOT));
			if (s == null || s.idsOrEmpty().isEmpty())
			{
				throw new IllegalArgumentException("No item stats for sample item " + n);
			}
			out.put(s.idsOrEmpty().get(0), 1);
		}
		return out;
	}

	/** The loadout the first option would save, drawn as if it were all in the bank. */
	static LoadoutDisplay loadout(Recommendation r)
	{
		Map<Integer, LoadoutDisplay.Slot> equipment = new HashMap<>();
		if (!r.getOptions().isEmpty())
		{
			LoadoutOptimizer.Option o = r.getOptions().get(0);
			Map<Integer, Integer> ids = LoadoutOptimizer.itemIds(o.getGear(), r.getOwned());
			for (Map.Entry<Integer, Integer> e : ids.entrySet())
			{
				equipment.put(e.getKey(), new LoadoutDisplay.Slot(e.getValue(), 1, false, o.getGear().get(e.getKey()).getName(),
					LoadoutDisplay.Status.IN_BANK, "In your bank"));
			}
		}
		List<LoadoutDisplay.Slot> inventory = new ArrayList<>();
		for (int i = 0; i < 28; i++)
		{
			inventory.add(i < 4 ? new LoadoutDisplay.Slot(2434, 1, false, "Prayer potion(4)", LoadoutDisplay.Status.IN_BANK, "In your bank") : null);
		}
		return new LoadoutDisplay(equipment, inventory);
	}

	/** Render the Task view as the side panel would lay it out; findings go to {@code out}. */
	BufferedImage render(PanelModel m, String where, List<Finding> out) throws Exception
	{
		return render(m, where, out, true);
	}

	/** As {@link #render(PanelModel, String, List)}; without {@code paint} only the checks run and null is returned. */
	BufferedImage render(PanelModel m, String where, List<Finding> out, boolean paint) throws Exception
	{
		BufferedImage[] image = new BufferedImage[1];
		Exception[] failure = new Exception[1];
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				image[0] = renderOnEdt(m, where, out, paint);
			}
			catch (Exception e)
			{
				failure[0] = e;
			}
		});
		if (failure[0] != null)
		{
			throw failure[0];
		}
		return image[0];
	}

	private BufferedImage renderOnEdt(PanelModel m, String where, List<Finding> out, boolean paint)
	{
		SlayerCompanionPanel panel = new SlayerCompanionPanel(mock(PanelActions.class, withSettings().stubOnly()), data.wilderness(), itemManager, spriteManager);
		panel.update(m, config);
		panel.setSize(PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH, 800);
		layout(panel);
		layout(panel);
		JScrollPane scroll = find(panel, JScrollPane.class);
		JViewport viewport = scroll.getViewport();
		Component view = viewport.getView();
		int width = viewport.getExtentSize().width;
		for (int pass = 0; pass < 3; pass++)
		{
			view.setSize(width, view.getPreferredSize().height);
			layout(view);
		}
		check(view, view, where, out);
		// The header above the tabs.
		check(panel.getComponent(0), panel.getComponent(0), where + " (header)", out);
		if (!paint)
		{
			return null;
		}

		BufferedImage img = new BufferedImage(width, Math.max(1, view.getHeight()), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		view.paint(g);
		g.dispose();
		return img;
	}

	private static void layout(Component c)
	{
		if (c instanceof Container)
		{
			Container k = (Container) c;
			k.doLayout();
			for (Component child : k.getComponents())
			{
				layout(child);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T find(Component c, Class<T> type)
	{
		if (type.isInstance(c))
		{
			return (T) c;
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				T t = find(child, type);
				if (t != null)
				{
					return t;
				}
			}
		}
		return null;
	}

	/** Walk the visible components below {@code c}. Returns true when {@code c} or a child reported an overflow. */
	private static void check(Component c, Component root, String where, List<Finding> out)
	{
		if (!c.isVisible())
		{
			return;
		}
		if (c != root)
		{
			Rectangle r = SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), root);
			if (r.x + r.width > root.getWidth() + 1)
			{
				out.add(new Finding("overflow", where, describe(c) + " reaches x=" + (r.x + r.width) + " in a " + root.getWidth() + "px panel"));
				return;
			}
		}
		if (c instanceof JLabel)
		{
			JLabel l = (JLabel) c;
			String text = plain(l.getText());
			if (l.getPreferredSize().width > l.getWidth() + 1)
			{
				out.add(new Finding("clipped", where, "\"" + text + "\" needs " + l.getPreferredSize().width + "px, has " + l.getWidth()));
			}
			if (BAD_TEXT.matcher(text).find())
			{
				out.add(new Finding("text", where, "\"" + text + "\""));
			}
		}
		else if (c instanceof AbstractButton)
		{
			AbstractButton b = (AbstractButton) c;
			if (b.getPreferredSize().width > b.getWidth() + 1)
			{
				out.add(new Finding("button-cut", where, "\"" + b.getText() + "\" needs " + b.getPreferredSize().width + "px, has " + b.getWidth()));
			}
		}
		else if (c instanceof JComboBox)
		{
			// Only the shown (selected) text matters; longer entries are cut in the list as in any Swing dropdown.
			JComboBox<?> box = (JComboBox<?>) c;
			String shown = String.valueOf(box.getSelectedItem());
			int needed = box.getFontMetrics(box.getFont()).stringWidth(shown) + box.getHeight() + 12;
			if (needed > box.getWidth() + 1)
			{
				out.add(new Finding("dropdown-cut", where, "\"" + shown + "\" needs " + needed + "px, has " + box.getWidth()));
			}
		}
		if (c.getClass().getName().endsWith("Ui$Card"))
		{
			Container card = (Container) c;
			if (card.getComponentCount() >= 2 && card.getComponent(1) instanceof Container)
			{
				Container body = (Container) card.getComponent(1);
				int content = 0;
				for (Component child : body.getComponents())
				{
					content += child instanceof javax.swing.Box.Filler ? 0 : 1;
				}
				if (content == 0)
				{
					out.add(new Finding("empty-card", where, "\"" + plain(((JLabel) card.getComponent(0)).getText()) + "\""));
				}
			}
		}
		if (c instanceof Container && !(c instanceof JComboBox))
		{
			String previous = null;
			for (Component child : ((Container) c).getComponents())
			{
				check(child, root, where, out);
				String text = child instanceof JLabel && child.isVisible() ? plain(((JLabel) child).getText()) : null;
				if (text != null && !text.isEmpty() && text.equals(previous))
				{
					out.add(new Finding("duplicate-line", where, "\"" + text + "\""));
				}
				previous = text;
			}
		}
	}

	private static String describe(Component c)
	{
		if (c instanceof JLabel)
		{
			return "label \"" + plain(((JLabel) c).getText()) + "\"";
		}
		if (c instanceof AbstractButton)
		{
			return "button \"" + ((AbstractButton) c).getText() + "\"";
		}
		return c.getClass().getSimpleName();
	}

	static String plain(@Nullable String html)
	{
		if (html == null)
		{
			return "";
		}
		return html.replaceAll("<[^>]+>", "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").trim();
	}

	/** Anything about a recommendation that should not happen. */
	static void checkRecommendation(Case c, SampleBank bank, Recommendation r, List<Finding> out)
	{
		String where = c.label() + " [" + bank + "]";
		if (r.getUnavailable() != null)
		{
			out.add(new Finding("rec-unavailable", where, r.getUnavailable()));
			return;
		}
		for (LoadoutOptimizer.Option o : r.getOptions())
		{
			String what = o.getKind() + " " + o.getStyle() + " with " + names(o.getGear());
			if (!Double.isFinite(o.getResult().getDps()) || o.getResult().getDps() <= 0)
			{
				out.add(new Finding("rec-dps", where, what + ": dps " + o.getResult().getDps()));
			}
			if (o.getResult().getHitChance() <= 0 || o.getResult().getHitChance() > 1)
			{
				out.add(new Finding("rec-accuracy", where, what + ": accuracy " + o.getResult().getHitChance()));
			}
			if (o.getResult().getMaxHit() <= 0 || o.getResult().getMaxHit() > 150)
			{
				out.add(new Finding("rec-maxhit", where, what + ": max hit " + o.getResult().getMaxHit()));
			}
			ItemStats weapon = o.getGear().get(Gear.WEAPON);
			if (weapon == null)
			{
				out.add(new Finding("rec-noweapon", where, what));
				continue;
			}
			if (weapon.isTwoHanded() && o.getGear().containsKey(Gear.SHIELD))
			{
				out.add(new Finding("rec-2h-shield", where, what));
			}
			for (Map.Entry<Integer, ItemStats> e : o.getGear().entrySet())
			{
				if (Gear.slotOf(e.getValue().getSlot()) != e.getKey())
				{
					out.add(new Finding("rec-slot", where, e.getValue().getName() + " in slot " + e.getKey()));
				}
			}
			Gear g = new Gear(o.getGear());
			if (o.getGear().containsKey(Gear.AMMO) && g.firesAmmo() && !g.ammoFits())
			{
				out.add(new Finding("rec-ammo", where, what));
			}
			String target = c.variant != null ? c.variant : r.getMonster();
			for (RequiredGear rule : c.info.requiredGearOrEmpty())
			{
				int slot = Gear.slotOf(rule.getSlot());
				if (!rule.appliesTo(target, false))
				{
					continue;
				}
				ItemStats worn = o.getGear().get(slot);
				boolean owns = bank.items.stream().anyMatch(rule::accepts);
				if (owns && (worn == null || !rule.accepts(worn.getName())))
				{
					out.add(new Finding("rec-ignores-required", where, what + ": needs " + rule.itemsOrEmpty() + " in " + rule.getSlot()));
				}
			}
			MonsterInfo monster = c.info.mainMonster(c.variant);
			if (o.getKind() == LoadoutOptimizer.Kind.MELEE && monster != null && monster.getCombatStats() != null
				&& ("none".equals(monster.getCombatStats().getMeleeReach())
				|| (("halberd".equals(monster.getCombatStats().getMeleeReach()) || monster.getCombatStats().is("flying"))
				&& !"polearm".equals(g.category()) && !"salamander".equals(g.category()))))
			{
				out.add(new Finding("rec-melee-out-of-reach", where, what));
			}
		}
	}

	static String names(Map<Integer, ItemStats> gear)
	{
		List<String> n = new ArrayList<>();
		for (ItemStats s : gear.values())
		{
			n.add(s.getName());
		}
		return String.join(", ", n);
	}

	/** Runs {@code work} for every case on a small pool; results keep the case order. */
	static <T> List<T> each(List<Case> cases, int threads, CaseWork<T> work) throws Exception
	{
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try
		{
			List<java.util.concurrent.Future<T>> futures = new ArrayList<>();
			for (Case c : cases)
			{
				futures.add(pool.submit(() -> work.run(c)));
			}
			List<T> out = new ArrayList<>();
			for (java.util.concurrent.Future<T> f : futures)
			{
				out.add(f.get());
			}
			return out;
		}
		finally
		{
			pool.shutdownNow();
		}
	}

	interface CaseWork<T>
	{
		T run(Case c) throws Exception;
	}
}
