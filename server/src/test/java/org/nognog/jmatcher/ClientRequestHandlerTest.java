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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
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
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.nognog.jmatcher.request.Request;
import org.nognog.jmatcher.request.RequestType;
import org.nognog.jmatcher.response.Response;

import mockit.Deencapsulation;
import mockit.Mocked;
import mockit.Verifications;

/**
 * @author goshi 2015/12/01
 */
@SuppressWarnings({ "static-method", "javadoc", "nls", "boxing" })
public class ClientRequestHandlerTest {

	/**
	 * @throws Exception
	 */
	@Test
	public final void testMakeEntry() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doMakeEntryTest(daemon);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	private void doMakeEntryTest(final JMatcherDaemon daemon) throws UnknownHostException, IOException, ClassNotFoundException {
		for (int i = 0; i < daemon.getMatchingMapCapacity(); i++) {
			try (final Socket socket = new Socket("localhost", JMatcherDaemon.PORT)) {
				final Response response = this.sendRequest(socket, RequestType.ENTRY);
				assertThat(response.completesRequest(), is(true));
				assertThat(response.getRequestType(), is(RequestType.ENTRY));
				assertThat(response.getAddress(), is(nullValue()));
				assertThat(response.getKeyNumber(), is(greaterThanOrEqualTo(0)));
				assertThat(response.getKeyNumber(), is(lessThan(daemon.getBoundOfKeyNumber())));
			}
		}
		final Map<Integer, InetAddress> map = Deencapsulation.getField(daemon, "matchingMap"); //$NON-NLS-1$
		assertThat(map.size(), is(daemon.getMatchingMapCapacity()));
		System.out.println(map);
		for (int i = 0; i < 10; i++) {
			try (final Socket socket = new Socket("localhost", JMatcherDaemon.PORT)) {
				final Response response = this.sendRequest(socket, RequestType.ENTRY);
				assertThat(response.completesRequest(), is(false));
				assertThat(response.getRequestType(), is(RequestType.ENTRY));
				assertThat(response.getAddress(), is(nullValue()));
				assertThat(response.getKeyNumber(), is(nullValue()));
			}
		}
	}

	Response sendRequest(final Socket socket, RequestType type) throws IOException, ClassNotFoundException {
		return this.sendRequest(socket, new Request(type));
	}

	Response sendRequest(final Socket socket, Request request) throws IOException, ClassNotFoundException {
		final ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
		final ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
		oos.writeObject(request);
		final Response response = (Response) ois.readObject();
		return response;
	}

