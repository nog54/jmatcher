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
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author goshi 2015/12/22
 */
public class TCPServerThread extends Thread {
	private final JMatcherDaemon jmatcherDaemon;
	private final ServerSocket tcpServerSocket;
	private int countOfAcceptedTCPClient;

	private static Logger logger = LogManager.getLogger(TCPServerThread.class);

	/**
	 * @param daemon
	 * @throws IOException
	 */
	public TCPServerThread(JMatcherDaemon daemon) throws IOException {
		this.jmatcherDaemon = daemon;
		this.tcpServerSocket = new ServerSocket(JMatcher.PORT);
		this.countOfAcceptedTCPClient = 0;
	}

	@Override
	public void run() {
		logger.info("started tcp acceptor loop"); //$NON-NLS-1$
		while (!this.jmatcherDaemon.isStopping()) {
			try {
				@SuppressWarnings("resource")
				final Socket socket = this.tcpServerSocket.accept();
				final TCPClientRequestHandler handler = new TCPClientRequestHandler(this.jmatcherDaemon, socket, this.countOfAcceptedTCPClient);
				this.jmatcherDaemon.getExecutorService().execute(handler);
				final String logMessage = new StringBuilder().append("TCP(").append(this.countOfAcceptedTCPClient).append(") Connect to ").append(socket.getInetAddress()).toString(); //$NON-NLS-1$ //$NON-NLS-2$
				logger.info(logMessage);
				this.countOfAcceptedTCPClient++;
			} catch (IOException e) {
				/*
				 * Don't dump any error message if we are stopping. A
				 * IOException is generated when the ServerSocket is closed in
				 * stop()
				 */
				if (!this.jmatcherDaemon.isStopping()) {
					logger.fatal("TCP acceptor loop thread : error occured", e); //$NON-NLS-1$
				}
			} catch (Throwable t) {
				logger.fatal("unexpected error occured", t); //$NON-NLS-1$
			}
		}
	}

	/**
	 * 
	 */
	public void closeSocket() {
		try {
			this.tcpServerSocket.close();
		} catch (IOException e) {
			logger.error("Failed to close tcp server socket", e); //$NON-NLS-1$
		}
	}
}
