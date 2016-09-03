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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.NoSuchPaddingException;

import org.junit.Test;
import org.nognog.jmatcher.Host;

import mockit.Deencapsulation;
import mockit.Mocked;
import mockit.Verifications;

/**
 * @author goshi 2016/08/17
 */
@SuppressWarnings({ "static-method", "javadoc" })
public class PeerExtenderTest {
	private static PeerExtender createPeerExtender(Peer peer) {
		return new PeerExtender(peer) {
			@Override
			protected String serialize(Object object) {
				return object.toString();
			}

			@SuppressWarnings("unchecked")
			@Override
			public <T> T deserialize(String string, Class<T> klass) {
				if (klass == Integer.class) {
					return (T) Integer.valueOf(string);
				}
				return null;
			}
		};
	}

	@SuppressWarnings("unused")
	@Test
	public void testSendObject(@Mocked final Peer peer) {
		try (final PeerExtender peerExtender = createPeerExtender(peer);) {
			final Integer sentObject = Integer.valueOf(8829401);
			final String serializedSentObject = peerExtender.serialize(sentObject);
			final Host[] targetHosts = { new Host(null, 0) };
			peerExtender.sendObjectTo(sentObject, targetHosts);
			this.enableEncryption(peerExtender);
			peerExtender.sendObjectTo(sentObject, targetHosts);
			final String[] encryptedStringReceiver = new String[1];
			new Verifications() {
				{
					peerExtender.getPeer().sendMessageTo((String) any, targetHosts);
					times = 2;
					forEachInvocation = new Object() {
						private int counter = 0;

						public void printMessage(String message, Host[] hosts) {
							System.out.println(this.counter + " = " + message); //$NON-NLS-1$
							if (this.counter == 0) {
								assertThat(message, is(serializedSentObject));
							} else if (this.counter == 1) {
								assertThat(message, is(not(serializedSentObject)));
								encryptedStringReceiver[0] = message;
							}
							this.counter++;
						}
					};
				}
			};
			assertThat(encryptedStringReceiver[0], is(not(nullValue())));
			final String decryptedString = Deencapsulation.invoke(peerExtender, "decrypt", encryptedStringReceiver[0]); //$NON-NLS-1$
			System.out.println("decryptedString = " + decryptedString); //$NON-NLS-1$
			final Integer decryptedObject = peerExtender.deserialize(decryptedString, Integer.class);
			System.out.println("decryptedObject = " + decryptedObject); //$NON-NLS-1$
			assertThat(decryptedObject, is(sentObject));
			this.disableEncryption(peerExtender);
			peerExtender.sendObjectTo(sentObject, targetHosts);
			new Verifications() {
				{
					peerExtender.getPeer().sendMessageTo(serializedSentObject, targetHosts);
					times = 2;
				}
			};
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings({ "boxing" })
	private void enableEncryption(final PeerExtender peerExtender) {
		try {
			final boolean enabledEncryption = peerExtender.setupCiphers("testtesttesttest", "AES", "AES/ECB/PKCS5Padding"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertThat(enabledEncryption, is(true));
		} catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | UnsupportedEncodingException e) {
			e.printStackTrace();
			fail();
		}
	}

	@SuppressWarnings({ "boxing" })
	private void disableEncryption(final PeerExtender peerExtender) {
		try {
			final boolean enabledEncryption = peerExtender.setupCiphers(null, "AES", "AES/ECB/PKCS5Padding"); //$NON-NLS-2$ //$NON-NLS-1$
			assertThat(enabledEncryption, is(false));
		} catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | UnsupportedEncodingException e) {
			e.printStackTrace();
			fail();
		}
	}
}
