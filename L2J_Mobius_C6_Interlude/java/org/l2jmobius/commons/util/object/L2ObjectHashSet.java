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
package org.l2jmobius.commons.util.object;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.model.WorldObject;

/**
 * This class is a highly optimized hashtable, where keys are integers. The main goal of this class is to allow concurent read/iterate and write access to this table, plus minimal used memory. This class uses plain array as the table of values, and keys are used to get position in the table. If the
 * position is already busy, we iterate to the next position, unil we find the needed element or null. To iterate over the table (read access) we may simply iterate throgh table array. In case we remove an element from the table, we check - if the next position is null, we reset table's slot to
 * null, otherwice we assign it to a dummy value
 * @author mkizub
 * @param <T> type of values stored in this hashtable
 */
public class L2ObjectHashSet<T extends WorldObject>extends L2ObjectSet<T>
{
	protected static final Logger LOGGER = Logger.getLogger(L2ObjectHashSet.class.getName());
	
	private static final boolean TRACE = false;
	
	private static final int[] PRIMES =
	{
		5,
		7,
		11,
		17,
		23,
		29,
		37,
		47,
		59,
		71,
		89,
		107,
		131,
		163,
		197,
		239,
		293,
		353,
		431,
		521,
		631,
		761,
		919,
		1103,
		1327,
		1597,
		1931,
		2333,
		2801,
		3371,
		4049,
		4861,
		5839,
		7013,
		8419,
		10103,
		12143,
		14591,
		17519,
		21023,
		25229,
		30293,
		36353,
		43627,
		52361,
		62851,
		75431,
		90523,
		108631,
		130363,
		156437,
		187751,
		225307,
		270371,
		324449,
		389357,
		467237,
		560689,
		672827,
		807403,
		968897,
		1162687,
		1395263,
		1674319,
		2009191,
		2411033,
		2893249,
		3471899,
		4166287,
		4999559,
		5999471,
		7199369
	};
	
	private T[] _table;
	private int[] _collisions;
	private int _count;
	
	private static int getPrime(int min)
	{
		for (int element : PRIMES)
		{
			if (element >= min)
			{
				return element;
			}
		}
		throw new OutOfMemoryError();
	}
	
	@SuppressWarnings("unchecked")
	public L2ObjectHashSet()
	{
		final int size = PRIMES[0];
		_table = (T[]) new WorldObject[size];
		_collisions = new int[(size + 31) >> 5];
	}
	
	@Override
	public int size()
	{
		return _count;
	}
	
