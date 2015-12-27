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
 * @author goshi 2015/12/20
 */
public class RequestingConnectionHostHandler {
	private final Thread thread;
	private final Host host;

	/**
	 * @param thread
	 * @param host
	 */
	public RequestingConnectionHostHandler(Thread thread, Host host) {
		if (thread == null || host == null) {
			throw new IllegalArgumentException();
		}
		this.thread = thread;
		this.host = host;
	}

	/**
	 * @return the thread
	 */
	public Thread getThread() {
		return this.thread;
	}

	/**
	 * @return the host
	 */
	public Host getHost() {
		return this.host;
	}
}
