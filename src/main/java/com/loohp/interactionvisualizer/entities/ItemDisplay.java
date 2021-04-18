package com.loohp.interactionvisualizer.entities;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.cryptomorin.xseries.XMaterial;
import com.loohp.interactionvisualizer.InteractionVisualizer;
import com.loohp.interactionvisualizer.api.InteractionVisualizerAPI;
import com.loohp.interactionvisualizer.api.InteractionVisualizerAPI.Modules;
import com.loohp.interactionvisualizer.api.VisualizerRunnableDisplay;
import com.loohp.interactionvisualizer.api.events.InteractionVisualizerReloadEvent;
import com.loohp.interactionvisualizer.managers.PlayerLocationManager;
import com.loohp.interactionvisualizer.nms.NMS;
import com.loohp.interactionvisualizer.objectholders.BoundingBox;
import com.loohp.interactionvisualizer.protocol.WatchableCollection;
import com.loohp.interactionvisualizer.utils.ChatColorUtils;
import com.loohp.interactionvisualizer.utils.ChatComponentUtils;
import com.loohp.interactionvisualizer.utils.JsonUtils;
import com.loohp.interactionvisualizer.utils.LanguageUtils;
import com.loohp.interactionvisualizer.utils.LineOfSightUtils;
import com.loohp.interactionvisualizer.utils.NBTUtils;
import com.loohp.interactionvisualizer.utils.RarityUtils;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;

public class ItemDisplay extends VisualizerRunnableDisplay implements Listener {
	
	private String[] regularFormatting;
	private String[] singularFormatting;
	private String[] toolsFormatting;
	private String highColor = "";
	private String mediumColor = "";
	private String lowColor = "";
	private int cramp = 6;
	
	public ItemDisplay() {
		onReload(new InteractionVisualizerReloadEvent());
	}
	
	@EventHandler
	public void onReload(InteractionVisualizerReloadEvent event) {
		regularFormatting = ChatColorUtils.translateAlternateColorCodes('&', InteractionVisualizer.plugin.getConfig().getString("Entities.Item.Options.RegularFormat")).split("\\{Item\\}", -1);
		singularFormatting = ChatColorUtils.translateAlternateColorCodes('&', InteractionVisualizer.plugin.getConfig().getString("Entities.Item.Options.SingularFormat")).split("\\{Item\\}", -1);
		toolsFormatting = ChatColorUtils.translateAlternateColorCodes('&', InteractionVisualizer.plugin.getConfig().getString("Entities.Item.Options.ToolsFormat")).split("\\{Item\\}", -1);
		highColor = ChatColorUtils.translateAlternateColorCodes('&', InteractionVisualizer.plugin.getConfig().getString("Entities.Item.Options.Color.High"));
		mediumColor = ChatColorUtils.translateAlternateColorCodes('&', InteractionVisualizer.plugin.getConfig().getString("Entities.Item.Options.Color.Medium"));
		lowColor = ChatColorUtils.translateAlternateColorCodes('&', InteractionVisualizer.plugin.getConfig().getString("Entities.Item.Options.Color.Low"));
		cramp = InteractionVisualizer.plugin.getConfig().getInt("Entities.Item.Options.Cramping");
	}

	@Override
	public int gc() {
		return -1;
	}

	@Override
	public int run() {
		return Bukkit.getScheduler().runTaskTimer(InteractionVisualizer.plugin, () -> {
			for (World world : Bukkit.getWorlds()) {
				Collection<Item> items = world.getEntitiesByClass(Item.class);
				Bukkit.getScheduler().runTaskAsynchronously(InteractionVisualizer.plugin, () -> {
					for (Item item : items) {
						if (item.isValid()) {
							tick(item, items);
						}
					}
				});				
			}
		}, 0, 20).getTaskId();
	}
	
