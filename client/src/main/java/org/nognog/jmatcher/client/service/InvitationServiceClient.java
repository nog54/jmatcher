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

package org.nognog.jmatcher.client.service;

import org.apache.logging.log4j.Logger;
import org.nognog.jmatcher.JMatcher;
import org.nognog.jmatcher.client.Connector;
import org.nognog.jmatcher.client.Connector.ConnectorPeer;
import org.nognog.jmatcher.client.Peer;

/**
 * @author goshi 2016/01/19
 */
public class InvitationServiceClient {
	protected final Connector jmatcherConnectionRequester;

	/**
	 * @param name
	 * @param host
	 * @param receiveBuffSize 
	 */
	public InvitationServiceClient(String name, String host,int receiveBuffSize) {
		this(name, host, JMatcher.PORT, receiveBuffSize);
	}

	/**
	 * @param name
	 * @param host
	 * @param jmatcherServerPort
	 * @param receiveBuffSize
	 */
	public InvitationServiceClient(String name, String host, int jmatcherServerPort, int receiveBuffSize) {
		this(new Connector(name, host));
		this.jmatcherConnectionRequester.setJmatcherServerPort(jmatcherServerPort);
		this.jmatcherConnectionRequester.setReceiveBuffSize(receiveBuffSize);
	}

	/**
	 * @param jmatcherConnectionRequester
	 */
	public InvitationServiceClient(Connector jmatcherConnectionRequester) {
		this.jmatcherConnectionRequester = jmatcherConnectionRequester;
	}

	/**
	 * @return the jmatcherConnectionRequester
	 */
	public Connector getJMatcherConnectionRequester() {
		return this.jmatcherConnectionRequester;
	}

	/**
	 * @param logger
	 */
	public void setLogger(Logger logger) {
		this.jmatcherConnectionRequester.setLogger(logger);
	}

	/**
	 * @param key
	 * @param listener
	 */
	public void connect(final int key, final EndListener<Peer> listener) {
		if (listener == null) {
			throw new IllegalArgumentException("the listener of connect method shouldn't be null."); //$NON-NLS-1$
		}
		new Thread(new Runnable() {
			@SuppressWarnings("resource")
			@Override
			public void run() {
				try {
					final ConnectorPeer peer = InvitationServiceClient.this.jmatcherConnectionRequester.connect(key);
					if (peer != null) {
						listener.success(peer);
					} else {
						listener.failure(null);
					}
				} catch (Exception e) {
					listener.failure(e);
				}
			}
		}).start();
	}
}
