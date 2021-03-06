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
package org.l2jmobius.gameserver.network.clientpackets;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.datatables.sql.PetDataTable;
import org.l2jmobius.gameserver.handler.IItemHandler;
import org.l2jmobius.gameserver.handler.ItemHandler;
import org.l2jmobius.gameserver.model.actor.instance.ItemInstance;
import org.l2jmobius.gameserver.model.actor.instance.PetInstance;
import org.l2jmobius.gameserver.model.actor.instance.PlayerInstance;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.PetInfo;
import org.l2jmobius.gameserver.network.serverpackets.PetItemList;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

public class RequestPetUseItem extends GameClientPacket
{
	private static Logger LOGGER = Logger.getLogger(RequestPetUseItem.class.getName());
	private int _objectId;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		final PlayerInstance player = getClient().getPlayer();
		
		if (player == null)
		{
			return;
		}
		
		if (!getClient().getFloodProtectors().getUseItem().tryPerformAction("pet use item"))
		{
			return;
		}
		
		final PetInstance pet = (PetInstance) player.getPet();
		
		if (pet == null)
		{
			return;
		}
		
		final ItemInstance item = pet.getInventory().getItemByObjectId(_objectId);
		
		if (item == null)
		{
			return;
		}
		
		if (item.isWear())
		{
			return;
		}
		
		final int itemId = item.getItemId();
		
		if (player.isAlikeDead() || pet.isDead())
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
			sm.addItemName(item.getItemId());
			player.sendPacket(sm);
			return;
		}
		
		// check if the item matches the pet
		if (item.isEquipable())
		{
			if (PetDataTable.isWolf(pet.getNpcId()) && // wolf
				item.getItem().isForWolf())
			{
				useItem(pet, item, player);
				return;
			}
			else if (PetDataTable.isHatchling(pet.getNpcId()) && // hatchlings
				item.getItem().isForHatchling())
			{
				useItem(pet, item, player);
				return;
			}
			else if (PetDataTable.isStrider(pet.getNpcId()) && // striders
				item.getItem().isForStrider())
			{
				useItem(pet, item, player);
				return;
			}
			else if (PetDataTable.isBaby(pet.getNpcId()) && // baby pets (buffalo, cougar, kookaboora)
				item.getItem().isForBabyPet())
			{
				useItem(pet, item, player);
				return;
			}
			else
			{
				player.sendPacket(SystemMessageId.ITEM_NOT_FOR_PETS);
				return;
			}
		}
		else if (PetDataTable.isPetFood(itemId))
		{
			if (PetDataTable.isWolf(pet.getNpcId()) && PetDataTable.isWolfFood(itemId))
			{
				feed(player, pet, item);
				return;
			}
			
			if (PetDataTable.isSinEater(pet.getNpcId()) && PetDataTable.isSinEaterFood(itemId))
			{
				feed(player, pet, item);
				return;
			}
			else if (PetDataTable.isHatchling(pet.getNpcId()) && PetDataTable.isHatchlingFood(itemId))
			{
				feed(player, pet, item);
				return;
			}
			else if (PetDataTable.isStrider(pet.getNpcId()) && PetDataTable.isStriderFood(itemId))
			{
				feed(player, pet, item);
				return;
			}
			else if (PetDataTable.isWyvern(pet.getNpcId()) && PetDataTable.isWyvernFood(itemId))
			{
				feed(player, pet, item);
				return;
			}
			else if (PetDataTable.isBaby(pet.getNpcId()) && PetDataTable.isBabyFood(itemId))
			{
				feed(player, pet, item);
				return;
			}
		}
		
		final IItemHandler handler = ItemHandler.getInstance().getItemHandler(item.getItemId());
		
		if (handler != null)
		{
			useItem(pet, item, player);
		}
		else
		{
			final SystemMessage sm = new SystemMessage(SystemMessageId.ITEM_NOT_FOR_PETS);
			player.sendPacket(sm);
		}
		
		return;
	}
	
	private synchronized void useItem(PetInstance pet, ItemInstance item, PlayerInstance player)
	{
		if (item.isEquipable())
		{
			if (item.isEquipped())
			{
				pet.getInventory().unEquipItemInSlot(item.getEquipSlot());
			}
			else
			{
				pet.getInventory().equipItem(item);
			}
			
			final PetItemList pil = new PetItemList(pet);
			player.sendPacket(pil);
			
			final PetInfo pi = new PetInfo(pet);
			player.sendPacket(pi);
			// The PetInfo packet wipes the PartySpelled (list of active spells' icons). Re-add them
			pet.updateEffectIcons(true);
		}
		else
		{
			// LOGGER.finest("item not equipable id:"+ item.getItemId());
			final IItemHandler handler = ItemHandler.getInstance().getItemHandler(item.getItemId());
			
			if (handler == null)
			{
				LOGGER.warning("no itemhandler registered for itemId:" + item.getItemId());
			}
			else
			{
				handler.useItem(pet, item);
			}
		}
	}
	
	/**
	 * When fed by owner double click on food from pet inventory. <BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : 1 food = 100 points of currentFed</B></FONT><BR>
	 * <BR>
	 * @param player
	 * @param pet
	 * @param item
	 */
	private void feed(PlayerInstance player, PetInstance pet, ItemInstance item)
	{
		// if pet has food in inventory
		if (pet.destroyItem("Feed", item.getObjectId(), 1, pet, false))
		{
			pet.setCurrentFed(pet.getCurrentFed() + 100);
		}
		
		pet.broadcastStatusUpdate();
	}
}
