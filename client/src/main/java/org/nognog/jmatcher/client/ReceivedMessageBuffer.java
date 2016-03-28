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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import org.nognog.jmatcher.Host;

/**
 * @author goshi 2016/02/02
 */
public class ReceivedMessageBuffer {
	// received messages map which is used in order to find the oldest
	// ReceivedMessage for each host quickly
	private Map<Host, Queue<ReceivedMessage>> receivedMessagesMap;

	// received messages queue which is used in order to find the oldest
	// ReceivedMessage in all
	private Queue<ReceivedMessage> receivedMessageQueue;

	// should probably use Lock class
	// anyLock corresponds with any host
	private final Object anyLock = new Object();
	// locks for each hosts
	private Map<Host, Object> locks;
	private Map<Host, AtomicInteger> waitingThreadsCounters;

	/**
	 * 
	 */
	public ReceivedMessageBuffer() {
		this.receivedMessagesMap = new HashMap<>();
		this.receivedMessageQueue = new LinkedList<>();
		this.locks = new HashMap<>();
		this.waitingThreadsCounters = new HashMap<>();
	}

	/**
	 * Store the message. This method should be called by one thread only.
	 * 
	 * @param host
	 * @param message
	 * @return true if success
	 */
	public boolean store(Host host, String message) {
		if (host == null) {
			throw new IllegalArgumentException("host cannot be null"); //$NON-NLS-1$
		}
		if (message == null) {
			throw new IllegalArgumentException("message cannot be null"); //$NON-NLS-1$
		}
		final ReceivedMessage receivedMessage = new ReceivedMessage(host, message);
		return this.addReceivedMessage(receivedMessage);
	}

	private synchronized boolean addReceivedMessage(final ReceivedMessage receivedMessage) {
		final Host host = receivedMessage.getSender();
		if (!this.receivedMessagesMap.containsKey(host)) {
			this.updateMapsForNewHost(host);
		}

		if (this.receivedMessageQueue.offer(receivedMessage) == false) {
			return false;
		}
		if (this.receivedMessagesMap.get(host).offer(receivedMessage) == false) {
			this.receivedMessageQueue.remove(receivedMessage);
			return false;
		}
		this.notifyLock(host);
		return true;
	}

	private void updateMapsForNewHost(final Host host) {
		this.receivedMessagesMap.put(host, new LinkedList<ReceivedMessage>());
		this.locks.put(host, new Object());
		this.waitingThreadsCounters.put(host, new AtomicInteger());
	}

	private synchronized void notifyLock(Host host) {
		final Object targetLock;
		if (this.waitingThreadsCounters.get(host).get() > 0) {
			targetLock = this.locks.get(host);
		} else {
			targetLock = this.anyLock;
		}
		synchronized (targetLock) {
			targetLock.notify();
		}
	}

	/**
	 * Poll ReceivedMessage. When it doesn't have any ReceivedMessage and start
	 * to wait, the priority of return from that is lower than
	 * {@link #poll(Host, long)}'s one.
	 * 
	 * @param timeout
	 * @return the oldest ReceivedMessage at the moment
	 */
	public ReceivedMessage poll(long timeout) {
		final ReceivedMessage resultOfFirstTry = this.tryToPoll();
		if (resultOfFirstTry != null) {
			return resultOfFirstTry;
		}
		this.waitForMessage(timeout);
		return this.tryToPoll();
	}

	private synchronized ReceivedMessage tryToPoll() {
		final ReceivedMessage result = this.receivedMessageQueue.poll();
		if (result != null) {
			this.receivedMessagesMap.get(result.getSender()).remove(result);
			return result;
		}
		return null;
	}

	private void waitForMessage(long timeout) {
		synchronized (this.anyLock) {
			try {
				this.anyLock.wait(timeout);
			} catch (InterruptedException e) {
				// ignore
			}
		}
	}

	/**
	 * Poll ReceivedMessage. When it doesn't have any ReceivedMessage from the
	 * host and start to wait, the priority of return from that is higher than
	 * {@link #poll(long)}'s one.
	 * 
	 * @param host
	 * @param timeout
	 * @return the oldest ReceivedMessage for the host
	 */
	public ReceivedMessage poll(Host host, long timeout) {
		final ReceivedMessage resultOfFirstTry = this.tryToPoll(host);
		if (resultOfFirstTry != null) {
			return resultOfFirstTry;
		}
		this.waitForMessageFrom(host, timeout);
		return this.tryToPoll(host);
	}

	private synchronized ReceivedMessage tryToPoll(Host host) {
		final Queue<ReceivedMessage> correspondingQueue = this.receivedMessagesMap.get(host);
		if (correspondingQueue == null) {
			this.updateMapsForNewHost(host);
			return this.tryToPoll(host);
		}
		final ReceivedMessage result = correspondingQueue.poll();
		if (result != null) {
			this.receivedMessageQueue.remove(result);
			return result;
		}
		return null;
	}

	private void waitForMessageFrom(Host host, long timeout) {
		final Object lock = this.locks.get(host);
		synchronized (lock) {
			final AtomicInteger waitingThreadsCounter = this.waitingThreadsCounters.get(host);
			waitingThreadsCounter.incrementAndGet();
			try {
				lock.wait(timeout);
			} catch (InterruptedException e) {
				// ignore
			}
			waitingThreadsCounter.decrementAndGet();
		}
	}

	/**
	 * clear this buffer
	 */
	public synchronized void clear() {
		this.receivedMessagesMap.clear();
		this.receivedMessageQueue.clear();
		this.locks.clear();
		this.waitingThreadsCounters.clear();
	}

	/**
	 * Clear received messages which is from the host
	 * 
	 * @param host
	 */
	public synchronized void clear(Host host) {
		this.receivedMessagesMap.remove(host);
		this.locks.remove(host);
		this.waitingThreadsCounters.remove(host);
		for (ReceivedMessage message : this.receivedMessageQueue) {
			if (message.getSender().equals(host)) {
				this.receivedMessageQueue.remove(message);
			}
		}
	}
}
