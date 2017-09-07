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
package instances.HideoutOfTheDawn;

import com.l2jmobius.gameserver.model.Location;
import com.l2jmobius.gameserver.model.actor.L2Npc;
import com.l2jmobius.gameserver.model.actor.instance.L2PcInstance;
import com.l2jmobius.gameserver.model.instancezone.InstanceWorld;

import instances.AbstractInstance;

/**
 * Hideout of the Dawn instance zone.
 * @author Adry_85
 */
public final class HideoutOfTheDawn extends AbstractInstance
{
	protected class HotDWorld extends InstanceWorld
	{
		
	}
	
	// NPCs
	private static final int WOOD = 32593;
	private static final int JAINA = 32617;
	// Location
	private static final Location WOOD_LOC = new Location(-23758, -8959, -5384);
	private static final Location JAINA_LOC = new Location(147072, 23743, -1984);
	// Misc
	private static final int TEMPLATE_ID = 113;
	
	public HideoutOfTheDawn()
	{
		super(HideoutOfTheDawn.class.getSimpleName());
		addFirstTalkId(JAINA);
		addStartNpc(WOOD);
		addTalkId(WOOD, JAINA);
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2PcInstance player)
	{
		String htmltext = null;
		switch (event)
		{
			case "32617-01.html":
			case "32617-02a.html":
			{
				htmltext = event;
				break;
			}
			case "32617-02.html":
			{
				player.setInstanceId(0);
				player.teleToLocation(JAINA_LOC, true);
				htmltext = event;
				break;
			}
			case "32593-01.html":
			{
				enterInstance(player, new HotDWorld(), "HideoutOfTheDawn.xml", TEMPLATE_ID);
				htmltext = event;
			}
		}
		return htmltext;
	}
	
	@Override
	public void onEnterInstance(L2PcInstance player, InstanceWorld world, boolean firstEntrance)
	{
		if (firstEntrance)
		{
			world.addAllowed(player.getObjectId());
		}
		teleportPlayer(player, WOOD_LOC, world.getInstanceId(), false);
	}
}