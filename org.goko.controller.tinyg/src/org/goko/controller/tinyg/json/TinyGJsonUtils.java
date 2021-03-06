/*
 *
 *   Goko
 *   Copyright (C) 2013  PsyKo
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.goko.controller.tinyg.json;

import java.math.BigDecimal;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.goko.controller.tinyg.controller.configuration.TinyGConfiguration;
import org.goko.controller.tinyg.controller.configuration.TinyGGroupSettings;
import org.goko.controller.tinyg.controller.configuration.TinyGSetting;
import org.goko.core.common.exception.GkException;
import org.goko.core.log.GkLog;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

public class TinyGJsonUtils {
	/** LOG */
	private static final GkLog LOG = GkLog.getLogger(TinyGJsonUtils.class);
	public static final String RESPONSE_ENVELOPE = "r";
	public static final String FOOTER = "f";
	public static final String STATUS_REPORT = "sr";
	public static final String QUEUE_REPORT = "qr";
	public static final String GCODE_COMMAND = "gc";
	public static final String LINE_REPORT = "n";
	public static final String PROBE_REPORT = "prb";
	public static final String MESSAGE_REPORT = "msg";

	public static final String STATUS_REPORT_POSITION_X = "posx";
	public static final String STATUS_REPORT_POSITION_Y = "posy";
	public static final String STATUS_REPORT_POSITION_Z = "posz";
	public static final String STATUS_REPORT_POSITION_A = "posa";
	public static final String STATUS_REPORT_UNITS = "unit";
	public static final String STATUS_REPORT_COORDINATES = "coor";
	public static final String STATUS_REPORT_DISTANCE_MODE = "dist";
	public static final String STATUS_REPORT_VELOCITY = "vel";
	public static final String STATUS_REPORT_FEEDRATE = "feed";
	public static final String STATUS_REPORT_STATE = "stat";

	public static final String PROBE_REPORT_SUCCESS    = "e";
	public static final String PROBE_REPORT_POSITION_X = "x";
	public static final String PROBE_REPORT_POSITION_Y = "y";
	public static final String PROBE_REPORT_POSITION_Z = "z";
	public static final String PROBE_REPORT_POSITION_A = "a";
	public static final String PROBE_REPORT_POSITION_B = "b";
	public static final String PROBE_REPORT_POSITION_C = "c";

	public static final int FOOTER_STATUS_CODE_INDEX = 1;
	public static final int FOOTER_BYTES_COUNT_INDEX = 2;
	public static final int FOOTER_CHECKSUM_INDEX = 3;
	private static final int HASHMASK = 9999;



	/**
	 * Determines if a string can be parsed as json object
	 *
	 * @param str the string
	 * @return <code>true</code> or <code>false</code>
	 */
	public static boolean isJsonFormat(String str){
		return StringUtils.startsWith(str.trim(), "{") && StringUtils.endsWith(str.trim(), "}");
	}

	/**
	 * Convert a TinyGConfiguration to a Json object
	 *
	 * @param cfg the configuration to convert
	 * @return {@link JsonObject}
	 */
	public static JsonObject toJson(TinyGConfiguration cfg){
		JsonObject result = new JsonObject();
		for(TinyGGroupSettings group: cfg.getGroups()){
			result.add(group.getGroupIdentifier(), toJson(group));
		}
		return result;
	}
	
	/**
	 * Write a group setting to Json object
	 *
	 * @param group the group
	 * @return {@link JsonObject}
	 */
	public static JsonObject toJson(TinyGGroupSettings group){

		JsonObject jsonParams = new JsonObject();
		for(TinyGSetting setting : group.getSettings()){
			// Make sure we have to export the setting
			if(setting.isReadOnly() || setting.getValue() == null){
				continue;
			}
			if(setting.getType() == BigDecimal.class){
				jsonParams.add(setting.getIdentifier(), ((BigDecimal) setting.getValue()).doubleValue());
			}else if(setting.getType() == String.class){
				jsonParams.add(setting.getIdentifier(), ((String) setting.getValue()));
			}
		}
		return jsonParams;
	}

	public static JsonObject toJson(TinyGSetting setting){
		// Make sure we have to export the setting
		if(setting == null || setting.isReadOnly() || setting.getValue() == null){
			return null;
		}
		JsonObject jsonParams = new JsonObject();

		if(setting.getType() == BigDecimal.class){
			jsonParams.add(setting.getIdentifier(), ((BigDecimal) setting.getValue()).doubleValue());
		}else if(setting.getType() == String.class){
			jsonParams.add(setting.getIdentifier(), ((String) setting.getValue()));
		}

		return jsonParams;
	}

	/**
	 * Convert a GCode command to a JSON object
	 *
	 * @param commandStr the command
	 * @return {@link JsonObject}
	 */
	public static JsonObject wrapGCode(String commandStr){
		JsonObject jsonGCode = new JsonObject();
		jsonGCode.add(GCODE_COMMAND, commandStr);
		return jsonGCode;
	}
	/**
	 * Returns a Json representation of a group. It includes the header of the group and the values
	 *
	 * @param group the group to transform
	 * @return {@link JsonObject}
	 */
	public static JsonObject toCompleteJson(TinyGGroupSettings group){
		JsonObject jsonGroup = null;
		JsonObject jsonGroupContent = toJson(group);
		if(jsonGroupContent != null && !jsonGroupContent.isEmpty()){
			jsonGroup = new JsonObject();
			jsonGroup.add(group.getGroupIdentifier(), jsonGroupContent);
		}
		return jsonGroup;
	}
	/**
	 * Build the given configuration using the JSon object as input
	 * @param config the target configuration
	 * @param json the JSon object to get values from
	 * @throws GkException GkException
	 */
	public static void buildConfigurationFromJson(TinyGConfiguration config, JsonObject json) throws GkException{
		buildConfigurationFromJsonRecursive(config, json, StringUtils.EMPTY);
	}
	
	/**
	 * Build the given configuration using the JSon object as input and support for recursive levels
	 * @param config the target configuration
	 * @param json the JSon object to get values from
	 * @param identifierPrefix the current identifier prefix for JSon group handling
	 * @throws GkException GkException
	 */
	public static void buildConfigurationFromJsonRecursive(TinyGConfiguration config, JsonObject json, String identifierPrefix) throws GkException{
		JsonObject jsonObj = json;
		for(String name : jsonObj.names()){
			JsonValue subObj = jsonObj.get(name);
			if(subObj.isObject()){
				buildConfigurationFromJsonRecursive(config, (JsonObject)subObj, name);
			}else{
				if(StringUtils.isNotEmpty( identifierPrefix )){
					registerConfiguration(config, identifierPrefix, name, getValue(subObj));
				}else{
					registerConfiguration(config, name, getValue(subObj));
				}
			}
		}
	}

	/**
	 * Returns the value of the given JsonValue
	 * @param jsonValue the JsonValue to get value of 
	 * @return Object
	 */
	private static Object getValue(JsonValue jsonValue){
		if(jsonValue.isNumber()){
			return jsonValue.asBigDecimal();
		}else if(jsonValue.isString()){
			return jsonValue.asString();
		}
		return null;

	}
	/**
	 * Extract the checksum value of a TinyG response envelope
	 *
	 * @param responseEnvelope
	 * @return an Integer value corresponding to the checksum value, or <code>null</code> if not found
	 */
	private Integer getCommandChecksum(JsonObject responseEnvelope){
		Integer result = null;
		JsonValue footer = responseEnvelope.get(TinyGJsonUtils.FOOTER);
		if(footer != null){
			if(footer.isArray()){
				JsonArray footerArray = footer.asArray();
				JsonValue checksum = footerArray.get(TinyGJsonUtils.FOOTER_CHECKSUM_INDEX);
				if(checksum.isNumber()){
					result = checksum.asInt();
				}
			}
		}
		return result;
	}
	
	/**
	 * Compute the checksum of a command
	 *
	 * @param command the command
	 * @return an Integer
	 */
	public Integer computeChecksum(List<Byte> command){
		int checksum = 0;
		for(Byte b : command){
			checksum = 31 * checksum + b;
		}
		return checksum % HASHMASK;
	}

	/**
	 * Registers the given value in the configuration
	 *
	 * @param groupIdentifier the group identifier (Optional)
	 * @param settingIdentifier the setting identifier
	 * @param value the value
	 * @throws GkException GkException
	 */
	protected static <T> void registerConfiguration(TinyGConfiguration configuration, String groupIdentifier, String settingIdentifier, T value) throws GkException{
		for(TinyGGroupSettings group : configuration.getGroups()){
			if(StringUtils.equals(group.getGroupIdentifier(),groupIdentifier)){
				for(TinyGSetting setting : group.getSettings()){
					if(StringUtils.equals(setting.getIdentifier(),settingIdentifier)){
						LOG.debug("Setting parameter "+settingIdentifier+" of group '"+groupIdentifier+"' to value '"+String.valueOf(value)+"'");
						setting.setValue(value);
					}
				}
			}
		}
	}

	protected static <T> void registerConfiguration(TinyGConfiguration configuration, String settingIdentifier, T value) throws GkException{
		registerConfiguration(configuration, TinyGConfiguration.SYSTEM_SETTINGS, settingIdentifier, value);
	}
}
