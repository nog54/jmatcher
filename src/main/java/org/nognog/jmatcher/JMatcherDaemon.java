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
import java.util.Vector;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author goshi 2015/10/28
 */
public class JMatcherDaemon implements Daemon, Runnable {

	private Logger logger;
	private ServerSocket serverSocket;
	private Vector<ClientRequestHandler> handlers;
	private Thread mainThread;
	private Thread handlersManagementThread;
	private boolean isStopping;

	static final int PORT = 11600;

	@Override
	public void init(DaemonContext context) throws Exception {
		this.logger = LogManager.getLogger(JMatcherDaemon.class);
		this.logger.info("initializing"); //$NON-NLS-1$
		this.serverSocket = new ServerSocket(PORT);
		this.handlers = new Vector<>();
		this.mainThread = new Thread(this);
		this.handlersManagementThread = new Thread(new Runnable() {

			@Override
			public void run() {
				final long interval = 1000; // 1 sec
				while (!JMatcherDaemon.this.isStopping()) {
					try {
						Thread.sleep(interval);
						this.freeHandlers();
					} catch (InterruptedException e) {
						if (!JMatcherDaemon.this.isStopping()) {
							JMatcherDaemon.this.getLogger().error("Handlers management Thread : error occured", e); //$NON-NLS-1$
						}
					}
				}
			}

			private void freeHandlers() {
				final Object[] elements = JMatcherDaemon.this.getHandlers().toArray();
				for (Object element : elements) {
					final ClientRequestHandler handler = (ClientRequestHandler) element;
					if (handler.hasClosedSocket()) {
						JMatcherDaemon.this.getHandlers().remove(element);
					}
				}
			}
		});
		this.logger.info("initialized"); //$NON-NLS-1$
	}

	@Override
	public void start() {
		this.logger.info("starting"); //$NON-NLS-1$
		this.mainThread.start();
		this.handlersManagementThread.start();
		this.logger.info("started"); //$NON-NLS-1$
	}

	@Override
	public void stop() throws Exception {
		this.logger.info("stopping"); //$NON-NLS-1$
		this.isStopping = true;
		this.serverSocket.close();
		final int waitThreadTime = 5000;
		this.mainThread.join(waitThreadTime);
		this.handlersManagementThread.join(waitThreadTime);
		this.logger.info("stopped"); //$NON-NLS-1$
	}

	@Override
	public void destroy() {
		this.logger.info("destroyed"); //$NON-NLS-1$
	}

	@Override
	public void run() {
		this.logger.info("started acceptor loop"); //$NON-NLS-1$

		while (!this.isStopping) {
			try {
				@SuppressWarnings("resource")
				final Socket socket = this.serverSocket.accept();
				final StringBuilder sb = new StringBuilder();
				sb.append(socket.getInetAddress().getHostName()).append(":").append(socket.getInetAddress().getHostAddress()); //$NON-NLS-1$
				this.logger.info(sb.toString());
				final ClientRequestHandler handler = new ClientRequestHandler(socket, this.logger);
				this.handlers.addElement(handler);
				new Thread(handler).start();
			} catch (IOException e) {
				/*
				 * Don't dump any error message if we are stopping. A
				 * IOException is generated when the ServerSocket is closed in
				 * stop()
				 */
				if (!this.isStopping) {
					this.logger.error("Acceptor loop thread : error occured", e); //$NON-NLS-1$
				}
			}
		}

		final Object[] elements = this.handlers.toArray();
		for (Object element : elements) {
			final ClientRequestHandler handler = (ClientRequestHandler) element;
			handler.close();
		}

	}

	/**
	 * @return true if this is stopping
	 */
	boolean isStopping() {
		return this.isStopping;
	}

	Logger getLogger() {
		return this.logger;
	}

	Vector<ClientRequestHandler> getHandlers() {
		return this.handlers;
	}

	@SuppressWarnings("all")
	public static void main(String[] args) throws Exception {
		final Socket socket = new Socket("nog-jserver1.servehttp.com", PORT); //$NON-NLS-1$
		socket.close();
	}

}
