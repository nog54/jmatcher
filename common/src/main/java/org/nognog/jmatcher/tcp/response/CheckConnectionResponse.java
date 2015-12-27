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

package org.nognog.jmatcher.tcp.response;

import org.nognog.jmatcher.Host;

/**
 * @author goshi 2015/12/19
 */
public class CheckConnectionResponse implements TCPResponse {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3524613839281055271L;
	private Host[] requestingHosts;

	/**
	 * @param requestingHosts
	 */
	public CheckConnectionResponse(Host[] requestingHosts) {
		this.requestingHosts = requestingHosts;
	}

	/**
	 * @return the requestingHosts
	 */
	public Host[] getRequestingHosts() {
		return this.requestingHosts;
	}

	/**
	 * @param requestingHosts
	 *            the requestingHosts to set
	 */
	public void setRequestingHosts(Host[] requestingHosts) {
		this.requestingHosts = requestingHosts;
	}

}