	@Override
	public boolean isEmpty()
	{
		return _count == 0;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public synchronized void clear()
	{
		final int size = PRIMES[0];
		_table = (T[]) new WorldObject[size];
		_collisions = new int[(size + 31) >> 5];
		_count = 0;
	}
	
	@Override
	public synchronized void put(T obj)
	{
		if (obj == null)
		{
			return;
		}
		if (contains(obj))
		{
			return;
		}
		if (_count >= (_table.length / 2))
		{
			expand();
		}
		final int hashcode = obj.getObjectId();
		int seed = hashcode;
		final int incr = 1 + (((seed >> 5) + 1) % (_table.length - 1));
		int ntry = 0;
		int slot = -1; // keep last found slot
		do
		{
			final int pos = (seed % _table.length) & 0x7FFFFFFF;
			if (_table[pos] == null)
			{
				if (slot < 0)
				{
					slot = pos;
				}
				if ((_collisions[pos >> 5] & (1 << (pos & 31))) == 0)
				{
					// found an empty slot without previous collisions,
					// but use previously found slot
					_table[slot] = obj;
					_count++;
					if (TRACE)
					{
						LOGGER.warning("ht: put obj id=" + hashcode + " at slot=" + slot);
					}
					return;
				}
			}
			else
			{
				// check if we are adding the same object
				if (_table[pos] == obj)
				{
					return;
				}
				// if there was no collisions at this slot, and we found a free
				// slot previously - use found slot
				if ((slot >= 0) && ((_collisions[pos >> 5] & (1 << (pos & 31))) == 0))
				{
					_table[slot] = obj;
					_count++;
					if (TRACE)
					{
						LOGGER.warning("ht: put obj id=" + hashcode + " at slot=" + slot);
					}
					return;
				}
			}
			
			// set collision bit
			_collisions[pos >> 5] |= 1 << (pos & 31);
			// calculate next slot
			seed += incr;
		}
		while (++ntry < _table.length);
		
		throw new IllegalStateException();
	}
	
	@Override
	public synchronized void remove(T obj)
	{
		if (obj == null)
		{
			return;
		}
		if (!contains(obj))
		{
			return;
		}
		final int hashcode = obj.getObjectId();
		int seed = hashcode;
		final int incr = 1 + (((seed >> 5) + 1) % (_table.length - 1));
		int ntry = 0;
		do
		{
			final int pos = (seed % _table.length) & 0x7FFFFFFF;
			if (_table[pos] == obj)
			{
				// found the object
				_table[pos] = null;
				_count--;
				if (TRACE)
				{
					LOGGER.warning("ht: remove obj id=" + hashcode + " from slot=" + pos);
				}
				return;
			}
			// check for collision (if we previously deleted element)
			if ((_table[pos] == null) && ((_collisions[pos >> 5] & (1 << (pos & 31))) == 0))
			{
				return; // throw new IllegalArgumentException();
			}
			// calculate next slot
			seed += incr;
		}
		while (++ntry < _table.length);
		
		throw new IllegalStateException();
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.l2jmobius.util.L2ObjectSet#contains(T)
	 */
	@Override
	public boolean contains(T obj)
	{
		final int size = _table.length;
		if (size <= 11)
		{
			// for small tables linear check is fast
			for (T a_table : _table)
			{
				if (a_table == obj)
				{
					return true;
				}
			}
			return false;
		}
		final int hashcode = obj.getObjectId();
		int seed = hashcode;
		final int incr = 1 + (((seed >> 5) + 1) % (size - 1));
		int ntry = 0;
		do
		{
			final int pos = (seed % size) & 0x7FFFFFFF;
			if (_table[pos] == obj)
			{
				return true;
			}
			// check for collision (if we previously deleted element)
			if ((_table[pos] == null) && ((_collisions[pos >> 5] & (1 << (pos & 31))) == 0))
			{
				return false;
			}
			// calculate next slot
			seed += incr;
		}
		while (++ntry < size);
		return false;
	}
	
	@SuppressWarnings("unchecked")
	private/* already synchronized in put() */void expand()
	{
		final int newSize = getPrime(_table.length + 1);
		final WorldObject[] newTable = new WorldObject[newSize];
		final int[] newCollisions = new int[(newSize + 31) >> 5];
		
		// over all old entries
		next_entry: for (int i = 0; i < _table.length; i++)
		{
			final WorldObject obj = _table[i];
			if (obj == null)
			{
				continue;
			}
			final int hashcode = obj.getObjectId();
			int seed = hashcode;
			final int incr = 1 + (((seed >> 5) + 1) % (newSize - 1));
			int ntry = 0;
			do
			{
				final int pos = (seed % newSize) & 0x7FFFFFFF;
				if (newTable[pos] == null)
				{
					// found an empty slot without previous collisions,
					// but use previously found slot
					newTable[pos] = obj;
					if (TRACE)
					{
						LOGGER.warning("ht: move obj id=" + hashcode + " from slot=" + i + " to slot=" + pos);
					}
					continue next_entry;
				}
				// set collision bit
				newCollisions[pos >> 5] |= 1 << (pos & 31);
				// calculate next slot
				seed += incr;
			}
			while (++ntry < newSize);
			throw new IllegalStateException();
		}
		_table = (T[]) newTable;
		_collisions = newCollisions;
	}
	
	@Override
	public Iterator<T> iterator()
	{
		return new Itr(_table);
	}
	
	class Itr implements Iterator<T>
	{
		private final T[] _array;
		private int _nextIdx;
		private T _nextObj;
		private T _lastRet;
		
		Itr(T[] pArray)
		{
			this._array = pArray;
			for (; _nextIdx < _array.length; _nextIdx++)
			{
				_nextObj = _array[_nextIdx];
				if (_nextObj != null)
				{
					return;
				}
			}
		}
		
		@Override
		public boolean hasNext()
		{
			return _nextObj != null;
		}
		
		@Override
		public T next()
		{
			if (_nextObj == null)
			{
				throw new NoSuchElementException();
			}
			_lastRet = _nextObj;
			for (_nextIdx++; _nextIdx < _array.length; _nextIdx++)
			{
				_nextObj = _array[_nextIdx];
				if (_nextObj != null)
				{
					break;
				}
			}
			if (_nextIdx >= _array.length)
			{
				_nextObj = null;
			}
			return _lastRet;
		}
		
		@Override
		public void remove()
		{
			if (_lastRet == null)
			{
				throw new IllegalStateException();
			}
			L2ObjectHashSet.this.remove(_lastRet);
		}
	}
}
