/*
 * This file is part of the L2J Mobius project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.l2jmobius.gameserver.model.quest;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import org.l2jmobius.commons.concurrent.ThreadPool;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.sql.impl.AnnouncementsTable;
import org.l2jmobius.gameserver.data.xml.impl.NpcData;
import org.l2jmobius.gameserver.datatables.EventDroplist;
import org.l2jmobius.gameserver.datatables.ItemTable;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.instance.PlayerInstance;
import org.l2jmobius.gameserver.model.announce.EventAnnouncement;
import org.l2jmobius.gameserver.model.holders.DropHolder;
import org.l2jmobius.gameserver.script.DateRange;
import org.l2jmobius.gameserver.util.Broadcast;

/**
 * Parent class for long time events.<br>
 * Maintains config reading, spawn of NPCs, adding of event's drop.
 * @author GKR
 */
public class LongTimeEvent extends Quest
{
	private String _eventName;
	
	// Messages
	protected String _onEnterMsg = "Event is in process";
	protected String _endMsg = "Event ends!";
	
	protected DateRange _eventPeriod = null;
	protected DateRange _dropPeriod;
	
	// NPCs to spawm and their spawn points
	protected final List<NpcSpawn> _spawnList = new ArrayList<>();
	
	// Drop data for event
	protected final List<DropHolder> _dropList = new ArrayList<>();
	
	// Items to destroy when event ends.
	protected final List<Integer> _destoyItemsOnEnd = new ArrayList<>();
	
	protected class NpcSpawn
	{
		protected final Location loc;
		protected final int npcId;
		
		protected NpcSpawn(int pNpcId, Location spawnLoc)
		{
			loc = spawnLoc;
			npcId = pNpcId;
		}
	}
	
	public LongTimeEvent()
	{
		super(-1);
		loadConfig();
		
		if (_eventPeriod != null)
		{
			if (_eventPeriod.isWithinRange(new Date()))
			{
				startEvent();
				LOGGER.info("Event " + _eventName + " active till " + _eventPeriod.getEndDate());
			}
			else if (_eventPeriod.getStartDate().after(new Date()))
			{
				final long delay = _eventPeriod.getStartDate().getTime() - System.currentTimeMillis();
				ThreadPool.schedule(new ScheduleStart(), delay);
				LOGGER.info("Event " + _eventName + " will be started at " + _eventPeriod.getStartDate());
			}
			else
			{
				// Destroy items that must exist only on event period.
				destoyItemsOnEnd();
				LOGGER.info("Event " + _eventName + " has passed... Ignored ");
			}
		}
	}
	
