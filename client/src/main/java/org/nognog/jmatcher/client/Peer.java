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

package org.nognog.jmatcher.client;

import java.io.Closeable;
import java.net.DatagramSocket;
import java.util.Set;

import org.nognog.jmatcher.Host;

/**
 * @author goshi 2016/01/29
 */
public interface Peer extends Closeable {

	/**
	 * Set new buffSize. Note that the minimum size of buffSize might be
	 * configured by each implementation so you mightn't set your preferred
	 * buffSize.
	 * 
	 * @param buffSize
	 */
	void setReceiveBuffSize(int buffSize);

	/**
	 * @return size of receive buffer
	 */
	int getReceiveBuffSize();

	/**
	 * @return received message
	 */
	ReceivedMessage receiveMessage();

	/**
	 * @param host
	 * @return message from the argument
	 */
	String receiveMessageFrom(Host host);

	/**
	 * @param message
	 * @param hosts
	 * @return sent hosts
	 */
	Host[] sendMessageTo(String message, Host... hosts);

	/**
	 * @return the connecting hosts array
	 */
	Set<Host> getConnectingHosts();

	/**
	 * @return true if it has a connection with anybody
	 */
	boolean isOnline();

	/**
	 * Get using udpSocket. You should be careful when you use this method. It
	 * might cause unexpected error.
	 * 
	 * @return the datargam socket
	 */
	DatagramSocket getSocket();
}
