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
package quests.Q00080_SagaOfTheWindRider;

import com.l2jmobius.gameserver.model.Location;

import quests.AbstractSagaQuest;

/**
 * Saga of the Wind Rider (80)
 * @author Emperorc
 */
public class Q00080_SagaOfTheWindRider extends AbstractSagaQuest
{
	public Q00080_SagaOfTheWindRider()
	{
		super(80);
		_npc = new int[]
		{
			31603,
			31624,
			0, // FIXME: 31284 - Elena
			0, // FIXME: 31615 - Hermit
			31612,
			31646,
			31648,
			31652,
			31654,
			31655,
			0, // FIXME: 31659 - Tablet of Vision
			31616
		};
		Items = new int[]
		{
			7080,
			0, // FIXME: 7517 - Donath's' Dish
			7081,
			7495,
			7278,
			7309,
			7340,
			7371,
			0, // FIXME: 7402 - Resonance Amulet - 5
			0, // FIXME: 7433 - Resonance Amulet - 6
			7103,
			0
		};
		Mob = new int[]
		{
			27300,
			27229,
			27303
		};
		classid = new int[]
		{
			101
		};
		prevclass = new int[]
		{
			0x17
		};
		npcSpawnLocations = new Location[]
		{
			new Location(161719, -92823, -1893),
			new Location(124314, 82155, -2803),
			new Location(124355, 82155, -2803)
		};
		Text = new String[]
		{
			"PLAYERNAME! Pursued to here! However, I jumped out of the Banshouren boundaries! You look at the giant as the sign of power!",
			"... Oh ... good! So it was ... let's begin!",
			"I do not have the patience ..! I have been a giant force ...! Cough chatter ah ah ah!",
			"Paying homage to those who disrupt the orderly will be PLAYERNAME's death!",
			"Now, my soul freed from the shackles of the millennium, Halixia, to the back side I come ...",
			"Why do you interfere others' battles?",
			"This is a waste of time.. Say goodbye...!",
			"...That is the enemy",
			"...Goodness! PLAYERNAME you are still looking?",
			"PLAYERNAME ... Not just to whom the victory. Only personnel involved in the fighting are eligible to share in the victory.",
			"Your sword is not an ornament. Don't you think, PLAYERNAME?",
			"Goodness! I no longer sense a battle there now.",
			"let...",
			"Only engaged in the battle to bar their choice. Perhaps you should regret.",
			"The human nation was foolish to try and fight a giant's strength.",
			"Must...Retreat... Too...Strong.",
			"PLAYERNAME. Defeat...by...retaining...and...Mo...Hacker",
			"....! Fight...Defeat...It...Fight...Defeat...It..."
		};
		registerNPCs();
	}
}