	/**
	 * @throws Exception
	 */
	@Test
	public final void testMakeEntryWithMultiThread() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doMakeEntryTestWithMultiThread(daemon);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	private void doMakeEntryTestWithMultiThread(final JMatcherDaemon daemon) {
		daemon.setBoundOfKeyNumber(1000);
		final int numberOfClients = 1000;
		daemon.setMatchingMapCapacity(numberOfClients);
		Thread[] threads = new Thread[numberOfClients];
		final Thread mainThread = Thread.currentThread();
		final Set<Thread> socketExceptionThreads = new HashSet<>();
		final Set<Thread> failedThreads = new HashSet<>();
		for (int i = 0; i < numberOfClients; i++) {
			threads[i] = new Thread(new Runnable() {
				@Override
				public void run() {
					try (final Socket socket = new Socket("localhost", JMatcherDaemon.PORT)) {
						final Response response = ClientRequestHandlerTest.this.sendRequest(socket, RequestType.ENTRY);
						assertThat(response.completesRequest(), is(true));
						assertThat(response.getRequestType(), is(RequestType.ENTRY));
						assertThat(response.getAddress(), is(nullValue()));
						assertThat(response.getKeyNumber(), is(greaterThanOrEqualTo(0)));
						assertThat(response.getKeyNumber(), is(lessThan(daemon.getBoundOfKeyNumber())));
					} catch (SocketException e) {
						synchronized (socketExceptionThreads) {
							socketExceptionThreads.add(Thread.currentThread());
						}
						mainThread.interrupt();
					} catch (AssertionError | Exception e) {
						failedThreads.add(Thread.currentThread());
						e.printStackTrace();
						mainThread.interrupt();
					}
				}
			});
			threads[i].start();
		}
		Set<Thread> endThreads = new HashSet<>();
		while (endThreads.size() != numberOfClients) {
			for (Thread t : threads) {
				try {
					t.join();
					endThreads.add(t);
				} catch (InterruptedException e) {
					continue;
				}
			}
		}
		System.out.println("socketExceptionThreadsCount = " + socketExceptionThreads.size());
		System.out.println("failedThreadsCount = " + failedThreads.size());
		assertThat(failedThreads.size(), is(0));

		daemon.setMatchingMapCapacity(daemon.getMatchingMapCapacity() - socketExceptionThreads.size());

		final Thread cannotGetEntryThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try (final Socket socket = new Socket("localhost", JMatcherDaemon.PORT)) {
					final Response response = ClientRequestHandlerTest.this.sendRequest(socket, RequestType.ENTRY);
					assertThat(response.completesRequest(), is(false));
					assertThat(response.getRequestType(), is(RequestType.ENTRY));
					assertThat(response.getAddress(), is(nullValue()));
					assertThat(response.getKeyNumber(), is(nullValue()));
				} catch (Throwable t) {
					t.printStackTrace();
					mainThread.interrupt();
				}
			}
		});
		cannotGetEntryThread.start();

		try {
			cannotGetEntryThread.join();
		} catch (InterruptedException e) {
			fail();
		}
	}

	@Test
	public final void testFindEntry() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doFindEntryTest(daemon);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param daemon
	 * @throws IOException
	 * @throws UnknownHostException
	 */
	private void doFindEntryTest(JMatcherDaemon daemon) throws Exception {
		final int key = this.makeEntryKey();
		for (int i = 0; i < 2; i++) {
			try (final Socket socket = new Socket("localhost", JMatcherDaemon.PORT)) {
				final Response response = sendRequest(socket, new Request(RequestType.FIND, key));
				assertThat(response.completesRequest(), is(true));
				assertThat(response.getRequestType(), is(RequestType.FIND));
				assertThat(response.getKeyNumber(), is(key));
				assertThat(response.getAddress(), is(not(nullValue())));
			}
		}
	}

	@Test
	public final void testCancelEntry() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doCancelEntryTest(daemon);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param daemon
	 * @throws IOException
	 * @throws UnknownHostException
	 */
	private void doCancelEntryTest(JMatcherDaemon daemon) throws Exception {
		final int key = this.makeEntryKey();
		try (final Socket socket = new Socket("localhost", JMatcherDaemon.PORT)) {
			final Response response = sendRequest(socket, new Request(RequestType.CANCEL_ENTRY, key));
			assertThat(response.completesRequest(), is(true));
			assertThat(response.getRequestType(), is(RequestType.CANCEL_ENTRY));
			assertThat(response.getKeyNumber(), is(key));
			assertThat(response.getAddress(), is(not(nullValue())));
		}
		try (final Socket socket = new Socket("localhost", JMatcherDaemon.PORT)) {
			final Response response = sendRequest(socket, new Request(RequestType.CANCEL_ENTRY, key));
			assertThat(response.completesRequest(), is(true));
			assertThat(response.getRequestType(), is(RequestType.CANCEL_ENTRY));
			assertThat(response.getKeyNumber(), is(key));
			assertThat(response.getAddress(), is(nullValue()));
		}
		try (final Socket socket = new Socket("localhost", JMatcherDaemon.PORT)) {
			final Response response = sendRequest(socket, new Request(RequestType.FIND, key));
			assertThat(response.completesRequest(), is(true));
			assertThat(response.getRequestType(), is(RequestType.FIND));
			assertThat(response.getKeyNumber(), is(key));
			assertThat(response.getAddress(), is(nullValue()));
		}
	}

	private int makeEntryKey() throws IOException, ClassNotFoundException, UnknownHostException {
		try (final Socket socket = new Socket("localhost", JMatcherDaemon.PORT)) {
			final Response response = this.sendRequest(socket, RequestType.ENTRY);
			return response.getKeyNumber();
		}
	}

	/**
	 * Test method for {@link org.nognog.jmatcher.ClientRequestHandler#close()}.
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings("unused")
	@Test
	public final void testClose(@Mocked final JMatcherDaemon daemon, @Mocked final Socket socket) throws Exception {
		final ClientRequestHandler ch = new ClientRequestHandler(daemon, socket, 0);

		assertThat(ch.hasClosedSocket(), is(false));
		new Verifications() {
			{
				socket.close();
				times = 0;
			}
		};

		ch.close();

		assertThat(ch.hasClosedSocket(), is(true));
		new Verifications() {
			{
				socket.close();
				times = 1;
			}
		};
		ch.close();
		assertThat(ch.hasClosedSocket(), is(true));
		new Verifications() {
			{
				socket.close();
				times = 1;
			}
		};
	}
}
