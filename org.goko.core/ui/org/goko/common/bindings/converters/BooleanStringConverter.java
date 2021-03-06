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
package org.goko.common.bindings.converters;

import org.eclipse.core.databinding.conversion.Converter;

public class BooleanStringConverter extends Converter {
	private String trueString;
	private String falseString;

	public BooleanStringConverter(){
		this("true","false");
	}
	public BooleanStringConverter(String strTrue, String strFalse) {
		super(boolean.class, String.class);
		this.trueString = strTrue;
		this.falseString = strFalse;
	}

	@Override
	public Object convert(Object fromObject) {
		if((boolean) fromObject){
			return trueString;
		}else{
			return falseString;
		}		
	}
	/**
	 * @return the trueString
	 */
	public String getTrueString() {
		return trueString;
	}
	/**
	 * @param trueString the trueString to set
	 */
	public void setTrueString(String trueString) {
		this.trueString = trueString;
	}
	/**
	 * @return the falseString
	 */
	public String getFalseString() {
		return falseString;
	}
	/**
	 * @param falseString the falseString to set
	 */
	public void setFalseString(String falseString) {
		this.falseString = falseString;
	}

}