	private void tick(Item item, Collection<Item> items) {
		World world = item.getWorld();
		Location location = item.getLocation();
		BoundingBox area = BoundingBox.of(item.getLocation(), 0.5, 0.5, 0.5);
		if (item.getPickupDelay() > 100 || item.getTicksLived() < 0 || cramp >= 0 && items.stream().filter(each -> each.getWorld().equals(world) && area.contains(each.getLocation().toVector())).count() > cramp) {
			PacketContainer defaultPacket = InteractionVisualizer.protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
		    defaultPacket.getIntegers().write(0, item.getEntityId());
		    defaultPacket.getWatchableCollectionModifier().write(0, WrappedDataWatcher.getEntityWatcher(item).getWatchableObjects());
		    Collection<Player> players = InteractionVisualizerAPI.getPlayerModuleList(Modules.HOLOGRAM);
		    for (Player player : players) {
	    		try {
	    			InteractionVisualizer.protocolManager.sendServerPacket(player, defaultPacket);
				} catch (InvocationTargetException e) {
					e.printStackTrace();
				}
		    }
		} else {
			ItemStack itemstack = item.getItemStack();
			if (itemstack == null) {
				itemstack = new ItemStack(Material.AIR);
			}
			
			BaseComponent name = getDisplayName(itemstack);
			int amount = itemstack.getAmount();
		    String durDisplay = null;
		    
		    if (itemstack.getType().getMaxDurability() > 0) {
				@SuppressWarnings("deprecation")
				int durability = itemstack.getType().getMaxDurability() - (InteractionVisualizer.version.isLegacy() ? itemstack.getDurability() : ((Damageable) itemstack.getItemMeta()).getDamage());
				int maxDur = itemstack.getType().getMaxDurability();
				double percentage = ((double) durability / (double) maxDur) * 100;
				String color;
				if (percentage > 66.666) {
					color = highColor;
				} else if (percentage > 33.333) {
					color = mediumColor;
				} else {
					color = lowColor;
				}
				durDisplay = color + durability + "/" + maxDur;
		    }			    			 
		    
		    int ticks = item.getTicksLived();
		    int despawnRate = NMS.getInstance().getItemDespawnRate(item);
		    int ticksLeft = despawnRate - ticks;
		    int secondsLeft = ticksLeft / 20;
		    String timerColor;
		    if (secondsLeft <= 30) {
		    	timerColor = lowColor;
		    } else if (secondsLeft <= 120) {
		    	timerColor = mediumColor;
		    } else {
		    	timerColor = highColor;
		    }
		    
		    String timer = timerColor + String.format("%02d:%02d", secondsLeft / 60, secondsLeft % 60);				    
		    
		    BaseComponent display;
		    if (ticksLeft >= 600 && durDisplay != null) {
		    	String line1 = (toolsFormatting.length > 0 ? toolsFormatting[0] : "").replace("{Amount}", amount + "").replace("{Timer}", timer).replace("{Durability}", durDisplay);
		    	display = new TextComponent(line1);
		    	for (int i = 1; i < toolsFormatting.length; i++) {
		    		String line = toolsFormatting[i].replace("{Amount}", amount + "").replace("{Timer}", timer).replace("{Durability}", durDisplay);
			    	TextComponent text = new TextComponent(line);
			    	display.addExtra(ChatComponentUtils.clone(name));
			    	display.addExtra(text);					 
		    	}
		    } else {
		    	if (amount == 1) {
			    	String line1 = (singularFormatting.length > 0 ? singularFormatting[0] : "").replace("{Amount}", amount + "").replace("{Timer}", timer);
			    	display = new TextComponent(line1);
			    	for (int i = 1; i < singularFormatting.length; i++) {
			    		String line = singularFormatting[i].replace("{Amount}", amount + "").replace("{Timer}", timer);
				    	TextComponent text = new TextComponent(line);
				    	display.addExtra(ChatComponentUtils.clone(name));
				    	display.addExtra(text);				
			    	}
		    	} else {
		    		String line1 = (regularFormatting.length > 0 ? regularFormatting[0] : "").replace("{Amount}", amount + "").replace("{Timer}", timer);
			    	display = new TextComponent(line1);
			    	for (int i = 1; i < regularFormatting.length; i++) {
			    		String line = regularFormatting[i].replace("{Amount}", amount + "").replace("{Timer}", timer);
				    	TextComponent text = new TextComponent(line);
				    	display.addExtra(ChatComponentUtils.clone(name));
				    	display.addExtra(text);				
			    	}
		    	}
		    }
		    
		    WrappedDataWatcher modifiedWatcher = WatchableCollection.getWatchableCollection(item, display);
		    WrappedDataWatcher defaultWatcher = WrappedDataWatcher.getEntityWatcher(item);
		    
		    PacketContainer modifiedPacket = InteractionVisualizer.protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
		    PacketContainer defaultPacket = InteractionVisualizer.protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
		    
		    modifiedPacket.getIntegers().write(0, item.getEntityId());
		    defaultPacket.getIntegers().write(0, item.getEntityId());
		    
		    modifiedPacket.getWatchableCollectionModifier().write(0, modifiedWatcher.getWatchableObjects());
		    defaultPacket.getWatchableCollectionModifier().write(0, defaultWatcher.getWatchableObjects());
		    
		    Location entityCenter = location.clone();
			entityCenter.setY(entityCenter.getY() + item.getHeight() * 1.7);
		    
		    Collection<Player> players = InteractionVisualizerAPI.getPlayerModuleList(Modules.HOLOGRAM);
		    Collection<Player> playersInRange = PlayerLocationManager.filterOutOfRange(players, location, player -> !InteractionVisualizer.hideIfObstructed || LineOfSightUtils.hasLineOfSight(player.getEyeLocation(), entityCenter));
		    for (Player player : players) {
	    		try {
	    			if (playersInRange.contains(player)) {
	    				InteractionVisualizer.protocolManager.sendServerPacket(player, modifiedPacket);
	    			} else {
	    				InteractionVisualizer.protocolManager.sendServerPacket(player, defaultPacket);
			    	}
				} catch (InvocationTargetException e) {
					e.printStackTrace();
				}
		    }
		}
	}
	
