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

package org.nognog.jmatcher.udp.response;

import org.nognog.jmatcher.Host;

/**
 * Now, it supports ConnectionResponse only. If classes of UDPResponse increase,
 * it will be improved drastically
 * 
 * @author goshi 2015/12/23
 */
public class UDPResponseSerializer {
	private static final UDPResponseSerializer instance = new UDPResponseSerializer();

	private UDPResponseSerializer() {
	}

	/**
	 * @return instance
	 */
	public static UDPResponseSerializer getInstance() {
		return instance;
	}

	/**
	 * @param udpResponse
	 * @return serialized udpRequest
	 */
	@SuppressWarnings("static-method")
	public String serialize(UDPResponse udpResponse) {
		try {
			final ConnectionResponse connectionResponse = (ConnectionResponse) udpResponse;
			if (connectionResponse.getHost().getAddress() == null) {
				return null;
			}
			final StringBuilder sb = new StringBuilder();
			sb.append(connectionResponse.getHost());
			return sb.toString();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * @param serializedRequest
	 * @return deserialized udpRequest
	 */
	@SuppressWarnings("static-method")
	public UDPResponse deserialize(String serializedRequest) {
		try {
			final int colonIndex = serializedRequest.indexOf(":"); //$NON-NLS-1$
			final String address = serializedRequest.substring(0, colonIndex);
			final int port = Integer.parseInt(serializedRequest.substring(colonIndex + 1));
			return new ConnectionResponse(new Host(address, port));
		} catch (Exception e) {
			return null;
		}
	}

}
