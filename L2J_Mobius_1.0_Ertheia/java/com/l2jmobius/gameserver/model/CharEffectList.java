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
package com.l2jmobius.gameserver.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.l2jmobius.Config;
import com.l2jmobius.gameserver.model.actor.L2Character;
import com.l2jmobius.gameserver.model.actor.L2Summon;
import com.l2jmobius.gameserver.model.actor.instance.L2PcInstance;
import com.l2jmobius.gameserver.model.effects.AbstractEffect;
import com.l2jmobius.gameserver.model.effects.EffectFlag;
import com.l2jmobius.gameserver.model.olympiad.OlympiadGameManager;
import com.l2jmobius.gameserver.model.olympiad.OlympiadGameTask;
import com.l2jmobius.gameserver.model.options.Options;
import com.l2jmobius.gameserver.model.skills.AbnormalType;
import com.l2jmobius.gameserver.model.skills.BuffInfo;
import com.l2jmobius.gameserver.model.skills.EffectScope;
import com.l2jmobius.gameserver.model.skills.Skill;
import com.l2jmobius.gameserver.network.serverpackets.AbnormalStatusUpdate;
import com.l2jmobius.gameserver.network.serverpackets.ExAbnormalStatusUpdateFromTarget;
import com.l2jmobius.gameserver.network.serverpackets.ExOlympiadSpelledInfo;
import com.l2jmobius.gameserver.network.serverpackets.PartySpelled;
import com.l2jmobius.gameserver.network.serverpackets.ShortBuffStatusUpdate;

/**
 * Effect lists.<br>
 * Holds all the buff infos that are affecting a creature.<br>
 * Manages the logic that controls whether a buff is added, remove, replaced or set inactive.<br>
 * Uses maps with skill ID as key and buff info DTO as value to avoid iterations.<br>
 * Uses Double-Checked Locking to avoid useless initialization and synchronization issues and overhead.<br>
 * Methods may resemble List interface, although it doesn't implement such interface.
 * @author Zoey76
 */
public final class CharEffectList
{
	private static final Logger _log = Logger.getLogger(CharEffectList.class.getName());
	/** Queue containing all effects from buffs for this effect list. */
	private volatile Queue<BuffInfo> _buffs;
	/** Queue containing all triggered skills for this effect list. */
	private volatile Queue<BuffInfo> _triggered;
	/** Queue containing all dances/songs for this effect list. */
	private volatile Queue<BuffInfo> _dances;
	/** Queue containing all toggle for this effect list. */
	private volatile Queue<BuffInfo> _toggles;
	/** Queue containing all debuffs for this effect list. */
	private volatile Queue<BuffInfo> _debuffs;
	/** Queue containing all passives for this effect list. They bypass most of the actions and they are not included in most operations. */
	private volatile Queue<BuffInfo> _passives;
	/** Queue containing all options for this effect list. They bypass most of the actions and they are not included in most operations. */
	private volatile Queue<BuffInfo> _options;
	/** Map containing the all stacked effect in progress for each abnormal type. */
	private volatile Map<AbnormalType, BuffInfo> _stackedEffects;
	/** Set containing all abnormal types that shouldn't be added to this creature effect list. */
	private volatile Set<AbnormalType> _blockedAbnormalTypes = null;
	/** Short buff skill ID. */
	private BuffInfo _shortBuff = null;
	/** If {@code true} this effect list has buffs removed on any action. */
	private final AtomicInteger _hasBuffsRemovedOnAnyAction = new AtomicInteger();
	/** If {@code true} this effect list has buffs removed on damage. */
	private final AtomicInteger _hasBuffsRemovedOnDamage = new AtomicInteger();
	/** Effect flags. */
	private long _effectFlags;
	/** If {@code true} only party icons need to be updated. */
	private boolean _partyOnly = false;
	/** The owner of this effect list. */
	private final L2Character _owner;
	/** Hidden buffs count, prevents iterations. */
	private final AtomicInteger _hiddenBuffs = new AtomicInteger();
	
	/**
	 * Constructor for effect list.
	 * @param owner the creature that owns this effect list
	 */
	public CharEffectList(L2Character owner)
	{
		_owner = owner;
	}
	
	/**
	 * Gets buff skills.
	 * @return the buff skills
	 */
	public Queue<BuffInfo> getBuffs()
	{
		if (_buffs == null)
		{
			synchronized (this)
			{
				if (_buffs == null)
				{
					_buffs = new ConcurrentLinkedQueue<>();
				}
			}
		}
		return _buffs;
	}
	
	/**
	 * Gets triggered skill skills.
	 * @return the triggered skill skills
	 */
	public Queue<BuffInfo> getTriggered()
	{
		if (_triggered == null)
		{
			synchronized (this)
			{
				if (_triggered == null)
				{
					_triggered = new ConcurrentLinkedQueue<>();
				}
			}
		}
		return _triggered;
	}
	
	/**
	 * Gets dance/song skills.
	 * @return the dance/song skills
	 */
	public Queue<BuffInfo> getDances()
	{
		if (_dances == null)
		{
			synchronized (this)
			{
				if (_dances == null)
				{
					_dances = new ConcurrentLinkedQueue<>();
				}
			}
		}
		return _dances;
	}
	
	/**
	 * Gets toggle skills.
	 * @return the toggle skills
	 */
	public Queue<BuffInfo> getToggles()
	{
		if (_toggles == null)
		{
			synchronized (this)
			{
				if (_toggles == null)
				{
					_toggles = new ConcurrentLinkedQueue<>();
				}
			}
		}
		return _toggles;
	}
	
