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

import java.io.Serializable;

/**
 * @author goshi 2015/12/18
 */
public class Host implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3927320884948684639L;
	private String name;
	private String address;
	private int port;

	/**
	 * @param address
	 * @param port
	 */
	public Host(String address, int port) {
		this(address, port, null);
	}

	/**
	 * @param address
	 * @param port
	 * @param name
	 */
	public Host(String address, int port, String name) {
		this.address = address;
		this.port = port;
		this.name = name;
	}

	/**
	 * @return the address
	 */
	public String getAddress() {
		return this.address;
	}

	/**
	 * @param address
	 *            the address to set
	 */
	public void setAddress(String address) {
		this.address = address;
	}

	/**
	 * @return the port
	 */
	public int getPort() {
		return this.port;
	}

	/**
	 * @param port
	 *            the port to set
	 */
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * @param name
	 *            the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.address == null) ? 0 : this.address.hashCode());
		result = prime * result + this.port;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof Host)) {
			return false;
		}
		return this.equals((Host) obj);
	}

	/**
	 * @param host
	 * @return true if they have same address and port
	 */
	public boolean equals(Host host) {
		if (this.address == null) {
			return false;
		}
		return this.address.equals(host.address) && this.port == host.port;

	}

	@Override
	public String toString() {
		if (this.address == null) {
			return null;
		}
		return new StringBuilder().append(this.address).append(":").append(this.port).toString(); //$NON-NLS-1$
	}
}
