/** Copyright 2016 Goshi Noguchi (noggon54@gmail.com)
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
 * @author goshi 2016/01/21
 */
public enum SpecialHostAddress {
	/**
	 * if this host is returned by jmatcher server, it indicates that target
	 * host is somewhere on the client's internal network.
	 */
	ON_INTERNAL_NETWORK_HOST("/*onYourInternalNetwork*/") //$NON-NLS-1$
	;

	private String address;

	private SpecialHostAddress(String address) {
		this.address = address;
	}
	
	/**
	 * @param string
	 * @return true if the string is the same as the value of this
	 */
	public boolean equals(String string){
		if(string == null){
			return false;
		}
		return this.address.equals(string);
	}

	/**
	 * @return address
	 */
	public String getAddress() {
		return this.address;
	}
}