	private BaseComponent getDisplayName(ItemStack item) {
		BaseComponent name = null;
		
		XMaterial xMaterial = XMaterial.matchXMaterial(item);
		
		String rawDisplayName = NBTUtils.getString(item, "display", "Name");
	    if (rawDisplayName != null && JsonUtils.isValid(rawDisplayName)) {
	    	try {
	    		if (item.getEnchantments().isEmpty()) {
	    			name = ChatComponentUtils.join(ComponentSerializer.parse(rawDisplayName));								    			
	    		} else {							
	    			TextComponent coloring = new TextComponent(ChatColor.AQUA + "");
	    			coloring.setColor(ChatColor.AQUA);
	    			coloring.setExtra(Arrays.asList(ComponentSerializer.parse(rawDisplayName)));
	    			name = ChatComponentUtils.cleanUpLegacyText(coloring);
	    		}
	    	} catch (Throwable e) {
	    		name = null;
	    	}
	    }
	    
	    if (name == null) {
		    if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() && !item.getItemMeta().getDisplayName().equals("")) {
		    	if (item.getEnchantments().isEmpty()) {
		    		name = new TextComponent(ChatColorUtils.filterIllegalColorCodes(item.getItemMeta().getDisplayName()));
		    	} else {
		    		name = new TextComponent(ChatColorUtils.filterIllegalColorCodes(ChatColor.AQUA + item.getItemMeta().getDisplayName()));
		    	}
		    } else {
		    	String str = LanguageUtils.getTranslation(LanguageUtils.getTranslationKey(item), InteractionVisualizer.language);
				if (xMaterial.equals(XMaterial.PLAYER_HEAD)) {
					String owner = NBTUtils.getString(item, "SkullOwner", "Name");
					if (owner != null) {
						str = str.replaceFirst("%s", owner);
					}
				}
				if (item.getEnchantments().isEmpty()) {
		    		name = new TextComponent(ChatColorUtils.filterIllegalColorCodes(str));
		    	} else {
		    		name = new TextComponent(ChatColorUtils.filterIllegalColorCodes(ChatColor.AQUA + str));
		    	}
		    }
	    }
	    
	    name.setColor(RarityUtils.getRarityColor(item));
	    
	    return name;
	}

}
