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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author goshi 2015/12/29
 */
public class Peer implements java.io.Closeable {
	private final DatagramSocket socket;
	private final Set<Host> connectingHosts;
	private final Map<Host, InetSocketAddress> socketAddressCache;

	private int buffSize = 1024;
	private int maxCountOfReceivePacketsAtOneTime = 10;

	Peer(DatagramSocket socket, Host host) {
		this(socket, createSet(host));
	}

	private static Set<Host> createSet(Host host) {
		final Set<Host> result = new HashSet<>();
		result.add(host);
		return result;
	}

	/**
	 * @param socket
	 * @param connectingHosts
	 */
	Peer(DatagramSocket socket, Set<Host> connectingHosts) {
		if (socket == null || connectingHosts == null) {
			throw new IllegalArgumentException();
		}
		this.socket = socket;
		this.connectingHosts = connectingHosts;
		this.socketAddressCache = new HashMap<>();
		this.initSocketAddressCache();
	}

	/**
	 * 
	 */
	private void initSocketAddressCache() {
		for (Host host : this.connectingHosts) {
			this.socketAddressCache.put(host, new InetSocketAddress(host.getAddress(), host.getPort()));
		}
	}

	/**
	 * @return the socket
	 */
	public DatagramSocket getSocket() {
		return this.socket;
	}

	/**
	 * @return the connectingHosts
	 */
	public Set<Host> getConnectingHosts() {
		return this.connectingHosts;
	}

	/**
	 * @return the buffSize
	 */
	public int getBuffSize() {
		return this.buffSize;
	}

	/**
	 * @param buffSize
	 *            the buffSize to set
	 */
	public void setBuffSize(int buffSize) {
		this.buffSize = buffSize;
	}

	/**
	 * @return the maxCountOfReceivePacketsAtOneTime
	 */
	public int getMaxCountOfReceivePacketsAtOneTime() {
		return this.maxCountOfReceivePacketsAtOneTime;
	}

	/**
	 * @param maxCountOfReceivePacketsAtOneTime
	 *            the maxCountOfReceivePacketsAtOneTime to set
	 */
	public void setMaxCountOfReceivePacketsAtOneTime(int maxCountOfReceivePacketsAtOneTime) {
		if (maxCountOfReceivePacketsAtOneTime < 1) {
			throw new IllegalArgumentException();
		}
		this.maxCountOfReceivePacketsAtOneTime = maxCountOfReceivePacketsAtOneTime;
	}

	/**
	 * @param host
	 * @param message
	 * @return true if succeeded in sending but it doesn't mean whether reach
	 * @throws IOException
	 */
	public boolean sendMessageTo(Host host, String message) {
		if (this.socket.isClosed()) {
			return false;
		}
		try {
			JMatcherClientUtil.sendMessage(this.socket, message, host);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * @param message
	 * @return the hosts which are sent the message (not received hosts)
	 * @throws IOException
	 */
	public Set<Host> sendMessageToConnectingHosts(String message) {
		final Set<Host> sentHosts = new HashSet<>();
		if (this.socket.isClosed()) {
			return sentHosts;
		}
		for (Host connectingHost : this.connectingHosts) {
			final boolean successInSending = this.sendMessageTo(connectingHost, message);
			if (successInSending) {
				sentHosts.add(connectingHost);
			}
		}
		return sentHosts;
	}

	/**
	 * @return message from connecting host or null if failed to receive
	 */
	public ReceivedMessage receiveMessage() {
		if (this.socket.isClosed()) {
			return null;
		}
		try {
			for (int i = 0; i < this.maxCountOfReceivePacketsAtOneTime; i++) {
				final DatagramPacket packet = JMatcherClientUtil.receiveUDPPacket(this.socket, this.buffSize);
				final Host sender = this.specifySenderHost(packet);
				if (sender != null) {
					return new ReceivedMessage(sender, JMatcherClientUtil.getMessageFrom(packet));
				}
			}
		} catch (IOException e) {
			// socket timeout or another io error
		}
		return null;
	}

	/**
	 * @param packet
	 * @return host which sent the packet, or null if packet is from unkrown
	 *         host
	 */
	private Host specifySenderHost(DatagramPacket packet) {
		for (Host connectingHost : this.connectingHosts) {
			final InetSocketAddress socketAddress = this.socketAddressCache.get(connectingHost);
			if (JMatcherClientUtil.packetCameFrom(socketAddress, packet)) {
				return connectingHost;
			}
		}
		return null;
	}

	@Override
	public void close() {
		this.socket.close();
	}
}
