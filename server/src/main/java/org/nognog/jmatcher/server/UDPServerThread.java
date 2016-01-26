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

package org.nognog.jmatcher.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nognog.jmatcher.JMatcher;

/**
 * @author goshi 2015/12/22
 */
public class UDPServerThread extends Thread {
	private final JMatcherDaemon jmatcherDaemon;
	private final DatagramSocket udpServerSocket;
	private int countOfReceivedUDPPacket;

	private static Logger logger = LogManager.getLogger(UDPServerThread.class);

	/**
	 * @param daemon
	 * @throws IOException
	 */
	public UDPServerThread(JMatcherDaemon daemon) throws IOException {
		this.jmatcherDaemon = daemon;
		this.udpServerSocket = new DatagramSocket(JMatcher.PORT);
		this.countOfReceivedUDPPacket = 0;
	}

	@Override
	public void run() {
		final byte[] buf = new byte[JMatcherDaemon.UDP_BUFFER_SIZE];
		final DatagramPacket packet = new DatagramPacket(buf, buf.length);
		while (!this.jmatcherDaemon.isStopping()) {
			try {
				this.udpServerSocket.receive(packet);
				String receivedMessage = new String(buf, 0, packet.getLength());
				final InetSocketAddress clientAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());
				final UDPClientRequestHandler handler = new UDPClientRequestHandler(this.jmatcherDaemon, this.udpServerSocket, clientAddress, receivedMessage, this.countOfReceivedUDPPacket);
				this.jmatcherDaemon.getExecutorService().execute(handler);
				String logMessage = new StringBuilder().append("UDP(").append(this.countOfReceivedUDPPacket).append(") ").append(this.udpServerSocket.getLocalAddress()).append(" <- ") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						.append(packet.getSocketAddress())
						.append(" : ").append(receivedMessage).toString(); //$NON-NLS-1$
				logger.info(logMessage);
				this.countOfReceivedUDPPacket++;
			} catch (IOException e) {
				if (!this.jmatcherDaemon.isStopping()) {
					logger.fatal("UDP acceptor loop thread : error occured", e); //$NON-NLS-1$
				}
			} catch (NumberFormatException e) {
				logger.error(e);
			} catch (Throwable t) {
				logger.fatal("unexpected error occured", t); //$NON-NLS-1$
			}
		}
	}

	/**
	 * 
	 */
	public void closeSocket() {
		this.udpServerSocket.close();
	}
}
