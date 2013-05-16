package net.dandielo.citizens.traders_v3.traders;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.citizensnpcs.api.npc.NPC;
import net.dandielo.citizens.traders_v3.tNpc;
import net.dandielo.citizens.traders_v3.core.Debugger;
import net.dandielo.citizens.traders_v3.core.locale.LocaleManager;
import net.dandielo.citizens.traders_v3.core.tools.StringTools;
import net.dandielo.citizens.traders_v3.traders.clicks.ClickHandler;
import net.dandielo.citizens.traders_v3.traders.setting.Settings;
import net.dandielo.citizens.traders_v3.traders.stock.Stock;
import net.dandielo.citizens.traders_v3.traders.stock.StockItem;
import net.dandielo.citizens.traders_v3.traders.wallet.Wallet;
import net.dandielo.citizens.traders_v3.traits.TraderTrait;
import net.dandielo.citizens.traders_v3.traits.WalletTrait;
import net.dandielo.citizens.traders_v3.utils.ItemUtils;

public abstract class Trader implements tNpc {
	//Click handlers
	private static Map<Class<? extends Trader>, List<Method>> handlers = new HashMap<Class<? extends Trader>, List<Method>>();
	
	public static void registerHandlers(Class<? extends Trader> clazz)
	{
		//debug info
		Debugger.info("Registering click handlers for trader type: ", clazz.getSimpleName());
		
		List<Method> methods = new ArrayList<Method>();
		for ( Method method : clazz.getMethods() )
			if ( method.isAnnotationPresent(ClickHandler.class) )
				methods.add(method);
		handlers.put(clazz, methods);
	}
	
	//static helpers
	protected LocaleManager locale = LocaleManager.locale;
	
	//temp data
	private int lastSlot = -1;
	private StockItem selectedItem = null;
	
	//the trader class
	protected Settings settings;
	protected Wallet wallet;
	protected Stock stock;
	protected Player player;
	
	protected Inventory inventory;
	protected Status baseStatus;
	protected Status status;
	
	//constructor
	public Trader(TraderTrait trader, WalletTrait wallet, Player player)
	{
		//debug info
		Debugger.info("Creating a trader, for: ", player.getName());
		
		settings = trader.getSettings();
		status = getDefaultStatus();
		stock = trader.getStock();
		this.wallet = wallet.getWallet();
		this.player = player;
	}
	
	//trader getters
	public Settings getSettings()
	{
		return settings;
	}
	
	public Status getStatus() 
	{
		return status;
	}
	
	public NPC getNPC()
	{
		return settings.getNPC();
	}
	
	public void parseStatus(Status newStatus)
	{
	    status = newStatus;
		baseStatus = Status.parseBaseManageStatus(baseStatus, newStatus);
	}
	
	public Stock getStock()
	{
		return stock;
	}
	
	public boolean equals(NPC npc)
	{
		return settings.getNPC().getId() == npc.getId();
	}
	
	//inventory click handler
	@Override
	public void onManageInventoryClick(InventoryClickEvent e)
	{
		inventoryClickParser(e);
	}
	
	@Override
	public void onInventoryClick(InventoryClickEvent e)
	{ 
		inventoryClickParser(e);
	}
	
	private final void inventoryClickParser(InventoryClickEvent e)
	{
		//debug info
		Debugger.info("Parsing click event");
		
        boolean top = e.getView().convertSlot(e.getRawSlot()) == e.getRawSlot();
		
		//get all handlers
		List<Method> methods = handlers.get(getClass());
		for ( Method method : methods )
		{
			ClickHandler handler = method.getAnnotation(ClickHandler.class);

			//debug info
			Debugger.info("Method: ", method.getName());
			Debugger.info("Checking shift click requirement");
			
			if ( !handler.shift() ? !e.isShiftClick() : true )
			{
				//debug info
				Debugger.info("Checking trader status requirement");
				
				if ( checkStatusWith(handler.status()) && handler.inventory().equals(top) )
				{
					try 
					{
						//debug info
						Debugger.info("Executing");
						
						method.invoke(this, e);
					} 
					catch (Exception ex) 
					{
						//debug info
						Debugger.critical("While executing inventory click event");
						Debugger.critical("Exception: ", ex.getClass().getSimpleName());
						Debugger.critical("Method: ", method.getName());
						Debugger.critical("Trader: ", this.getSettings().getNPC().getName(), ", player: ", player.getName());
						Debugger.critical(" ");
						Debugger.critical("Exception message: ", ex.getMessage());
						Debugger.high("Stack trace: ", StringTools.stackTrace(ex.getStackTrace()));
						
						//cancel the event because of the exception!
						e.setCancelled(true);
					}
				}
			}
		}
	}
	
