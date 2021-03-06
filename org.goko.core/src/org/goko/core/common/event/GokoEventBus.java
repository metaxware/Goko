/*******************************************************************************
 * 	This file is part of Goko.
 *
 *   Goko is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Goko is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Goko.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package org.goko.core.common.event;

import java.util.concurrent.Executors;

import com.google.common.eventbus.AsyncEventBus;

/**
 * Event bus singleton
 *
 * @author PsyKo
 *
 */
public class GokoEventBus extends AsyncEventBus{
	/** Instance of the eventbus */
	private static GokoEventBus instance;

	/**
	 * Private contructor
	 */
	private GokoEventBus(){
		super(Executors.newCachedThreadPool());
	}

	/**
	 * Singleton like access
	 * @return {@link GokoEventBus}
	 */
	public static GokoEventBus getInstance(){
		if(instance == null){
			instance=  new GokoEventBus();
		}
		return instance;
	}
}
