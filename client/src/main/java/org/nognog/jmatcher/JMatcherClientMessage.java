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

package org.nognog.jmatcher;

/**
 * @author goshi 2015/12/27
 */
public class JMatcherClientMessage {

	private JMatcherClientMessageType type;
	private String senderName;

	/**
	 * max length of sender name
	 */
	public static final int maxLengthOfSenderName = 50;

	/**
	 * enough buffSize to receive serialized JMatcherClientMessage
	 */
	public static final int buffSizeToReceiveSerializedMessage = 256;

	/**
	 * @param type
	 * @param senderName
	 */
	public JMatcherClientMessage(JMatcherClientMessageType type, String senderName) {
		if (!regardsAsValidName(senderName)) {
			throw new IllegalArgumentException("senderName is too long"); //$NON-NLS-1$
		}
		this.type = type;
		this.senderName = senderName;
	}

	/**
	 * @param senderName
	 * @return true if senderName is regarded as a valid name
	 */
	public static boolean regardsAsValidName(String senderName) {
		if (senderName == null) {
			return true;
		}
		return senderName.length() <= maxLengthOfSenderName;
	}

	/**
	 * @return the type
	 */
	public JMatcherClientMessageType getType() {
		return this.type;
	}

	/**
	 * @return the senderName
	 */
	public String getSenderName() {
		return this.senderName;
	}

	private static final String delimiter = "@"; //$NON-NLS-1$

	/**
	 * @param message
	 * @return serialized message
	 */
	public static String serialize(JMatcherClientMessage message) {
		final StringBuilder sb = new StringBuilder();
		sb.append(message.getType().toString());
		if (message.getSenderName() != null) {
			sb.append(delimiter).append(message.getSenderName());
		}
		return sb.toString();
	}

	/**
	 * @param message
	 * @return deserialized message
	 */
	public static JMatcherClientMessage deserialize(String message) {
		try {
			final int indexOfDelimiter = message.indexOf(delimiter);
			final String typeString;
			final String senderNameString;
			if (indexOfDelimiter == -1) {
				typeString = message;
				senderNameString = null;
			} else {
				typeString = message.substring(0, indexOfDelimiter);
				senderNameString = message.substring(indexOfDelimiter + 1);
			}
			final JMatcherClientMessageType type = JMatcherClientMessageType.valueOf(typeString);
			return new JMatcherClientMessage(type, senderNameString);
		} catch (Exception e) {
			return null;
		}
	}
}
