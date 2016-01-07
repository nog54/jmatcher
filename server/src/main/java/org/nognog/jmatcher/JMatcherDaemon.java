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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author goshi 2015/10/28
 */
public class JMatcherDaemon implements Daemon {
	/**
	 * default bound of key number exclusive this number (max key number =
	 * {@link #DEFAULT_BOUND_OF_KEY_NUMBER} - 1)
	 */
	public static final int DEFAULT_BOUND_OF_KEY_NUMBER = 100000000;

	/**
	 * The default of the capacity of matching map.
	 */
	public static final int DEFAULT_MATCHING_MAP_CAPACITY = 256;
	
	/**
	 * UDP buffer size
	 */
	public static final int UDP_BUFFER_SIZE = 12; // should be 16?

	private ExecutorService executorService;

	private ConcurrentMap<Integer, Host> matchingMap;
	private ConcurrentMap<Integer, CopyOnWriteArraySet<RequestingConnectionHostHandler>> waitingForSyncHandlersMap;
	private int matchingMapCapacity;
	private int boundOfKeyNumber; // exclusive

	private Logger logger;
	private TCPServerThread tcpServerThread;
	private UDPServerThread udpServerThread;
	private boolean isStopping;

	@Override
	public void init(DaemonContext context) throws Exception {
		this.logger = LogManager.getLogger(JMatcherDaemon.class);
		this.logger.info("initializing"); //$NON-NLS-1$

		this.executorService = Executors.newCachedThreadPool();
		this.matchingMap = new ConcurrentHashMap<>();
		this.waitingForSyncHandlersMap = new ConcurrentHashMap<>();
		this.matchingMapCapacity = DEFAULT_MATCHING_MAP_CAPACITY;
		this.boundOfKeyNumber = DEFAULT_BOUND_OF_KEY_NUMBER;
		this.tcpServerThread = new TCPServerThread(this);
		this.udpServerThread = new UDPServerThread(this);
		this.logger.info("initialized"); //$NON-NLS-1$
	}

	@Override
	public void start() {
		this.logger.info("starting"); //$NON-NLS-1$
		this.tcpServerThread.start();
		this.udpServerThread.start();
		this.logger.info("started"); //$NON-NLS-1$
	}

	@Override
	public void stop() throws Exception {
		this.logger.info("stopping"); //$NON-NLS-1$
		this.isStopping = true;
		final int waitThreadTime = 5000;
		this.tcpServerThread.closeSocket();
		this.udpServerThread.closeSocket();
		this.tcpServerThread.join(waitThreadTime);
		this.udpServerThread.join(waitThreadTime);
		this.executorService.shutdown();
		this.logger.info("stopped"); //$NON-NLS-1$
	}

	@Override
	public void destroy() {
		this.logger.info("destroyed"); //$NON-NLS-1$
	}

	/**
	 * @return true if this is stopping
	 */
	boolean isStopping() {
		return this.isStopping;
	}

	/**
	 * @return the maxMapSize
	 */
	public int getMatchingMapCapacity() {
		return this.matchingMapCapacity;
	}

	/**
	 * @param mapCapacity
	 *            the maxMapSize to set
	 */
	public void setMatchingMapCapacity(int mapCapacity) {
		this.matchingMapCapacity = mapCapacity;
	}

	/**
	 * @return the boundOfKeyNumber
	 */
	public int getBoundOfKeyNumber() {
		return this.boundOfKeyNumber;
	}

	/**
	 * @param boundOfKeyNumber
	 *            the boundOfKeyNumber to set
	 */
	public void setBoundOfKeyNumber(int boundOfKeyNumber) {
		this.boundOfKeyNumber = boundOfKeyNumber;
	}

	/**
	 * @return the matchingMap
	 */
	public ConcurrentMap<Integer, Host> getMatchingMap() {
		return this.matchingMap;
	}

	/**
	 * @return the executorService
	 */
	public ExecutorService getExecutorService() {
		return this.executorService;
	}

	/**
	 * @return the waitingForSyncHandlersMap
	 */
	public ConcurrentMap<Integer, CopyOnWriteArraySet<RequestingConnectionHostHandler>> getWaitingHandlersMap() {
		return this.waitingForSyncHandlersMap;
	}

	/**
	 * 
	 */
	public void logMatchingMap() {
		this.logger.info(this.matchingMap);
	}

	/**
	 * 
	 */
	public void logWaitingForSyncHandlersMap() {
		this.logger.info(this.waitingForSyncHandlersMap);
	}
}
