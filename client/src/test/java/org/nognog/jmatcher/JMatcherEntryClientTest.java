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
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import mockit.Mocked;
import mockit.Verifications;

/**
 * @author goshi 2015/12/28
 */
@SuppressWarnings({ "static-method", "boxing" })
public class JMatcherEntryClientTest {

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.JMatcherEntryClient#startInvitation()}.
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testStartInvitation() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doTestStartInvitation(daemon);
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
	private void doTestStartInvitation(JMatcherDaemon daemon) throws IOException, InterruptedException {
		final String wrongJmatcherHost = "rokalfost"; //$NON-NLS-1$
		final int wrongPort = 80;
		try (final JMatcherEntryClient entryClient = new JMatcherEntryClient(wrongJmatcherHost, wrongPort)) {
			assertThat(entryClient.getJmatcherHost(), is(wrongJmatcherHost));
			assertThat(entryClient.getConnectingHosts(), is(not(nullValue())));
			assertThat(entryClient.getConnectingHosts().size(), is(0));
			assertThat(entryClient.getPort(), is(wrongPort));
			assertThat(entryClient.getRetryCount(), is(JMatcherEntryClient.defalutRetryCount));
			// ---- Test with wrong configuration ----
			try {
				entryClient.startInvitation();
				fail();
			} catch (IOException e) {
				// ok
			}
			final String jmatcherHost = "localhost"; //$NON-NLS-1$
			entryClient.setJMatcherHost(jmatcherHost);

			try {
				entryClient.startInvitation();
				fail();
			} catch (IOException e) {
				// ok
			}
			entryClient.setPort(JMatcher.PORT);
			// ---- start invitation correctly ----
			final Integer entryKey1 = entryClient.startInvitation();
			assertThat(entryKey1, is(not(nullValue())));
			assertThat(daemon.getMatchingMap().keySet().contains(entryKey1), is(true));
			assertThat(daemon.getMatchingMap().keySet().size(), is(1));
			// ---- start invitation in case it has already started ----
			final Integer entryKey2 = entryClient.startInvitation();
			assertThat(entryKey2, is(nullValue()));
			assertThat(daemon.getMatchingMap().keySet().contains(entryKey1), is(true));
			assertThat(daemon.getMatchingMap().keySet().size(), is(1));
			// ---- stop invitation ----
			entryClient.stopInvitation();
			Thread.sleep(500); // wait for end of TCPClientRequestHandler
			assertThat(daemon.getMatchingMap().keySet().contains(entryKey1), is(false));
			assertThat(daemon.getMatchingMap().keySet().size(), is(0));
			// ---- start invitation in case it still has connection in udp ----
			final Integer entryKey3 = entryClient.startInvitation();
			assertThat(entryKey3, is(nullValue()));
			assertThat(daemon.getMatchingMap().keySet().contains(entryKey1), is(false));
			assertThat(daemon.getMatchingMap().keySet().size(), is(0));

			entryClient.closeAllConnections();
			// ---- start invitation again after close all ----
			final Integer entryKey4 = entryClient.startInvitation();
			assertThat(entryKey4, is(not(nullValue())));
			assertThat(daemon.getMatchingMap().keySet().contains(entryKey4), is(true));
			assertThat(daemon.getMatchingMap().keySet().size(), is(1));

			entryClient.closeAllConnections();
			daemon.setMatchingMapCapacity(0);
			// ---- start invitation in case the matching map is full ----
			final Integer entryKey5 = entryClient.startInvitation();
			assertThat(entryKey5, is(nullValue()));
			assertThat(daemon.getMatchingMap().size(), is(0));
			daemon.setMatchingMapCapacity(JMatcherDaemon.DEFAULT_MATCHING_MAP_CAPACITY);
			// ---- start invitation after the matching map become not full ----
			final Integer entryKey6 = entryClient.startInvitation();
			assertThat(entryKey6, is(not(nullValue())));
			entryClient.closeAllConnections();
			// ---- start invitation after close entryClient without
			// stopInvitation ----
			final Integer entryKey7 = entryClient.startInvitation();
			assertThat(entryKey7, is(not(nullValue())));
			entryClient.closeAllConnections();
			Thread.sleep(500); // wait for end of TCPClientRequestHandler
			assertThat(daemon.getMatchingMap().keySet().size(), is(0));
		}
	}

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.JMatcherEntryClient#stopInvitation()}.
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testStartInvitationWithConnectionClient() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doTestStartInvitationWithConnectionClient(daemon);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param daemon
	 * @throws IOException
	 */
	private void doTestStartInvitationWithConnectionClient(JMatcherDaemon daemon) throws IOException {
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		try (JMatcherEntryClient entryClient = new JMatcherEntryClient(jmatcherHost)) {
			final Integer entryKey = entryClient.startInvitation();
			final int numberOfParallelConnectionClient = 10;
			this.testConnect(jmatcherHost, entryKey, numberOfParallelConnectionClient);
			assertThat(entryClient.getConnectingHosts().size(), is(numberOfParallelConnectionClient));
			this.testConnect(jmatcherHost, entryKey, 1);
			assertThat(entryClient.getConnectingHosts().size(), is(numberOfParallelConnectionClient + 1));
			assertThat(daemon.getMatchingMap().size(), is(1));
			entryClient.stopInvitation();
			assertThat(entryClient.getConnectingHosts().size(), is(numberOfParallelConnectionClient + 1));
			entryClient.closeAllConnections();
			assertThat(entryClient.getConnectingHosts().size(), is(0));
			try {
				// wait for end of TCPClientRequestHandler in the daemon
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// nothing
			}
			assertThat(daemon.getMatchingMap().size(), is(0));
		}
	}

