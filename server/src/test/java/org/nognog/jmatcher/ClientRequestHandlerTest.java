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
import java.util.ArrayList;
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
@SuppressWarnings({ "all" })
public class ClientRequestHandlerTest {

	/**
	 * @throws Exception
	 */
	@Test
	public final void testMakeEntry() throws Exception {
		final JMatcherDaemon daemon1 = new JMatcherDaemon();
		daemon1.init(null);
		daemon1.start();
		try {
			this.doMakeEntryTest(daemon1);
		} finally {
			daemon1.stop();
			daemon1.destroy();
		}

		final JMatcherDaemon daemon2 = new JMatcherDaemon();
		daemon2.init(null);
		daemon2.start();
		try {
			this.doMakeEntryTestWithMultiThread(daemon2);
		} finally {
			daemon2.stop();
			daemon2.destroy();
		}
	}

	private void doMakeEntryTest(final JMatcherDaemon daemon) throws UnknownHostException, IOException, ClassNotFoundException {
		for (int i = 0; i < daemon.getMatchingMapCapacity(); i++) {
			final Socket socket = new Socket("localhost", JMatcherDaemon.PORT); //$NON-NLS-1$
			final ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
			final ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
			oos.writeObject(new Request(RequestType.ENTRY));
			final Response response = (Response) ois.readObject();
			assertThat(response.completesRequest(), is(true));
			assertThat(response.getRequestType(), is(RequestType.ENTRY));
			assertThat(response.getAddress(), is(nullValue()));
			assertThat(response.getKeyNumber(), is(greaterThanOrEqualTo(0)));
			assertThat(response.getKeyNumber(), is(lessThan(daemon.getBoundOfKeyNumber())));
		}
		final Map<Integer, InetAddress> map = Deencapsulation.getField(daemon, "matchingMap"); //$NON-NLS-1$
		assertThat(map.size(), is(daemon.getMatchingMapCapacity()));
		System.out.println(map);
		for (int i = 0; i < 10; i++) {
			final Socket socket = new Socket("localhost", JMatcherDaemon.PORT); //$NON-NLS-1$
			final ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
			final ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
			oos.writeObject(new Request(RequestType.ENTRY));
			final Response response = (Response) ois.readObject();
			assertThat(response.completesRequest(), is(false));
			assertThat(response.getRequestType(), is(RequestType.ENTRY));
			assertThat(response.getAddress(), is(nullValue()));
			assertThat(response.getKeyNumber(), is(nullValue()));
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
				@SuppressWarnings("resource")
				public void run() {
					try {
						final Socket socket = new Socket("localhost", JMatcherDaemon.PORT); //$NON-NLS-1$
						final ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
						final ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
						oos.writeObject(new Request(RequestType.ENTRY));
						final Response response = (Response) ois.readObject();
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
		System.out.println("socketExceptionThreadCount = " + socketExceptionThreads.size());
		final Map<Integer, InetAddress> map = Deencapsulation.getField(daemon, "matchingMap"); //$NON-NLS-1$

		assertThat(map.size(), is(numberOfClients - socketExceptionThreads.size()));
		//assertThat(socketExceptionThreads.size(), is(lessThan(numberOfClients / 10))); // 適当
		assertThat(failedThreads.size(), is(0));
		daemon.setMatchingMapCapacity(daemon.getMatchingMapCapacity() - socketExceptionThreads.size());

		Thread cannotGetEntryThread = new Thread(new Runnable() {
			@Override
			@SuppressWarnings("resource")
			public void run() {
				try {
					final Socket socket = new Socket("localhost", JMatcherDaemon.PORT); //$NON-NLS-1$
					final ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
					final ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
					oos.writeObject(new Request(RequestType.ENTRY));
					final Response response = (Response) ois.readObject();
					assertThat(response.completesRequest(), is(false));
					assertThat(response.getRequestType(), is(RequestType.ENTRY));
					assertThat(response.getAddress(), is(nullValue()));
					assertThat(response.getKeyNumber(), is(nullValue()));
				} catch (Throwable t) {
					t.printStackTrace();
					mainThread.interrupt();
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
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

	/**
	 * Test method for {@link org.nognog.jmatcher.ClientRequestHandler#close()}.
	 * 
	 * @throws Exception
	 */
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