	/**
	 * Gets debuff skills.
	 * @return the debuff skills
	 */
	public Queue<BuffInfo> getDebuffs()
	{
		if (_debuffs == null)
		{
			synchronized (this)
			{
				if (_debuffs == null)
				{
					_debuffs = new ConcurrentLinkedQueue<>();
				}
			}
		}
		return _debuffs;
	}
	
	/**
	 * Gets passive skills.
	 * @return the passive skills
	 */
	public Queue<BuffInfo> getPassives()
	{
		if (_passives == null)
		{
			synchronized (this)
			{
				if (_passives == null)
				{
					_passives = new ConcurrentLinkedQueue<>();
				}
			}
		}
		return _passives;
	}
	
	/**
	 * Gets passive skills.
	 * @return the options (augments) skills
	 */
	public Queue<BuffInfo> getOptions()
	{
		if (_options == null)
		{
			synchronized (this)
			{
				if (_options == null)
				{
					_options = new ConcurrentLinkedQueue<>();
				}
			}
		}
		return _options;
	}
	
	/**
	 * Gets all the effects on this effect list.
	 * @return all the effects on this effect list
	 */
	public List<BuffInfo> getEffects()
	{
		if (isEmpty())
		{
			return Collections.emptyList();
		}
		
		final List<BuffInfo> buffs = new ArrayList<>();
		if (hasBuffs())
		{
			buffs.addAll(getBuffs());
		}
		
		if (hasTriggered())
		{
			buffs.addAll(getTriggered());
		}
		
		if (hasDances())
		{
			buffs.addAll(getDances());
		}
		
		if (hasToggles())
		{
			buffs.addAll(getToggles());
		}
		
		if (hasDebuffs())
		{
			buffs.addAll(getDebuffs());
		}
		return buffs;
	}
	
	/**
	 * Gets the effect list where the skill effects should be.
	 * @param skill the skill
	 * @param option TODO
	 * @return the effect list
	 */
	private Queue<BuffInfo> getEffectList(Skill skill, Options option)
	{
		if (skill == null)
		{
			if (option != null)
			{
				return getOptions();
			}
			return null;
		}
		
		if (skill.isPassive())
		{
			return getPassives();
		}
		else if (skill.isDebuff())
		{
			return getDebuffs();
		}
		else if (skill.isTriggeredSkill())
		{
			return getTriggered();
		}
		else if (skill.isDance())
		{
			return getDances();
		}
		else if (skill.isToggle())
		{
			return getToggles();
		}
		else
		{
			return getBuffs();
		}
	}
	
	/**
	 * Verifies if this effect list contains the given skill ID.<br>
	 * Prevents initialization.
	 * @param skillId the skill ID to verify
	 * @return {@code true} if the skill ID is present in the effect list, {@code false} otherwise
	 */
	public boolean isAffectedBySkill(int skillId)
	{
		return getBuffInfoBySkillId(skillId) != null;
	}
	
	/**
	 * Gets the buff info by skill ID.<br>
	 * Prevents initialization.
	 * @param skillId the skill ID
	 * @return the buff info
	 */
	public BuffInfo getBuffInfoBySkillId(int skillId)
	{
		//@formatter:off
		return Stream.of(getBuffs(), getTriggered(), getDances(), getToggles(), getDebuffs(), getPassives())
			.flatMap(Queue::stream).filter(b -> b.getSkill().getId() == skillId)
			.findFirst()
			.orElse(null);
		//@formatter:on
	}
	
	/**
	 * Check if any buff info of this abnormal type exists.<br>
	 * @param type the abnormal skill type
	 * @return {@code true} if there is any buff info by this abnormal type, {@code false} otherwise
	 */
	public boolean hasAbnormalType(AbnormalType type)
	{
		return (_stackedEffects != null) && _stackedEffects.containsKey(type);
	}
	
	/**
	 * Gets a buff info by abnormal type.<br>
	 * It's O(1) for every buff in this effect list.
	 * @param type the abnormal skill type
	 * @return the buff info if it's present, {@code null} otherwise
	 */
	public BuffInfo getBuffInfoByAbnormalType(AbnormalType type)
	{
		return (_stackedEffects != null) ? _stackedEffects.get(type) : null;
	}
	
	/**
	 * Adds abnormal types to the blocked buff slot set.
	 * @param blockedAbnormalTypes the blocked buff slot set to add
	 */
	public void addBlockedAbnormalTypes(Set<AbnormalType> blockedAbnormalTypes)
	{
		if (_blockedAbnormalTypes == null)
		{
			synchronized (this)
			{
				if (_blockedAbnormalTypes == null)
				{
					_blockedAbnormalTypes = ConcurrentHashMap.newKeySet(blockedAbnormalTypes.size());
				}
			}
		}
		_blockedAbnormalTypes.addAll(blockedAbnormalTypes);
	}
	
	/**
	 * Removes abnormal types from the blocked buff slot set.
	 * @param blockedBuffSlots the blocked buff slot set to remove
	 * @return {@code true} if the blocked buff slots set has been modified, {@code false} otherwise
	 */
	public boolean removeBlockedAbnormalTypes(Set<AbnormalType> blockedBuffSlots)
	{
		if (_blockedAbnormalTypes != null)
		{
			return _blockedAbnormalTypes.removeAll(blockedBuffSlots);
		}
		return false;
	}
	