	/** Transaction methods */
	public boolean sellTransaction()
	{
		if ( wallet.withdraw(player, selectedItem.getPrice()) )
		{
			wallet.deposit(this, selectedItem.getPrice());
			return true;
		}
		return false;
	}
	
	public boolean buyTransaction()
	{
		if ( wallet.withdraw(this, selectedItem.getPrice()) )
		{
			wallet.deposit(player, selectedItem.getPrice());
			return true;
		}
		return false;
	}

	/** Helper methods */
/*	public void addToInventory()
	{
		//debug info
		Debugger.info("Adding item to players inventory, player", player.getName());
		
		player.getInventory().addItem(selectedItem.getItem());
	}*/
	public final boolean inventoryHasPlace()
	{
		return inventoryHasPlace(0);
	}
	
	public final boolean inventoryHasPlace(int slot) 
	{
		//debug info
		Debugger.info("Checking players inventory space");
		Debugger.info("Player: ", player.getName(), ", item: ", selectedItem.getItem().getType().name().toLowerCase());
				
		return _inventoryHasPlace(selectedItem.getAmount(slot));
	}
	public final boolean _inventoryHasPlace(int amount) 
	{
		/*PlayerInventory inventory = player.getInventory();
		int amountToAdd = amount;
		
		//get all item stack with the same type
		for ( ItemStack item : inventory.all(selectedItem.getItemStack().getType()).values() )
		{
			if ( item.getDurability() == selectedItem.getItemStack().getDurability() ) 
			{
				if ( MetaTools.getName(item).equals(selectedItem.getName()) ) 
				{
					if ( item.getAmount() + amountToAdd <= selectedItem.getItemStack().getMaxStackSize() )
						return true;
					
					if ( item.getAmount() < 64 ) {
						amountToAdd = ( item.getAmount() + amountToAdd ) % 64; 
					}
					
					if ( amountToAdd <= 0 )
						return true;
				}
			}
		}

		//if we still ahve some items to add, is there an empty slot for them?
		if ( inventory.firstEmpty() < inventory.getSize() 
				&& inventory.firstEmpty() >= 0 ) {
			return true;
		}*/
		return false;
	}
	
	
	
	public final boolean addToInventory() 
	{
		return addToInventory(0);
	}
	
	public final boolean addToInventory(int slot) 
	{
		//debug info
		Debugger.info("Adding item to players inventory");
		Debugger.info("Player: ", player.getName(), ", item: ", selectedItem.getItem().getType().name().toLowerCase());
				
		return _addToInventory(selectedItem.getAmount(slot));
	}
	
	private final boolean _addToInventory(int amount) 
	{
		PlayerInventory inventory = player.getInventory();
		int amountToAdd = amount;

		for ( ItemStack item : inventory.all(selectedItem.getItem().getType()).values() ) 
		{
			if ( selectedItem.equals(ItemUtils.createStockItem(item)) )
			{
				
			}
		/*	if ( item.getDurability() == selectedItem.getItemStack().getDurability() )
			{
				if ( MetaTools.getName(item).equals(selectedItem.getName()) && selectedItem.equalsLores(item) && selectedItem.equalsFireworks(item) ) 
				{
					//add amount to an item in the inventory, its done
					if ( item.getAmount() + amountToAdd <= selectedItem.getItemStack().getMaxStackSize() ) {
						item.setAmount( item.getAmount() + amountToAdd );
						setItemPriceLore(item);
						return true;
					} 
					
					//add amount to an item in the inventory, but we still got some left
					if ( item.getAmount() < selectedItem.getItemStack().getMaxStackSize() ) {
						amountToAdd = ( item.getAmount() + amountToAdd ) % selectedItem.getItemStack().getMaxStackSize(); 
						item.setAmount(selectedItem.getItemStack().getMaxStackSize());
						setItemPriceLore(item);
					}
						
					//nothing left
					if ( amountToAdd <= 0 )
						return true;
				}
			}*/
		}
		return false;
	/*	
		//create new stack
		if ( inventory.firstEmpty() < inventory.getSize() 
				&& inventory.firstEmpty() >= 0 ) {
			
			//new stack
			ItemStack is = selectedItem.getItemStack().clone();
			is.setAmount(amountToAdd);
			
			setItemPriceLore(is);
			
			//set the item info the inv
			inventory.setItem(inventory.firstEmpty(), is);
			return true;
		}
		
		//could not be added to inventory
		return false;*/
	}
	
	
	
	
	
	
	/** 
	 * Selects the item using the slot as search key. It will search in a stock depending on the actual traders status.
	 * The result will be stored and returned.
	 * 
	 * @param slot 
	 *     Search for item at slot
	 * @return 
	 *     Returns the item found or null otherwise    
	 * 
	 * @author dandielo
	 */
	public StockItem selectItem(int slot)
	{
		return (selectedItem = stock.getItem(slot, status.asStock()));
	}

