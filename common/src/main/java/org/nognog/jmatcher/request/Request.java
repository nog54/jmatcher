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

package org.nognog.jmatcher.request;

import java.io.Serializable;

/**
 * @author goshi 2015/11/25
 */
public class Request implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3101381332604600272L;
	private RequestType type;
	private String keyNumber;

	@SuppressWarnings("unused")
	private Request() {
		// to serializable
		this(null, null);
	}

	/**
	 * @param type
	 */
	public Request(RequestType type) {
		this(type, null);
	}

	/**
	 * @param type
	 * @param keyNumber
	 */
	public Request(RequestType type, String keyNumber) {
		this.type = type;
		this.keyNumber = keyNumber;
	}

	/**
	 * @return the type
	 */
	public RequestType getType() {
		return this.type;
	}

	/**
	 * @param type
	 *            the type to set
	 */
	public void setType(RequestType type) {
		this.type = type;
	}

	/**
	 * @return the keyNumber
	 */
	public String getKeyNumber() {
		return this.keyNumber;
	}

	/**
	 * @param keyNumber
	 *            the keyNumber to set
	 */
	public void setKeyNumber(String keyNumber) {
		this.keyNumber = keyNumber;
	}
}
