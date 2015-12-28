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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.Set;

import org.junit.Test;

/**
 * @author goshi 2015/12/28
 */
@SuppressWarnings({ "static-method", "boxing" })
public class JMatcherEntryClientTest {

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.JMatcherEntryClient#makeEntry()}.
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testMakeEntry() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doTestMakeEntry(daemon);
		} catch (Throwable t) {
			t.printStackTrace();
			fail();
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param daemon
	 * @throws IOException
	 */
	private void doTestMakeEntry(JMatcherDaemon daemon) throws IOException {
		final String wrongJmatcherHost = "rocalhost"; //$NON-NLS-1$
		final int wrongPort = 80;
		final JMatcherEntryClient entryClient = new JMatcherEntryClient(wrongJmatcherHost, wrongPort);
		assertThat(entryClient.getJmatcherHost(), is(wrongJmatcherHost));
		assertThat(entryClient.getConnectedHost(), is(not(nullValue())));
		assertThat(entryClient.getConnectedHost().size(), is(0));
		assertThat(entryClient.getPort(), is(wrongPort));
		assertThat(entryClient.getRetryCount(), is(JMatcherEntryClient.defalutRetryCount));
		try {
			entryClient.makeEntry();
			fail();
		} catch (IOException e) {
			// ok
		}
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		entryClient.setHost(jmatcherHost);

		try {
			entryClient.makeEntry();
			fail();
		} catch (IOException e) {
			// ok
		}
		final int port = JMatcher.PORT;
		entryClient.setPort(port);
		final Integer entryKey1 = entryClient.makeEntry();
		assertThat(entryKey1, is(not(nullValue())));
		assertThat(daemon.getMatchingMap().keySet().contains(entryKey1), is(true));
		final Integer entryKey2 = entryClient.makeEntry();
		assertThat(entryKey2, is(not(nullValue())));
		assertThat(daemon.getMatchingMap().keySet().contains(entryKey1), is(false));
		assertThat(daemon.getMatchingMap().keySet().contains(entryKey2), is(true));
		entryClient.cancelEntry();
		daemon.setMatchingMapCapacity(0);
		final Integer entryKey3 = entryClient.makeEntry();
		assertThat(entryKey3, is(nullValue()));
		assertThat(daemon.getMatchingMap().size(), is(0));
	}

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.JMatcherEntryClient#updateConnectedHosts()}.
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testUpdateConnectedHosts() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doTestUpdateConnectedHosts(daemon);
		} catch (Throwable t) {
			t.printStackTrace();
			fail();
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param daemon
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void doTestUpdateConnectedHosts(JMatcherDaemon daemon) throws IOException {
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		final JMatcherEntryClient entryClient = new JMatcherEntryClient(jmatcherHost);
		final Integer entryKey = entryClient.makeEntry();
		Set<Host> connectedHost = entryClient.getConnectedHost();
		assertThat(entryClient.updateConnectedHosts(), is(false));
		assertThat(connectedHost.size(), is(0));

		final JMatcherConnectionClient connectionClient = new JMatcherConnectionClient(jmatcherHost);
		this.craeteUpdateConnectedHostsThread(entryClient).start();
		connectionClient.connect(entryKey);
		assertThat(connectedHost.size(), is(1));

		final int numberOfParallelConnectionClient = 5;
		this.testConnectWithoutUpdateConnectedHosts(jmatcherHost, entryKey, numberOfParallelConnectionClient);
		assertThat(connectedHost.size(), is(1));
		this.testConnectWithUpdateConnectedHosts(jmatcherHost, entryKey, entryClient, numberOfParallelConnectionClient);
		assertThat(connectedHost.size(), is(1 + numberOfParallelConnectionClient));
		entryClient.cancelEntry();
		entryClient.closeAllCurrentSockets();
	}

	private void testConnectWithoutUpdateConnectedHosts(final String jmatcherHost, final Integer entryKey, final int numberOfParallelConnectionClient) {
		this.startTryingToConnectThreads(jmatcherHost, entryKey, numberOfParallelConnectionClient, false);
	}

	private void testConnectWithUpdateConnectedHosts(final String jmatcherHost, final Integer entryKey, JMatcherEntryClient entryClient, final int numberOfParallelConnectionClient) {
		this.craeteUpdateConnectedHostsThread(entryClient).start();
		this.startTryingToConnectThreads(jmatcherHost, entryKey, numberOfParallelConnectionClient, true);
	}

	private void startTryingToConnectThreads(final String jmatcherHost, final Integer entryKey, final int numberOfParallelConnectionClient, final boolean expectedConnect) {
		final Thread mainThread = Thread.currentThread();
		final Thread[] threads = new Thread[numberOfParallelConnectionClient];
		for (int i = 0; i < numberOfParallelConnectionClient; i++) {
			threads[i] = new Thread(new Runnable() {
				@Override
				public void run() {
					final JMatcherConnectionClient parallelConnectionClient = new JMatcherConnectionClient(jmatcherHost);
					try {
						assertThat(parallelConnectionClient.connect(entryKey), is(expectedConnect));
					} catch (IOException | AssertionError e) {
						mainThread.interrupt();
					}
				}
			});
			threads[i].start();
		}
		for (int i = 0; i < numberOfParallelConnectionClient; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				fail();
			}
		}
	}

	private Thread craeteUpdateConnectedHostsThread(final JMatcherEntryClient entryClient) {
		return new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				try {
					entryClient.updateConnectedHosts();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.JMatcherEntryClient#cancelEntry()}.
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testCancelEntry() throws Exception {

		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doTestCancelEntry(daemon);
		} catch (Throwable t) {
			t.printStackTrace();
			fail();
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param daemon
	 * @throws IOException
	 */
	private void doTestCancelEntry(JMatcherDaemon daemon) throws IOException {
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		final JMatcherEntryClient entryClient = new JMatcherEntryClient(jmatcherHost);
		final Integer entryKey = entryClient.makeEntry();

		final int numberOfParallelConnectionClient = 10;
		this.craeteUpdateConnectedHostsThread(entryClient).start();
		this.startTryingToConnectThreads(jmatcherHost, entryKey, numberOfParallelConnectionClient, true);
		assertThat(entryClient.getConnectedHost().size(), is(numberOfParallelConnectionClient));
		@SuppressWarnings("resource")
		final DatagramSocket socket = entryClient.cancelEntry();
		assertThat(socket.isClosed(), is(false));
		entryClient.closeAllCurrentSockets();
		assertThat(socket.isClosed(), is(true));
	}
}