	private void testConnect(final String jmatcherHost, final Integer entryKey, final int numberOfParallelConnectionClient) {
		final Thread[] threads = new Thread[numberOfParallelConnectionClient];
		final Set<Thread> failedThreads = new HashSet<>();
		for (int i = 0; i < numberOfParallelConnectionClient; i++) {
			threads[i] = new Thread(new Runnable() {
				@Override
				public void run() {
					final JMatcherConnectionClient parallelConnectionClient = new JMatcherConnectionClient(jmatcherHost);
					try {
						assertThat(parallelConnectionClient.connect(entryKey), is(true));
					} catch (IOException | AssertionError e) {
						e.printStackTrace();
						failedThreads.add(Thread.currentThread());
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
		assertThat(failedThreads.size(), is(0));
	}

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.JMatcherEntryClient#startInvitation()}.
	 * @param observer 
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testNotifyObservers(@Mocked JMatcherEntryClientObserver observer) throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doTestObservers(daemon, observer);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param daemon
	 * @throws IOException
	 */
	private void doTestObservers(JMatcherDaemon daemon, final JMatcherEntryClientObserver observer) throws Exception {
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		try (JMatcherEntryClient entryClient = new JMatcherEntryClient(jmatcherHost)) {
			Integer key = entryClient.startInvitation();
			JMatcherConnectionClient connectionClient = new JMatcherConnectionClient(jmatcherHost);
			connectionClient.connect(key);
			this.verifyCountOfNotificationOfObserver(observer, 0);
			connectionClient.cancelConnection();
			this.verifyCountOfNotificationOfObserver(observer, 0);
			entryClient.addObserver(observer);
			connectionClient.connect(key);
			this.verifyCountOfNotificationOfObserver(observer, 1);
			connectionClient.cancelConnection();
			this.verifyCountOfNotificationOfObserver(observer, 2);
			connectionClient.connect(key);
			this.verifyCountOfNotificationOfObserver(observer, 3);
			entryClient.stopInvitation();
			this.verifyCountOfNotificationOfObserver(observer, 3);
			entryClient.closeAllConnections();
			this.verifyCountOfNotificationOfObserver(observer, 4);
			key = entryClient.startInvitation();
			this.verifyCountOfNotificationOfObserver(observer, 4);
			connectionClient.connect(key);
			this.verifyCountOfNotificationOfObserver(observer, 5);
		}
	}

	@SuppressWarnings({ "unused", "unchecked" })
	private void verifyCountOfNotificationOfObserver(final JMatcherEntryClientObserver observer, final int expectedTimes) {
		new Verifications() {
			{
				observer.updateConnectingHosts((Set<Host>) any);
				times = expectedTimes;
			}
		};
	}
}
