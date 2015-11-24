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

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Vector;

import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.nognog.jmatcher.ClientRequestHandler;
import org.nognog.jmatcher.JMatcherDaemon;

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
	 * @throws Exception 
	 */
	@Test
	public final void testInit() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		assertThat(daemon.getLogger(), is(nullValue()));
		assertThat(daemon.getHandlers(), is(nullValue()));
		try {
			daemon.init(null);
		} catch (Exception e) {
			e.printStackTrace();
			daemon.stop();
			fail();
		}
		assertThat(daemon.getLogger(), is(not(nullValue())));
		assertThat(daemon.getHandlers(), is(not(nullValue())));
		daemon.stop();
	}

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.JMatcherDaemon#start()}.
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
		final Thread mainThread = Deencapsulation.getField(daemon, "mainThread"); //$NON-NLS-1$
		final Thread manageHandlerThread = Deencapsulation.getField(daemon, "manageHandlersThread"); //$NON-NLS-1$
		assertThat(mainThread.isAlive(), is(true));
		assertThat(manageHandlerThread.isAlive(), is(true));
		assertThat(daemon.isStopping(), is(false));
		daemon.stop();
		assertThat(daemon.isStopping(), is(true));
		Thread.sleep(3000);
		assertThat(mainThread.isAlive(), is(false));
		assertThat(manageHandlerThread.isAlive(), is(false));
	}

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.JMatcherDaemon#run()}.
	 * 
	 * @param handler
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings("unused")
	@Test
	public final void testRun(@Mocked final ClientRequestHandler handler) throws Exception {
		new NonStrictExpectations() {
			{
				new ClientRequestHandler((Socket) any, (Logger) any);
				result = handler;
			}
		};

		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try (final Socket socket = new Socket("localhost", JMatcherDaemon.PORT)) { //$NON-NLS-1$
			Thread.sleep(100);
			new Verifications() {
				{
					handler.run();
					times = 1;
				}
			};
		}
		daemon.stop();
	}

	/**
	 * @throws Exception
	 */
	@SuppressWarnings("deprecation")
	@Test
	public final void testManageHandlersThread() throws Exception {
		JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		final Vector<ClientRequestHandler> handlers = Deencapsulation.getField(daemon, "handlers"); //$NON-NLS-1$
		final int numberOfRequests = 100;
		this.makeRequest(numberOfRequests);
		Thread.sleep(1500);
		assertThat(handlers.size(), is(0));
		
		final Thread manageHandlerThread = Deencapsulation.getField(daemon, "manageHandlersThread"); //$NON-NLS-1$
		manageHandlerThread.stop();
		this.makeRequest(numberOfRequests);
		Thread.sleep(1500);
		assertThat(handlers.size(), is(numberOfRequests));
		daemon.stop();
	}

	private void makeRequest(final int numberOfRequests) throws IOException, UnknownHostException {
		for (int i = 0; i < numberOfRequests; i++) {
			try (final Socket socket = new Socket("localhost", JMatcherDaemon.PORT)) { //$NON-NLS-1$
			}
		}
	}
}