	/**
	 * Load event configuration file
	 */
	private void loadConfig()
	{
		final File configFile = new File("data/scripts/events/" + getName() + "/config.xml");
		try
		{
			final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			final DocumentBuilder db = dbf.newDocumentBuilder();
			final Document doc = db.parse(configFile);
			if (!doc.getDocumentElement().getNodeName().equalsIgnoreCase("event"))
			{
				throw new NullPointerException("WARNING!!! " + getName() + " event: bad config file!");
			}
			_eventName = doc.getDocumentElement().getAttributes().getNamedItem("name").getNodeValue();
			final String period = doc.getDocumentElement().getAttributes().getNamedItem("active").getNodeValue();
			_eventPeriod = DateRange.parse(period, new SimpleDateFormat("dd MM yyyy", Locale.US));
			
			if (doc.getDocumentElement().getAttributes().getNamedItem("dropPeriod") != null)
			{
				final String dropPeriod = doc.getDocumentElement().getAttributes().getNamedItem("dropPeriod").getNodeValue();
				_dropPeriod = DateRange.parse(dropPeriod, new SimpleDateFormat("dd MM yyyy", Locale.US));
				// Check if drop period is within range of event period
				if (!_eventPeriod.isWithinRange(_dropPeriod.getStartDate()) || !_eventPeriod.isWithinRange(_dropPeriod.getEndDate()))
				{
					_dropPeriod = _eventPeriod;
				}
			}
			else
			{
				_dropPeriod = _eventPeriod; // Drop period, if not specified, assumes all event period.
			}
			
			if (_eventPeriod == null)
			{
				throw new NullPointerException("WARNING!!! " + getName() + " event: illegal event period");
			}
			
			final Date today = new Date();
			
			if (_eventPeriod.getStartDate().after(today) || _eventPeriod.isWithinRange(today))
			{
				for (Node n = doc.getDocumentElement().getFirstChild(); n != null; n = n.getNextSibling())
				{
					// Loading droplist
					if (n.getNodeName().equalsIgnoreCase("droplist"))
					{
						for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
						{
							if (d.getNodeName().equalsIgnoreCase("add"))
							{
								try
								{
									final int itemId = Integer.parseInt(d.getAttributes().getNamedItem("item").getNodeValue());
									final int minCount = Integer.parseInt(d.getAttributes().getNamedItem("min").getNodeValue());
									final int maxCount = Integer.parseInt(d.getAttributes().getNamedItem("max").getNodeValue());
									final String chance = d.getAttributes().getNamedItem("chance").getNodeValue();
									final int finalChance = !chance.isEmpty() && chance.endsWith("%") ? Integer.parseInt(chance.substring(0, chance.length() - 1)) * 10000 : 0;
									
									if (ItemTable.getInstance().getTemplate(itemId) == null)
									{
										LOGGER.warning(getName() + " event: " + itemId + " is wrong item id, item was not added in droplist");
										continue;
									}
									
									if (minCount > maxCount)
									{
										LOGGER.warning(getName() + " event: item " + itemId + " - min greater than max, item was not added in droplist");
										continue;
									}
									
									if ((finalChance < 10000) || (finalChance > 1000000))
									{
										LOGGER.warning(getName() + " event: item " + itemId + " - incorrect drop chance, item was not added in droplist");
										continue;
									}
									
									_dropList.add(new DropHolder(null, itemId, minCount, maxCount, finalChance));
								}
								catch (NumberFormatException nfe)
								{
									LOGGER.warning("Wrong number format in config.xml droplist block for " + getName() + " event");
								}
							}
						}
					}
					else if (n.getNodeName().equalsIgnoreCase("spawnlist"))
					{
						// Loading spawnlist
						for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
						{
							if (d.getNodeName().equalsIgnoreCase("add"))
							{
								try
								{
									final int npcId = Integer.parseInt(d.getAttributes().getNamedItem("npc").getNodeValue());
									final int xPos = Integer.parseInt(d.getAttributes().getNamedItem("x").getNodeValue());
									final int yPos = Integer.parseInt(d.getAttributes().getNamedItem("y").getNodeValue());
									final int zPos = Integer.parseInt(d.getAttributes().getNamedItem("z").getNodeValue());
									final int heading = d.getAttributes().getNamedItem("heading").getNodeValue() != null ? Integer.parseInt(d.getAttributes().getNamedItem("heading").getNodeValue()) : 0;
									
									if (NpcData.getInstance().getTemplate(npcId) == null)
									{
										LOGGER.warning(getName() + " event: " + npcId + " is wrong NPC id, NPC was not added in spawnlist");
										continue;
									}
									
									_spawnList.add(new NpcSpawn(npcId, new Location(xPos, yPos, zPos, heading)));
								}
								catch (NumberFormatException nfe)
								{
									LOGGER.warning("Wrong number format in config.xml spawnlist block for " + getName() + " event");
								}
							}
						}
					}
					else if (n.getNodeName().equalsIgnoreCase("messages"))
					{
						// Loading Messages
						for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
						{
							if (d.getNodeName().equalsIgnoreCase("add"))
							{
								final String msgType = d.getAttributes().getNamedItem("type").getNodeValue();
								final String msgText = d.getAttributes().getNamedItem("text").getNodeValue();
								if ((msgType != null) && (msgText != null))
								{
									if (msgType.equalsIgnoreCase("onEnd"))
									{
										_endMsg = msgText;
									}
									else if (msgType.equalsIgnoreCase("onEnter"))
									{
										_onEnterMsg = msgText;
									}
								}
							}
						}
					}
				}
			}
			
			// Load destroy item list at all times.
			for (Node n = doc.getDocumentElement().getFirstChild(); n != null; n = n.getNextSibling())
			{
				if (n.getNodeName().equalsIgnoreCase("destoyItemsOnEnd"))
				{
					for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
					{
						if (d.getNodeName().equalsIgnoreCase("item"))
						{
							try
							{
								final int itemId = Integer.parseInt(d.getAttributes().getNamedItem("id").getNodeValue());
								if (ItemTable.getInstance().getTemplate(itemId) == null)
								{
									LOGGER.warning(getScriptName() + " event: Item " + itemId + " does not exist.");
									continue;
								}
								_destoyItemsOnEnd.add(itemId);
							}
							catch (NumberFormatException nfe)
							{
								LOGGER.warning("Wrong number format in config.xml destoyItemsOnEnd block for " + getScriptName() + " event");
							}
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, getName() + " event: error reading " + configFile.getAbsolutePath() + " ! " + e.getMessage(), e);
		}
	}
	
	/**
	 * Maintenance event start - adds global drop, spawns event NPCs, shows start announcement.
	 */
	protected void startEvent()
	{
		final long currentTime = System.currentTimeMillis();
		// Add drop
		if ((_dropList != null) && (currentTime < _dropPeriod.getEndDate().getTime()))
		{
			for (DropHolder drop : _dropList)
			{
				EventDroplist.getInstance().addGlobalDrop(drop.getItemId(), drop.getMin(), drop.getMax(), (int) drop.getChance(), _dropPeriod);
			}
		}
		
		// Add spawns
		final Long millisToEventEnd = _eventPeriod.getEndDate().getTime() - currentTime;
		if (_spawnList != null)
		{
			for (NpcSpawn spawn : _spawnList)
			{
				addSpawn(spawn.npcId, spawn.loc.getX(), spawn.loc.getY(), spawn.loc.getZ(), spawn.loc.getHeading(), false, millisToEventEnd, false);
			}
		}
		
		// Send message on begin
		Broadcast.toAllOnlinePlayers(_onEnterMsg);
		
		// Add announce for entering players
		AnnouncementsTable.getInstance().addAnnouncement(new EventAnnouncement(_eventPeriod, _onEnterMsg));
		
		// Schedule event end (now only for message sending)
		ThreadPool.schedule(new ScheduleEnd(), millisToEventEnd);
	}
	
	/**
	 * @return event period
	 */
	public DateRange getEventPeriod()
	{
		return _eventPeriod;
	}
	
	/**
	 * @return {@code true} if now is event period
	 */
	public boolean isEventPeriod()
	{
		return _eventPeriod.isWithinRange(new Date());
	}
	
	/**
	 * @return {@code true} if now is drop period
	 */
	public boolean isDropPeriod()
	{
		return _dropPeriod.isWithinRange(new Date());
	}
	
	protected class ScheduleStart implements Runnable
	{
		@Override
		public void run()
		{
			startEvent();
		}
	}
	
	protected class ScheduleEnd implements Runnable
	{
		@Override
		public void run()
		{
			// Destroy items that must exist only on event period.
			destoyItemsOnEnd();
			// Send message on end
			Broadcast.toAllOnlinePlayers(_endMsg);
		}
	}
	
	void destoyItemsOnEnd()
	{
		if (!_destoyItemsOnEnd.isEmpty())
		{
			for (int itemId : _destoyItemsOnEnd)
			{
				// Remove item from online players.
				for (PlayerInstance player : World.getInstance().getPlayers())
				{
					if (player != null)
					{
						player.destroyItemByItemId(_eventName, itemId, -1, player, true);
					}
				}
				// Update database
				try (Connection con = DatabaseFactory.getConnection();
					PreparedStatement statement = con.prepareStatement("DELETE FROM items WHERE item_id=?"))
				{
					statement.setInt(1, itemId);
					statement.execute();
				}
				catch (SQLException e)
				{
					e.printStackTrace();
				}
			}
		}
	}
}
