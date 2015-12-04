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
import static org.junit.Assert.assertThat;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.junit.Ignore;
import org.junit.Test;
import org.nognog.jmatcher.request.Request;
import org.nognog.jmatcher.request.RequestType;
import org.nognog.jmatcher.response.Response;

/**
 * @author goshi 2015/12/04
 */
@SuppressWarnings({ "javadoc", "nls", "boxing" })
public class ClientRequestHandlerTestWithProductEnvironment {

	final String host = "***";

	@Test
	@Ignore
	public void testWithProductEnvironment() throws Exception {
		// perhaps this test will leave entries in the server.
		final int numberOfClients = 100;
		Thread[] threads = new Thread[numberOfClients];
		final Thread mainThread = Thread.currentThread();
		final Set<Integer> keys = new CopyOnWriteArraySet<>();
		final Set<Thread> socketExceptionThreads = new CopyOnWriteArraySet<>();
		final Set<Thread> failedThreads = new CopyOnWriteArraySet<>();
		for (int i = 0; i < numberOfClients; i++) {
			threads[i] = new Thread(new Runnable() {
				@Override
				public void run() {
					try (final Socket socket = new Socket(ClientRequestHandlerTestWithProductEnvironment.this.host, JMatcherDaemon.PORT)) {
						final ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
						final ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
						oos.writeObject(new Request(RequestType.ENTRY));
						final Response response = (Response) ois.readObject();
						assertThat(response.completesRequest(), is(true));
						assertThat(response.getRequestType(), is(RequestType.ENTRY));
						assertThat(response.getAddress(), is(nullValue()));
						assertThat(response.getKeyNumber(), is(greaterThanOrEqualTo(0)));
						keys.add(response.getKeyNumber());
					} catch (SocketException e) {
						socketExceptionThreads.add(Thread.currentThread());
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
		assertThat(keys.size(), is(numberOfClients - socketExceptionThreads.size() - failedThreads.size()));
		for (Integer key : keys) {
			// test of find request
			for (int i = 0; i < 2; i++) {
				try (final Socket socket = new Socket(this.host, JMatcherDaemon.PORT)) {
					final ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
					final ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
					oos.writeObject(new Request(RequestType.FIND, key));
					final Response response = (Response) ois.readObject();
					assertThat(response.completesRequest(), is(true));
					assertThat(response.getRequestType(), is(RequestType.FIND));
					assertThat(response.getKeyNumber(), is(key));
					assertThat(response.getAddress(), is(not(nullValue())));
				}
			}
			// cancel mapped keyNumber
			try (final Socket socket = new Socket(this.host, JMatcherDaemon.PORT)) {
				final ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
				final ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
				oos.writeObject(new Request(RequestType.CANCEL_ENTRY, key));
				final Response response = (Response) ois.readObject();
				assertThat(response.completesRequest(), is(true));
				assertThat(response.getRequestType(), is(RequestType.CANCEL_ENTRY));
				assertThat(response.getKeyNumber(), is(key));
				assertThat(response.getAddress(), is(not(nullValue())));
			}
			// cancel cancelled keyNumber
			try (final Socket socket = new Socket(this.host, JMatcherDaemon.PORT)) {
				final ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
				final ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
				oos.writeObject(new Request(RequestType.CANCEL_ENTRY, key));
				final Response response = (Response) ois.readObject();
				assertThat(response.completesRequest(), is(true));
				assertThat(response.getRequestType(), is(RequestType.CANCEL_ENTRY));
				assertThat(response.getKeyNumber(), is(key));
				assertThat(response.getAddress(), is(nullValue()));
			}

			// make sure that key number was cancelled
			try (final Socket socket = new Socket(this.host, JMatcherDaemon.PORT)) {
				final ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
				final ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
				oos.writeObject(new Request(RequestType.FIND, key));
				final Response response = (Response) ois.readObject();
				assertThat(response.completesRequest(), is(true));
				assertThat(response.getRequestType(), is(RequestType.FIND));
				assertThat(response.getKeyNumber(), is(key));
				assertThat(response.getAddress(), is(nullValue()));
			}
		}
	}
}
