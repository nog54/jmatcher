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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.nognog.jmatcher.tcp.request.PlainTCPRequest;
import org.nognog.jmatcher.tcp.request.TCPRequest;
import org.nognog.jmatcher.tcp.response.PlainTCPResponse;
import org.nognog.jmatcher.tcp.response.PreEntryResponse;
import org.nognog.jmatcher.tcp.response.TCPResponse;

import mockit.Deencapsulation;

/**
 * @author goshi 2015/12/01
 */
@SuppressWarnings({ "static-method", "nls", "boxing" })
public class ClientRequestHandlerTest {

	/**
	 * @throws Exception
	 */
	@Test
	public final void testPreEntry() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			final int numberOfIterations = 1;
			for (int i = 0; i < numberOfIterations; i++) {
				this.doPreEntryTest(daemon);
			}
		} catch (Throwable t) {
			t.printStackTrace();
			fail();
		}

		finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	private void doPreEntryTest(final JMatcherDaemon daemon) throws UnknownHostException, IOException, ClassNotFoundException {
		daemon.setBoundOfKeyNumber(JMatcherDaemon.DEFAULT_MATCHING_MAP_CAPACITY);
		final int numberOfClients = daemon.getMatchingMapCapacity();
		final Socket[] sockets = new Socket[numberOfClients];
		try {
			for (int i = 0; i < numberOfClients; i++) {
				@SuppressWarnings("resource")
				final Socket socket = new Socket("localhost", JMatcher.PORT);
				sockets[i] = socket;
				final TCPResponse response = this.sendTCPRequestAndGetResponse(socket, PlainTCPRequest.ENTRY);
				assertThat(response instanceof PreEntryResponse, is(true));
				PreEntryResponse entryResponse = (PreEntryResponse) response;
				assertThat(entryResponse.getKeyNumber(), is(greaterThanOrEqualTo(0)));
				assertThat(entryResponse.getKeyNumber(), is(lessThan(daemon.getBoundOfKeyNumber())));
			}
			final Map<Integer, InetAddress> map = Deencapsulation.getField(daemon, "matchingMap"); //$NON-NLS-1$
			assertThat(map.size(), is(numberOfClients));
			for (int i = 0; i < 10; i++) {
				try (final Socket socket = new Socket("localhost", JMatcher.PORT)) {
					final TCPResponse response = this.sendTCPRequestAndGetResponse(socket, PlainTCPRequest.ENTRY);
					assertThat(response == PlainTCPResponse.FAILURE, is(true));
				}
			}
			this.waitForTCPConnectionTimeout();
			assertThat(map.size(), is(0));
		} finally {
			this.closeSockets(sockets);
		}
	}

	TCPResponse sendTCPRequestAndGetResponse(final Socket socket, TCPRequest request) throws IOException, ClassNotFoundException {
		final ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
		final ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
		return this.sendTCPRequestAndGetResponse(oos, ois, request);
	}

	TCPResponse sendTCPRequestAndGetResponse(ObjectOutputStream oos, ObjectInputStream ois, TCPRequest request) throws IOException, ClassNotFoundException {
		oos.writeObject(request);
		final TCPResponse response = (TCPResponse) ois.readObject();
		return response;
	}

	/**
	 * @throws Exception
	 */
	@Test
	public final void testPreEntryWithMultiThread() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			final int numberOfIterations = 1;
			for (int i = 0; i < numberOfIterations; i++) {
				this.doPreEntryTestWithMultiThread(daemon);
			}
		} catch (Throwable t) {
			t.printStackTrace();
			fail();
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	private void doPreEntryTestWithMultiThread(final JMatcherDaemon daemon) {
		daemon.setBoundOfKeyNumber(JMatcherDaemon.DEFAULT_MATCHING_MAP_CAPACITY);
		final int numberOfClients = JMatcherDaemon.DEFAULT_MATCHING_MAP_CAPACITY;
		daemon.setMatchingMapCapacity(numberOfClients);
		Thread[] threads = new Thread[numberOfClients];
		final Set<Socket> sockets = new HashSet<>();
		final Set<Thread> socketExceptionThreads = new HashSet<>();
		final Set<Thread> failedThreads = new HashSet<>();
		for (int i = 0; i < numberOfClients; i++) {
			threads[i] = this.createEntryRequestClientThread(daemon, sockets, socketExceptionThreads, failedThreads);
			threads[i].start();
		}
		this.waitAllClientThreads(threads);
		System.out.println("socketExceptionThreadsCount = " + socketExceptionThreads.size());
		System.out.println("failedThreadsCount = " + failedThreads.size());
		assertThat(failedThreads.size(), is(0));
		final Map<Integer, InetAddress> map = Deencapsulation.getField(daemon, "matchingMap"); //$NON-NLS-1$
		assertThat(map.size(), is(numberOfClients - socketExceptionThreads.size()));
		daemon.setMatchingMapCapacity(daemon.getMatchingMapCapacity() - socketExceptionThreads.size());
		final Thread cannotGetEntryThread = this.craeteCannotGetEntryClientThread();
		cannotGetEntryThread.start();
		try {
			// it might throw InterruptedException
			cannotGetEntryThread.join();
		} catch (InterruptedException e) {
			fail();
		}
		this.waitForTCPConnectionTimeout();
		assertThat(map.size(), is(0));
		this.closeSockets(sockets);
	}

	private Thread createEntryRequestClientThread(final JMatcherDaemon daemon, final Set<Socket> sockets, final Set<Thread> socketExceptionThreads, final Set<Thread> failedThreads) {
		return new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					@SuppressWarnings("resource")
					final Socket socket = new Socket("localhost", JMatcher.PORT);
					sockets.add(socket);
					final TCPResponse response = sendTCPRequestAndGetResponse(socket, PlainTCPRequest.ENTRY);
					assertThat(response instanceof PreEntryResponse, is(true));
					PreEntryResponse entryResponse = (PreEntryResponse) response;
					assertThat(entryResponse.getKeyNumber(), is(greaterThanOrEqualTo(0)));
					assertThat(entryResponse.getKeyNumber(), is(lessThan(daemon.getBoundOfKeyNumber())));
				} catch (SocketException e) {
					synchronized (socketExceptionThreads) {
						socketExceptionThreads.add(Thread.currentThread());
					}
				} catch (SocketTimeoutException e) {
					failedThreads.add(Thread.currentThread());
					System.err.println("Time out occured when trying to read object in createEntryRequestClientThread");
				} catch (AssertionError | Exception e) {
					failedThreads.add(Thread.currentThread());
					e.printStackTrace();
				}
			}
		});
	}

	private Thread craeteCannotGetEntryClientThread() {
		final Thread mainThread = Thread.currentThread();
		return new Thread(new Runnable() {
			@Override
			public void run() {
				try (final Socket socket = new Socket("localhost", JMatcher.PORT)) {
					final TCPResponse response = sendTCPRequestAndGetResponse(socket, PlainTCPRequest.ENTRY);
					assertThat(response == PlainTCPResponse.FAILURE, is(true));
				} catch (SocketTimeoutException e) {
					System.err.println("Time out occured when trying to read object in createCannotGetEntryClientThread");
					mainThread.interrupt();
				} catch (Throwable t) {
					t.printStackTrace();
					mainThread.interrupt();
				}
			}
		});
	}

	private void waitAllClientThreads(Thread[] threads) {
		for (Thread t : threads) {
			try {
				t.join();
			} catch (InterruptedException e) {
				fail();
			}
		}
	}

	/**
	 * 
	 */
	private void waitForTCPConnectionTimeout() {
		try {
			Thread.sleep(TCPClientRequestHandler.WAIT_TIME_FOR_UDP_ENTRY + 1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void closeSockets(Set<Socket> sockets) {
		for (Socket socket : sockets) {
			if (socket != null) {
				try {
					socket.close();
				} catch (Exception e) {
					// nothing to do
				}
			}
		}
	}

	private void closeSockets(Socket[] sockets) {
		for (Socket socket : sockets) {
			if (socket != null) {
				try {
					socket.close();
				} catch (Exception e) {
					// nothing to do
				}
			}
		}
	}
}
