/** Copyright 2015 Goshi Noguchi (noggon54@gmail.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. */

package org.nognog.jmatcher.udp.request;

import java.util.HashMap;
import java.util.Map;

/**
 * @author goshi 2015/12/23
 */
public class UDPRequestSerializer {
	private static final UDPRequestSerializer instance = new UDPRequestSerializer();

	private final Map<Class<?>, Integer> classToNumber;

	private UDPRequestSerializer() {
		this.classToNumber = new HashMap<>();
		this.classToNumber.put(ConnectionRequest.class, Integer.valueOf(0));
		this.classToNumber.put(EnableEntryRequest.class, Integer.valueOf(1));
	}

	/**
	 * @return instance
	 */
	public static UDPRequestSerializer getInstance() {
		return instance;
	}

	/**
	 * @param udpRequest
	 * @return serialized udpRequest
	 */
	public String serialize(UDPRequest udpRequest) {
		final Integer classNumber = this.classToNumber.get(udpRequest.getClass());
		if (classNumber == null) {
			return null;
		}
		final StringBuilder sb = new StringBuilder();
		sb.append(classNumber).append(udpRequest.getKeyNumber());
		return sb.toString();
	}

	/**
	 * @param serializedRequest
	 * @return deserialized udpRequest
	 */
	@SuppressWarnings("static-method")
	public UDPRequest deserialize(String serializedRequest) {
		int classNumber = Integer.parseInt(serializedRequest.substring(0, 1));
		Integer keyNumber = Integer.valueOf(Integer.parseInt(serializedRequest.substring(1)));

		// very simple implementation
		// If classes of UDPRequest increase, perhaps here is needed to be
		// improved
		if (classNumber == 0) {
			return new ConnectionRequest(keyNumber);
		}
		if (classNumber == 1) {
			return new EnableEntryRequest(keyNumber);
		}
		return null;
	}

}