	/** 
	 * Selects the item using a bukkit item to compare with. If a similar item is found it will be stored.
	 * 
	 * @param item 
	 *     Item to compare with
	 * @return 
	 *     Returns the item found, or null otherwise
	 * @author dandielo
	 */
	public StockItem selectItem(ItemStack item)
	{
		return stock.getItem(ItemUtils.createStockItem(item), status.asStock());
	}

	/** 
	 * Selects the item using the slot as search key. It will search in a stock depending on the actual traders status.
	 * The result will be checked true if an item was found false otherwise. The item found will be stored.
	 * 
	 * @param slot 
	 *     Search for item at slot
	 * @return 
	 *     Returns true if the item was found, false otherwise   
	 * @author dandielo
	 */
	//TODO This might be not needed
	public boolean selectAndCheckItem(int slot)
	{
		return (selectedItem = stock.getItem(slot, status.asStock())) != null;
	}
	
	/** 
	 * Selects the item using a bukkit item to compare with. If a similar item is found it will be stored.
	 * 
	 * @param item 
	 *     Item to compare with
	 * @return 
	 *     Returns true if the item was found, false otherwise   
	 * @author dandielo
	 */
	//TODO This might be not needed
	public boolean selectAndCheckItem(ItemStack item)
	{
		return (selectedItem = stock.getItem(ItemUtils.createStockItem(item), status.asStock())) != null;
	}
	
	/** 
	 * @return returns true if there is a item stored, false otherwise
	 * @author dandielo 
	 */
	public boolean hasSelectedItem()
	{
		return selectedItem != null;
	}
	
	/**
	 * @return 
	 *     Returns the stored item 
	 * @author dandielo 
	 */
	public StockItem getSelectedItem()
	{
		return selectedItem;
	}
	
	/** 
	 * Checks if the currents trader status is present in the given array
	 *
	 * @param stat
	 *     array to check
	 *    
	 * @author dandielo
	 */
	public boolean checkStatusWith(Status[] stat)
	{
		for ( Status s : stat )
			if ( s.equals(status) )
				return true;
		return false;
	}
	
	/** 
	 * Depending on the config value, this function will check if the given slot was clicked 2 times in a row
	 * 
	 * @param slot
	 *     Inventory slot to be checked
	 *     
	 * @author dandielo
	 */
	public boolean handleClick(int slot)
	{
		if ( Settings.dClickEvent() )
			return lastSlot == (lastSlot = slot); 
		else
			return true;
	}

	/** 
	 * Makes a hit-test with the slot given and a item saved in the config file (UI items)
	 * 
	 * @param slot
	 *     slot to test
	 * @param item
	 *     the name of the item, that name is used in the config file
	 * 
	 * @author dandielo
	 */
	public boolean hitTest(int slot, String item)
	{
		return Settings.getUiItems().get(item).equals(inventory.getItem(slot));
	}

	//Trader status enum
	public static enum Status
	{
		SELL, BUY, SELL_AMOUNTS, MANAGE_SELL, MANAGE_BUY, MANAGE_PRICE, MANAGE_AMOUNTS, MANAGE_LIMITS;
		
		public boolean inManagementMode()
		{
			return !( this.equals(SELL) || this.equals(BUY) || this.equals(SELL_AMOUNTS) ); 
		}
		
		public static Status parseBaseManageStatus(Status oldStatus, Status newStatus)
		{
			return newStatus.equals(MANAGE_SELL) || newStatus.equals(MANAGE_BUY) ? newStatus : oldStatus;
		}
		
		public static Status baseManagementStatus(String status)
		{
			if ( MANAGE_SELL.name().toLowerCase().contains(status) )
				return MANAGE_SELL;
			return MANAGE_BUY;
		}
		
		public static Status baseStatus(String status)
		{
			if ( SELL.name().toLowerCase().equals(status) )
				return SELL;
			return BUY;
		}

		public String asStock() {
			return this.equals(BUY) || this.equals(MANAGE_BUY) ? "buy" : "sell";
		}
	}
	
	public Status getDefaultStatus()
	{
		return Status.baseStatus(settings.getStockStart());
	}
	
	public Status getDefaultManagementStatus()
	{
		return Status.baseManagementStatus(settings.getManagerStockStart());
	}
	
	
}