	/**
	 * Gets all the blocked abnormal types for this creature effect list.
	 * @return the current blocked abnormal types set
	 */
	public Set<AbnormalType> getBlockedAbnormalTypes()
	{
		return _blockedAbnormalTypes;
	}
	
	/**
	 * Gets the Short Buff info.
	 * @return the short buff info
	 */
	public BuffInfo getShortBuff()
	{
		return _shortBuff;
	}
	
	/**
	 * Sets the Short Buff data and sends an update if the effected is a player.
	 * @param info the buff info
	 */
	public void shortBuffStatusUpdate(BuffInfo info)
	{
		if (_owner.isPlayer())
		{
			_shortBuff = info;
			if (info == null)
			{
				_owner.sendPacket(ShortBuffStatusUpdate.RESET_SHORT_BUFF);
			}
			else
			{
				_owner.sendPacket(new ShortBuffStatusUpdate(info.getSkill().getId(), info.getSkill().getLevel(), info.getSkill().getSubLevel(), info.getTime()));
			}
		}
	}
	
	/**
	 * Checks if the given skill stacks with an existing one.
	 * @param skill the skill to verify
	 * @return {@code true} if this effect stacks with the given skill, {@code false} otherwise
	 */
	private boolean doesStack(Skill skill)
	{
		final AbnormalType type = skill.getAbnormalType();
		if (type.isNone() || isEmpty())
		{
			return false;
		}
		
		final Queue<BuffInfo> effects = getEffectList(skill, null);
		
		for (BuffInfo info : effects)
		{
			if ((info != null) && (info.getSkill().getAbnormalType() == type))
			{
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Gets the buffs count without including the hidden buffs (after getting an Herb buff).<br>
	 * Prevents initialization.
	 * @return the number of buffs in this creature effect list
	 */
	public int getBuffCount()
	{
		return hasBuffs() ? getBuffs().size() - _hiddenBuffs.get() - (getShortBuff() != null ? 1 : 0) : 0;
	}
	
	/**
	 * Gets the Songs/Dances count.<br>
	 * Prevents initialization.
	 * @return the number of Songs/Dances in this creature effect list
	 */
	public int getDanceCount()
	{
		return hasDances() ? getDances().size() : 0;
	}
	
	/**
	 * Gets the triggered buffs count.<br>
	 * Prevents initialization.
	 * @return the number of triggered buffs in this creature effect list
	 */
	public int getTriggeredBuffCount()
	{
		return hasTriggered() ? getTriggered().size() : 0;
	}
	
	/**
	 * Gets the hidden buff count.
	 * @return the number of hidden buffs
	 */
	public int getHiddenBuffsCount()
	{
		return _hiddenBuffs.get();
	}
	
	/**
	 * Auxiliary method to stop all effects from a buff info and remove it from an effect list and stacked effects.
	 * @param info the buff info
	 */
	protected void stopAndRemove(BuffInfo info)
	{
		stopAndRemove(true, info, getEffectList(info.getSkill(), null));
	}
	
	/**
	 * Auxiliary method to stop all effects from a buff info and remove it from an effect list and stacked effects.
	 * @param info the buff info
	 * @param effects the effect list
	 */
	protected void stopAndRemove(BuffInfo info, Queue<BuffInfo> effects)
	{
		stopAndRemove(true, info, effects);
	}
	
	/**
	 * Auxiliary method to stop all effects from a buff info and remove it from an effect list and stacked effects.
	 * @param removed {@code true} if the effect is removed, {@code false} otherwise
	 * @param info the buff info
	 * @param buffs the buff list
	 */
	private void stopAndRemove(boolean removed, BuffInfo info, Queue<BuffInfo> buffs)
	{
		if (info == null)
		{
			return;
		}
		
		// Removes the buff from the given effect list.
		buffs.remove(info);
		
		// Stop the buff effects.
		info.stopAllEffects(removed);
		
		// Augmentation options doesn't have skill
		if (info.getSkill() == null)
		{
			return;
		}
		
		// If it's a hidden buff that ends, then decrease hidden buff count.
		if (!info.isInUse())
		{
			_hiddenBuffs.decrementAndGet();
		}
		// Removes the buff from the stack.
		else if (_stackedEffects != null)
		{
			_stackedEffects.remove(info.getSkill().getAbnormalType());
		}
		
		// If it's an herb that ends, check if there are hidden buffs.
		if (info.getSkill().isAbnormalInstant() && hasBuffs())
		{
			for (BuffInfo buff : getBuffs())
			{
				if ((buff != null) && (buff.getSkill().getAbnormalType() == info.getSkill().getAbnormalType()) && !buff.isInUse())
				{
					// Sets the buff in use again.
					buff.setInUse(true);
					// Adds the buff to the stack.
					if (_stackedEffects != null)
					{
						_stackedEffects.put(buff.getSkill().getAbnormalType(), buff);
					}
					// If it's a hidden buff that gets activated, then decrease hidden buff count.
					_hiddenBuffs.decrementAndGet();
					
					// Recalculate all stats
					_owner.getStat().recalculateStats(true);
					break;
				}
			}
		}
		
		// Update flag for skills being removed on action or damage.
		if (info.getSkill().isRemovedOnAnyActionExceptMove())
		{
			_hasBuffsRemovedOnAnyAction.decrementAndGet();
		}
		if (info.getSkill().isRemovedOnDamage())
		{
			_hasBuffsRemovedOnDamage.decrementAndGet();
		}
		
		if (!removed)
		{
			info.getSkill().applyEffectScope(EffectScope.END, info, true, false);
		}
	}
	
	/**
	 * Exits all effects in this effect list.<br>
	 * Stops all the effects, clear the effect lists and updates the effect flags and icons.
	 */
	public void stopAllEffects()
	{
		// Stop buffs.
		stopAllBuffs(false, true);
		// Stop dances and songs.
		stopAllDances(false);
		// Stop toggles.
		stopAllToggles(false);
		// Stop debuffs.
		stopAllDebuffs(false);
		
		if (_stackedEffects != null)
		{
			_stackedEffects.clear();
		}
		
		// Update effect flags and icons.
		updateEffectList(true);
	}
	
	/**
	 * Stops all effects in this effect list except those that last through death.
	 */
	public void stopAllEffectsExceptThoseThatLastThroughDeath()
	{
		final boolean update = !isEmpty();
		
		getBuffs().stream().filter(info -> !info.getSkill().isStayAfterDeath()).forEach(info -> stopAndRemove(info, getBuffs()));
		getTriggered().stream().filter(info -> !info.getSkill().isStayAfterDeath()).forEach(info -> stopAndRemove(info, getTriggered()));
		getDebuffs().stream().filter(info -> !info.getSkill().isStayAfterDeath()).forEach(info -> stopAndRemove(info, getDebuffs()));
		getDances().stream().filter(info -> !info.getSkill().isStayAfterDeath()).forEach(info -> stopAndRemove(info, getDances()));
		getToggles().stream().filter(info -> !info.getSkill().isStayAfterDeath()).forEach(info -> stopAndRemove(info, getToggles()));
		
		// Update effect flags and icons.
		updateEffectList(update);
	}
	
	/**
	 * Stop all effects that doesn't stay on sub-class change.
	 */
	public void stopAllEffectsNotStayOnSubclassChange()
	{
		final boolean update = !isEmpty();
		
		getBuffs().stream().filter(info -> !info.getSkill().isIrreplacableBuff()).forEach(info -> stopAndRemove(info, getBuffs()));
		getTriggered().stream().filter(info -> !info.getSkill().isIrreplacableBuff()).forEach(info -> stopAndRemove(info, getTriggered()));
		getDebuffs().stream().filter(info -> !info.getSkill().isIrreplacableBuff()).forEach(info -> stopAndRemove(info, getDebuffs()));
		getDances().stream().filter(info -> !info.getSkill().isIrreplacableBuff()).forEach(info -> stopAndRemove(info, getDances()));
		getToggles().stream().filter(info -> !info.getSkill().isIrreplacableBuff()).forEach(info -> stopAndRemove(info, getToggles()));
		
		// Update effect flags and icons.
		updateEffectList(update);
	}
	
	/**
	 * Stops all the active buffs.
	 * @param update set to true to update the effect flags and icons
	 * @param triggered if {@code true} stops triggered skills buffs
	 */
	public void stopAllBuffs(boolean update, boolean triggered)
	{
		if (hasBuffs())
		{
			getBuffs().forEach(b -> stopAndRemove(b, getBuffs()));
		}
		
		if (triggered && hasTriggered())
		{
			getTriggered().forEach(b -> stopAndRemove(b, getTriggered()));
		}
		
		// Update effect flags and icons.
		updateEffectList(update);
	}
	
	/**
	 * Stops all active toggle skills.<br>
	 * Performs an update.
	 */
	public void stopAllToggles()
	{
		stopAllToggles(true);
	}
	
	/**
	 * Stops all active toggle skills.
	 * @param update set to true to update the effect flags and icons
	 */
	public void stopAllToggles(boolean update)
	{
		if (hasToggles())
		{
			// Ignore necessary toggles.
			getToggles().stream().filter(b -> !b.getSkill().isNecessaryToggle()).forEach(b -> stopAndRemove(b, getToggles()));
			// Update effect flags and icons.
			updateEffectList(update);
		}
	}
	
	public void stopAllTogglesOfGroup(int toggleGroup)
	{
		if (hasToggles())
		{
			getToggles().stream().filter(b -> b.getSkill().getToggleGroupId() == toggleGroup).forEach(b -> stopSkillEffects(true, b.getSkill()));
			// Update effect flags and icons.
			updateEffectList(true);
		}
	}
	
	/**
	 * Stops all active dances/songs skills.
	 * @param update set to true to update the effect flags and icons
	 */
	public void stopAllDances(boolean update)
	{
		if (hasDances())
		{
			getDances().forEach(b -> stopAndRemove(b, getDances()));
			// Update effect flags and icons.
			updateEffectList(update);
		}
	}
	
	/**
	 * Stops all active dances/songs skills.
	 * @param update set to true to update the effect flags and icons
	 */
	public void stopAllDebuffs(boolean update)
	{
		if (hasDebuffs())
		{
			getDebuffs().forEach(b -> stopAndRemove(b, getDebuffs()));
			// Update effect flags and icons.
			updateEffectList(update);
		}
	}
	
	/**
	 * Stops all active dances/songs skills.
	 * @param update set to true to update the effect flags and icons
	 */
	public void stopAllPassives(boolean update)
	{
		if (hasPassives())
		{
			getPassives().forEach(b -> stopAndRemove(b, getPassives()));
			// Update effect flags and icons.
			updateEffectList(update);
		}
	}
	
	/**
	 * Stops all active dances/songs skills.
	 * @param update set to true to update the effect flags and icons
	 */
	public void stopAllOptions(boolean update)
	{
		if (hasOptions())
		{
			getOptions().forEach(b -> stopAndRemove(b, getOptions()));
			// Update effect flags and icons.
			updateEffectList(update);
		}
	}
	
	/**
	 * Exit all effects having a specified flag.<br>
	 * @param effectFlag the flag of the effect to stop
	 */
	public void stopEffects(EffectFlag effectFlag)
	{
		if (isAffected(effectFlag))
		{
			//@formatter:off
			Stream.of(getBuffs(), getTriggered(), getDances(), getToggles(), getDebuffs())
				.flatMap(Queue::stream)
				.filter(Objects::nonNull)
				.filter(info -> info.getEffects().stream().anyMatch(effect -> (effect != null) && ((effect.getEffectFlags() & effectFlag.getMask()) != 0)))
				.forEach(info -> stopAndRemove(info));
			//@formatter:on
			
			// Update effect flags and icons.
			updateEffectList(true);
		}
	}
	
	/**
	 * Exits all effects created by a specific skill ID.<br>
	 * Removes the effects from the effect list.<br>
	 * Removes the stats from the creature.<br>
	 * Updates the effect flags and icons.<br>
	 * Presents two overloads:<br>
	 * {@link #stopSkillEffects(boolean, Skill)}<br>
	 * {@link #stopSkillEffects(boolean, AbnormalType)}
	 * @param removed {@code true} if the effect is removed, {@code false} otherwise
	 * @param skillId the skill ID
	 */
	public void stopSkillEffects(boolean removed, int skillId)
	{
		final BuffInfo info = getBuffInfoBySkillId(skillId);
		if (info != null)
		{
			remove(removed, info, null);
		}
	}
	
	/**
	 * Exits all effects created by a specific skill.<br>
	 * Removes the effects from the effect list.<br>
	 * Removes the stats from the creature.<br>
	 * Updates the effect flags and icons.<br>
	 * Presents two overloads:<br>
	 * {@link #stopSkillEffects(boolean, int)}<br>
	 * {@link #stopSkillEffects(boolean, AbnormalType)}
	 * @param removed {@code true} if the effect is removed, {@code false} otherwise
	 * @param skill the skill
	 */
	public void stopSkillEffects(boolean removed, Skill skill)
	{
		if (skill != null)
		{
			stopSkillEffects(removed, skill.getId());
		}
	}
	
	/**
	 * Exits all effects created by a specific skill abnormal type.<br>
	 * It's O(1) for every effect in this effect list except passive effects.<br>
	 * Presents two overloads:<br>
	 * {@link #stopSkillEffects(boolean, int)}<br>
	 * {@link #stopSkillEffects(boolean, Skill)}
	 * @param removed {@code true} if the effect is removed, {@code false} otherwise
	 * @param type the skill abnormal type
	 * @return {@code true} if there was a buff info with the given abnormal type
	 */
	public boolean stopSkillEffects(boolean removed, AbnormalType type)
	{
		if (_stackedEffects != null)
		{
			final BuffInfo old = _stackedEffects.remove(type);
			if (old != null)
			{
				stopSkillEffects(removed, old.getSkill());
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Exits all buffs effects of the skills with "removedOnAnyAction" set.<br>
	 * Called on any action except movement (attack, cast).
	 */
	public void stopEffectsOnAction()
	{
		if (_hasBuffsRemovedOnAnyAction.intValue() > 0)
		{
			final boolean update = !isEmpty();
			
			getBuffs().stream().filter(info -> info.getSkill().isRemovedOnAnyActionExceptMove()).forEach(info -> stopAndRemove(info, getBuffs()));
			getTriggered().stream().filter(info -> info.getSkill().isRemovedOnAnyActionExceptMove()).forEach(info -> stopAndRemove(info, getTriggered()));
			getDebuffs().stream().filter(info -> info.getSkill().isRemovedOnAnyActionExceptMove()).forEach(info -> stopAndRemove(info, getDebuffs()));
			getDances().stream().filter(info -> info.getSkill().isRemovedOnAnyActionExceptMove()).forEach(info -> stopAndRemove(info, getDances()));
			getToggles().stream().filter(info -> info.getSkill().isRemovedOnAnyActionExceptMove()).forEach(info -> stopAndRemove(info, getToggles()));
			
			// Update effect flags and icons.
			updateEffectList(update);
		}
	}
	
	public void stopEffectsOnDamage()
	{
		boolean update = false;
		if (_hasBuffsRemovedOnDamage.intValue() > 0)
		{
			update = !isEmpty();
			
			getBuffs().stream().filter(Objects::nonNull).filter(info -> info.getSkill().isRemovedOnDamage()).forEach(info -> stopAndRemove(info, getBuffs()));
			getTriggered().stream().filter(Objects::nonNull).filter(info -> info.getSkill().isRemovedOnDamage()).forEach(info -> stopAndRemove(info, getTriggered()));
			getDances().stream().filter(Objects::nonNull).filter(info -> info.getSkill().isRemovedOnDamage()).forEach(info -> stopAndRemove(info, getDances()));
			getToggles().stream().filter(Objects::nonNull).filter(info -> info.getSkill().isRemovedOnDamage()).forEach(info -> stopAndRemove(info, getToggles()));
			getDebuffs().stream().filter(Objects::nonNull).filter(info -> info.getSkill().isRemovedOnDamage()).forEach(info -> stopAndRemove(info, getDebuffs()));
		}
		
		// Update effect flags and icons.
		updateEffectList(update);
	}
	
	/**
	 * @param partyOnly
	 */
	public void updateEffectIcons(boolean partyOnly)
	{
		if (partyOnly)
		{
			_partyOnly = true;
		}
		// Update effect flags and icons.
		updateEffectList(true);
	}
	
	/**
	 * Verify if this effect list is empty.<br>
	 * Prevents initialization.
	 * @return {@code true} if this effect list contains any skills
	 */
	public boolean isEmpty()
	{
		return !hasBuffs() && !hasTriggered() && !hasDances() && !hasDebuffs() && !hasToggles();
	}
	
	/**
	 * Verify if this effect list has buffs skills.<br>
	 * Prevents initialization.
	 * @return {@code true} if {@link #_buffs} is not {@code null} and is not empty
	 */
	public boolean hasBuffs()
	{
		return (_buffs != null) && !_buffs.isEmpty();
	}
	
	/**
	 * Verify if this effect list has triggered skills.<br>
	 * Prevents initialization.
	 * @return {@code true} if {@link #_triggered} is not {@code null} and is not empty
	 */
	public boolean hasTriggered()
	{
		return (_triggered != null) && !_triggered.isEmpty();
	}
	
	/**
	 * Verify if this effect list has dance/song skills.<br>
	 * Prevents initialization.
	 * @return {@code true} if {@link #_dances} is not {@code null} and is not empty
	 */
	public boolean hasDances()
	{
		return (_dances != null) && !_dances.isEmpty();
	}
	
	/**
	 * Verify if this effect list has toggle skills.<br>
	 * Prevents initialization.
	 * @return {@code true} if {@link #_toggles} is not {@code null} and is not empty
	 */
	public boolean hasToggles()
	{
		return (_toggles != null) && !_toggles.isEmpty();
	}
	
	/**
	 * Verify if this effect list has debuffs skills.<br>
	 * Prevents initialization.
	 * @return {@code true} if {@link #_debuffs} is not {@code null} and is not empty
	 */
	public boolean hasDebuffs()
	{
		return (_debuffs != null) && !_debuffs.isEmpty();
	}
	
	/**
	 * Verify if this effect list has passive skills.<br>
	 * Prevents initialization.
	 * @return {@code true} if {@link #_passives} is not {@code null} and is not empty
	 */
	public boolean hasPassives()
	{
		return (_passives != null) && !_passives.isEmpty();
	}
	
	/**
	 * Verify if this effect list has options skills.<br>
	 * Prevents initialization.
	 * @return {@code true} if {@link #_options} is not {@code null} and is not empty
	 */
	public boolean hasOptions()
	{
		return (_options != null) && !_options.isEmpty();
	}
	
	/**
	 * Executes a procedure for all effects.<br>
	 * Prevents initialization.
	 * @param function the function to execute
	 * @param dances if {@code true} dances/songs will be included
	 */
	public void forEach(Function<BuffInfo, Boolean> function, boolean dances)
	{
		boolean update = false;
		if (hasBuffs())
		{
			for (BuffInfo info : getBuffs())
			{
				update |= function.apply(info);
			}
		}
		
		if (hasTriggered())
		{
			for (BuffInfo info : getTriggered())
			{
				update |= function.apply(info);
			}
		}
		
		if (dances && hasDances())
		{
			for (BuffInfo info : getDances())
			{
				update |= function.apply(info);
			}
		}
		
		if (hasToggles())
		{
			for (BuffInfo info : getToggles())
			{
				update |= function.apply(info);
			}
		}
		
		if (hasDebuffs())
		{
			for (BuffInfo info : getDebuffs())
			{
				update |= function.apply(info);
			}
		}
		// Update effect flags and icons.
		updateEffectList(update);
	}
	
	/**
	 * Removes a set of effects from this effect list.
	 * @param removed {@code true} if the effect is removed, {@code false} otherwise
	 * @param info the effects to remove
	 * @param option augmentation option
	 */
	public void remove(boolean removed, BuffInfo info, Options option)
	{
		if (info == null)
		{
			return;
		}
		
		// Remove the effect from creature effects.
		stopAndRemove(removed, info, getEffectList(info.getSkill(), option));
		
		// Update effect flags and icons.
		updateEffectList(true);
	}
	
	/**
	 * Adds a set of effects to this effect list.
	 * @param info the buff info
	 */
	public void add(BuffInfo info)
	{
		if (info == null)
		{
			return;
		}
		
		// Support for blocked buff slots.
		final Skill skill = info.getSkill();
		
		// Options
		if (skill == null)
		{
			if (info.getOption() != null)
			{
				getOptions().add(info);
				
				// Initialize effects.
				info.initializeEffects();
			}
			return;
		}
		
		if (info.getEffector() != null)
		{
			// Check for debuffs against target.
			if ((info.getEffector() != info.getEffected()) && skill.isBad())
			{
				// Check if effected is debuff blocked.
				if ((info.getEffected().isDebuffBlocked() || (info.getEffector().isGM() && !info.getEffector().getAccessLevel().canGiveDamage())))
				{
					return;
				}
				
				if (info.getEffector().isPlayer() && info.getEffected().isPlayer() && info.getEffected().isAffected(EffectFlag.FACEOFF) && (info.getEffected().getActingPlayer().getAttackerObjId() != info.getEffector().getObjectId()))
				{
					return;
				}
			}
			
			// Check if buff skills are blocked.
			if (info.getEffected().isBuffBlocked() && !skill.isBad())
			{
				return;
			}
		}
		
		if ((_blockedAbnormalTypes != null) && _blockedAbnormalTypes.contains(skill.getAbnormalType()))
		{
			return;
		}
		
		// Passive effects are treated specially
		if (skill.isPassive())
		{
			// Passive effects don't need stack type!
			if (!skill.getAbnormalType().isNone())
			{
				_log.warning("Passive " + skill + " with abnormal type: " + skill.getAbnormalType() + "!");
			}
			
			// Check for passive skill conditions.
			if (!skill.checkCondition(info.getEffector(), info.getEffected()))
			{
				return;
			}
			
			// Puts the effects in the list.
			getPassives().stream().filter(b -> b.getSkill().getId() == skill.getId()).forEach(b ->
			{
				b.setInUse(false);
				getPassives().remove(b);
			});
			
			getPassives().add(info);
			
			// Initialize effects.
			info.initializeEffects();
			return;
		}
		
		// Prevent adding and initializing buffs/effects on dead creatures.
		if (info.getEffected().isDead())
		{
			return;
		}
		
		// The old effect is removed using Map#remove(key) instead of Map#put(key, value) (that would be the wisest choice),
		// Because order matters and put method would insert in the same place it was before, instead of, at the end of the effect list
		// Where new buff should be placed
		if (skill.getAbnormalType().isNone())
		{
			stopSkillEffects(true, skill);
		}
		// Verify stacked skills.
		else
		{
			if (_stackedEffects == null)
			{
				synchronized (this)
				{
					if (_stackedEffects == null)
					{
						_stackedEffects = new ConcurrentHashMap<>();
					}
				}
			}
			
			if (_stackedEffects.containsKey(skill.getAbnormalType()))
			{
				BuffInfo stackedInfo = _stackedEffects.get(skill.getAbnormalType());
				// Skills are only replaced if the incoming buff has greater or equal abnormal level.
				if ((stackedInfo != null) && (skill.getAbnormalLvl() >= stackedInfo.getSkill().getAbnormalLvl()))
				{
					// If it is an herb, set as not in use the lesser buff.
					// Effect will be present in the effect list.
					// Effects stats are removed and onActionTime() is not called.
					// But finish task continues to run, and ticks as well.
					if (skill.isAbnormalInstant())
					{
						if (stackedInfo.getSkill().isAbnormalInstant())
						{
							stopSkillEffects(true, skill.getAbnormalType());
							stackedInfo = _stackedEffects.get(skill.getAbnormalType());
						}
						
						if (stackedInfo != null)
						{
							stackedInfo.setInUse(false);
							// Recalculate all stats
							_owner.getStat().recalculateStats(true);
							_hiddenBuffs.incrementAndGet();
						}
					}
					// Remove buff that will stack with the abnormal type.
					else
					{
						if (stackedInfo.getSkill().isAbnormalInstant())
						{
							stopSkillEffects(true, skill.getAbnormalType());
						}
						stopSkillEffects(true, skill.getAbnormalType());
					}
				}
				// If the new buff is a lesser buff, then don't add it.
				else
				{
					return;
				}
			}
			_stackedEffects.put(skill.getAbnormalType(), info);
		}
		
		// Select the map that holds the effects related to this skill.
		final Queue<BuffInfo> effects = getEffectList(skill, null);
		// Remove first buff when buff list is full.
		if (!skill.isDebuff() && !skill.isToggle() && !skill.is7Signs() && !doesStack(skill))
		{
			int buffsToRemove = -1;
			if (skill.isDance())
			{
				buffsToRemove = getDanceCount() - Config.DANCES_MAX_AMOUNT;
			}
			else if (skill.isTriggeredSkill())
			{
				buffsToRemove = getTriggeredBuffCount() - Config.TRIGGERED_BUFFS_MAX_AMOUNT;
			}
			else if (!skill.isHealingPotionSkill())
			{
				buffsToRemove = getBuffCount() - _owner.getStat().getMaxBuffCount();
			}
			
			for (BuffInfo bi : effects)
			{
				if (buffsToRemove < 0)
				{
					break;
				}
				
				if (!bi.isInUse())
				{
					continue;
				}
				
				stopAndRemove(bi, effects);
				
				buffsToRemove--;
			}
		}
		
		// Update flag for skills being removed on action or damage.
		if (skill.isRemovedOnAnyActionExceptMove())
		{
			_hasBuffsRemovedOnAnyAction.incrementAndGet();
		}
		if (skill.isRemovedOnDamage())
		{
			_hasBuffsRemovedOnDamage.incrementAndGet();
		}
		
		// After removing old buff (same ID) or stacked buff (same abnormal type),
		// Add the buff to the end of the effect list.
		effects.add(info);
		// Initialize effects.
		info.initializeEffects();
		// Update effect flags and icons.
		updateEffectList(true);
	}
	
	/**
	 * Update effect icons.<br>
	 * Prevents initialization.
	 */
	private void updateEffectIcons()
	{
		if (_owner == null)
		{
			return;
		}
		
		if (_owner.isPlayable())
		{
			final AbnormalStatusUpdate asu;
			final PartySpelled ps;
			final PartySpelled psSummon;
			final ExOlympiadSpelledInfo os;
			final boolean isSummon;
			
			if (_owner.isPlayer())
			{
				if (_partyOnly)
				{
					asu = null;
					_partyOnly = false;
				}
				else
				{
					asu = new AbnormalStatusUpdate();
				}
				
				ps = _owner.isInParty() ? new PartySpelled(_owner) : null;
				os = _owner.getActingPlayer().isInOlympiadMode() && _owner.getActingPlayer().isOlympiadStart() ? new ExOlympiadSpelledInfo(_owner.getActingPlayer()) : null;
				psSummon = null;
				isSummon = false;
			}
			else if (_owner.isSummon())
			{
				isSummon = true;
				asu = null;
				ps = new PartySpelled(_owner);
				os = null;
				psSummon = new PartySpelled(_owner);
			}
			else
			{
				asu = null;
				ps = null;
				os = null;
				psSummon = null;
				isSummon = false;
			}
			
			//@formatter:off
			Stream.of(getBuffs(), getTriggered(), getDances(), getToggles(), getDebuffs())
				.flatMap(Queue::stream)
				.filter(Objects::nonNull)
				.forEach(i ->
				{
					if (i.getSkill().isHealingPotionSkill())
					{
						shortBuffStatusUpdate(i);
					}
					else
					{
						addIcon(i, asu, ps, psSummon, os, isSummon);
					}
				});
			//@formatter:on
			
			if (asu != null)
			{
				_owner.sendPacket(asu);
			}
			
			if (ps != null)
			{
				if (_owner.isSummon())
				{
					final L2PcInstance summonOwner = ((L2Summon) _owner).getOwner();
					if (summonOwner != null)
					{
						if (summonOwner.isInParty())
						{
							summonOwner.getParty().broadcastToPartyMembers(summonOwner, psSummon); // send to all member except summonOwner
						}
						
						summonOwner.sendPacket(ps); // now send to summonOwner
					}
				}
				else if (_owner.isPlayer() && _owner.isInParty())
				{
					_owner.getParty().broadcastPacket(ps);
				}
			}
			
			if (os != null)
			{
				final OlympiadGameTask game = OlympiadGameManager.getInstance().getOlympiadTask(_owner.getActingPlayer().getOlympiadGameId());
				if ((game != null) && game.isBattleStarted())
				{
					game.getStadium().broadcastPacketToObservers(os);
				}
			}
		}
		
		// Update effect icons for everyone targeting this owner.
		final ExAbnormalStatusUpdateFromTarget upd = new ExAbnormalStatusUpdateFromTarget(_owner);
		
		// @formatter:off
		_owner.getStatus().getStatusListener().stream()
			.filter(Objects::nonNull)
			.filter(L2Object::isPlayer)
			.map(L2Character::getActingPlayer)
			.forEach(upd::sendTo);
		// @formatter:on
		
		if (_owner.isPlayer() && (_owner.getTarget() == _owner))
		{
			_owner.sendPacket(upd);
		}
	}
	
	private void addIcon(BuffInfo info, AbnormalStatusUpdate asu, PartySpelled ps, PartySpelled psSummon, ExOlympiadSpelledInfo os, boolean isSummon)
	{
		// Avoid null and not in use buffs.
		if ((info == null) || !info.isInUse())
		{
			return;
		}
		
		final Skill skill = info.getSkill();
		if (asu != null)
		{
			asu.addSkill(info);
		}
		
		if ((ps != null) && (isSummon || !skill.isToggle()))
		{
			ps.addSkill(info);
		}
		
		if ((psSummon != null) && !skill.isToggle())
		{
			psSummon.addSkill(info);
		}
		
		if (os != null)
		{
			os.addSkill(info);
		}
	}
	
	/**
	 * Wrapper to update abnormal icons and effect flags.
	 * @param update if {@code true} performs an update
	 */
	private void updateEffectList(boolean update)
	{
		if (update)
		{
			updateEffectIcons();
			computeEffectFlags();
		}
	}
	
	/**
	 * Recalculate effect bits flag.<br>
	 * TODO: Rework to update in real time and avoid iterations.
	 */
	private void computeEffectFlags()
	{
		long flags = 0;
		if (hasBuffs())
		{
			for (BuffInfo info : getBuffs())
			{
				if (info != null)
				{
					for (AbstractEffect e : info.getEffects())
					{
						flags |= e.getEffectFlags();
					}
				}
			}
		}
		
		if (hasTriggered())
		{
			for (BuffInfo info : getTriggered())
			{
				if (info != null)
				{
					for (AbstractEffect e : info.getEffects())
					{
						flags |= e.getEffectFlags();
					}
				}
			}
		}
		
		if (hasDebuffs())
		{
			for (BuffInfo info : getDebuffs())
			{
				if (info != null)
				{
					for (AbstractEffect e : info.getEffects())
					{
						flags |= e.getEffectFlags();
					}
				}
			}
		}
		
		if (hasDances())
		{
			for (BuffInfo info : getDances())
			{
				if (info != null)
				{
					for (AbstractEffect e : info.getEffects())
					{
						flags |= e.getEffectFlags();
					}
				}
			}
		}
		
		if (hasToggles())
		{
			for (BuffInfo info : getToggles())
			{
				if (info != null)
				{
					for (AbstractEffect e : info.getEffects())
					{
						flags |= e.getEffectFlags();
					}
				}
			}
		}
		_effectFlags = flags;
	}
	
	/**
	 * Check if target is affected with special buff
	 * @param flag of special buff
	 * @return boolean true if affected
	 */
	public boolean isAffected(EffectFlag flag)
	{
		return (_effectFlags & flag.getMask()) != 0;
	}
}