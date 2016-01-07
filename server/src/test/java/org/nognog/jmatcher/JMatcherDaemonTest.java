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
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.junit.Test;

import mockit.Deencapsulation;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

/**
 * @author goshi 2015/11/02
 */
@SuppressWarnings({ "static-method", "boxing" })
public class JMatcherDaemonTest {

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.JMatcherDaemon#init(org.apache.commons.daemon.DaemonContext)}
	 * .
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testInit() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		assertThat(daemon.getMatchingMap(), is(nullValue()));
		assertThat(daemon.getWaitingHandlersMap(), is(nullValue()));
		assertThat(daemon.getExecutorService(), is(nullValue()));
		assertThat(daemon.getMatchingMapCapacity(), is(0));
		assertThat(daemon.getBoundOfKeyNumber(), is(0));
		try {
			daemon.init(null);
			assertThat(daemon.getMatchingMap(), is(not(nullValue())));
			assertThat(daemon.getWaitingHandlersMap(), is(not(nullValue())));
			assertThat(daemon.getExecutorService(), is(not(nullValue())));
			assertThat(daemon.getMatchingMapCapacity(), is(not(0)));
			assertThat(daemon.getBoundOfKeyNumber(), is(not(0)));
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * Test method for {@link org.nognog.jmatcher.JMatcherDaemon#start()}.
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testStartStop() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		try {
			daemon.start();
			fail();
		} catch (NullPointerException e) {
			// ok
		}
		daemon.init(null);
		daemon.start();
		final Thread tcpServerThread = Deencapsulation.getField(daemon, "tcpServerThread"); //$NON-NLS-1$
		final Thread udpServerThread = Deencapsulation.getField(daemon, "udpServerThread"); //$NON-NLS-1$
		assertThat(tcpServerThread.isAlive(), is(true));
		assertThat(udpServerThread.isAlive(), is(true));
		assertThat(daemon.isStopping(), is(false));
		daemon.stop();
		assertThat(daemon.isStopping(), is(true));
		Thread.sleep(3000);
		assertThat(tcpServerThread.isAlive(), is(false));
		assertThat(udpServerThread.isAlive(), is(false));
		daemon.destroy();
	}

	/**
	 * Test method for {@link org.nognog.jmatcher.JMatcherDaemon#run()}.
	 * 
	 * @param tcpHandler
	 * @param udpHandler
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings("unused")
	@Test
	public final void testConnect(@Mocked final TCPClientRequestHandler tcpHandler, @Mocked final UDPClientRequestHandler udpHandler) throws Exception {
		new NonStrictExpectations() {
			{
				new TCPClientRequestHandler((JMatcherDaemon) any, (Socket) any, anyInt);
				result = tcpHandler;
				new UDPClientRequestHandler((JMatcherDaemon) any, (DatagramSocket) any, (InetSocketAddress) any, (String) any, anyInt);
				result = udpHandler;
			}
		};

		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try (final Socket socket = new Socket("localhost", JMatcher.PORT)) { //$NON-NLS-1$
			Thread.sleep(100);
			new Verifications() {
				{
					tcpHandler.run();
					times = 1;
					udpHandler.run();
					times = 0;
				}
			};
		}
		try (DatagramSocket socket = new DatagramSocket()) {
			final byte[] buf = "test".getBytes(); //$NON-NLS-1$
			final DatagramPacket packet = new DatagramPacket(buf, buf.length, new InetSocketAddress("localhost", JMatcher.PORT)); //$NON-NLS-1$
			socket.send(packet);
			Thread.sleep(100);
			new Verifications() {
				{
					tcpHandler.run();
					times = 1;
					udpHandler.run();
					times = 1;
				}
			};
		}

		daemon.stop();
		daemon.destroy();
	}
}
