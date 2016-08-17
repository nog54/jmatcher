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

import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.nognog.jmatcher.Host;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

/**
 * @author goshi 2016/02/12
 */
public abstract class PeerExtender implements Closeable {
	private final Peer peer;
	private Cipher encrypter;
	private Cipher decrypter;

	private static final String charSetName = "UTF-8"; //$NON-NLS-1$

	/**
	 * @param peer
	 * @param json
	 */
	public PeerExtender(Peer peer) {
		this.peer = peer;
	}

	/**
	 * @return the peer
	 */
	public Peer getPeer() {
		return this.peer;
	}

	/**
	 * It's used to send an object as a string.
	 * 
	 * @param object
	 * @return the serialized object
	 */
	protected abstract String serialize(Object object);

	/**
	 * It's used to create an object from a sent string.
	 * 
	 * @param string
	 * @param klass 
	 * @return the deserialized string
	 */
	public abstract <T> T deserialize(String string, Class<T> klass);

	/**
	 * @param object
	 * @param hosts
	 * @return hosts which this succeeded in sending to
	 */
	public Host[] send(Object object, Host... hosts) {
		if (hosts == null || hosts.length == 0) {
			return new Host[0];
		}
		final String serializedObject = this.serialize(object);
		if (serializedObject == null) {
			return new Host[0];
		}
		if (this.encrypter == null) {
			return this.peer.sendMessageTo(serializedObject, hosts);
		}
		try {
			return this.peer.sendMessageTo(this.encrypt(serializedObject), hosts);
		} catch (Exception e) {
			return new Host[0];
		}
	}

	/**
	 * Receive a message from specified host and deserialize. If the method
	 * failed to deserialize a message, the message will be lost. It shouldn't
	 * be used expect in case we are sure what the type of object will be
	 * received next.
	 * 
	 * @param host
	 * @param type
	 * @return an object
	 */
	public <T> T receiveFrom(Host host, Class<T> type) {
		final String serializedObject = this.receiveFrom(host);
		if (serializedObject == null) {
			return null;
		}
		return this.deserialize(serializedObject, type);
	}

	/**
	 * Receive a message from specified host.
	 * 
	 * @param host
	 * @return a message from the host, or null if timeout occured or catched
	 *         other SocketException
	 */
	public String receiveFrom(Host host) {
		final String message = this.peer.receiveMessageFrom(host);
		if (message == null) {
			return null;
		}
		if (this.decrypter == null) {
			return message;
		}
		try {
			return this.decrypt(message);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Receive a message from any host and deserialize. If this method failed to
	 * deserialize the message, the message will be lost. This method shouldn't
	 * be used expect in case we are sure what the type of object will be
	 * received next.
	 * 
	 * @param type
	 * @return received object or null if timeout occured or catched other
	 *         SocketException or failed to deserialize
	 */
	public <T> ReceivedObject<T> receive(Class<T> type) {
		final ReceivedMessage receivedMessage = this.receive();
		if (receivedMessage == null) {
			return null;
		}
		final T object = this.deserialize(receivedMessage.getMessage(), type);
		if (object == null) {
			return null;
		}
		return new ReceivedObject<>(receivedMessage.getSender(), object);
	}

	/**
	 * Received a message from any host.
	 * 
	 * @return received message, or null if timeout occured or catched other
	 *         SocketException
	 */
	public ReceivedMessage receive() {
		final ReceivedMessage receivedMessage = this.peer.receiveMessage();
		if (receivedMessage == null) {
			return null;
		}
		if (this.decrypter == null) {
			return receivedMessage;
		}
		try {
			final String decryptedMessage = this.decrypt(receivedMessage.getMessage());
			return new ReceivedMessage(receivedMessage.getSender(), decryptedMessage);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * @return size of receive buffer
	 */
	public int getReceiveBuffSize() {
		return this.peer.getReceiveBuffSize();
	}

	/**
	 * Set new buffSize. Note that the minimum size of buffSize might be
	 * configured by each implementation so you mightn't set your preferred
	 * buffSize.
	 * 
	 * @param buffSize
	 */
	public void setReceiveBuffSize(int buffSize) {
		this.peer.setReceiveBuffSize(buffSize);
	}

	/**
	 * @return a Host instance which has the address and the port of the peer.
	 */
	public Host toLocalhost() {
		return new Host(this.peer.getSocket().getLocalAddress().getHostAddress(), this.peer.getSocket().getLocalPort());
	}

	/**
	 * @return true if the peer is online
	 */
	public boolean isOnline() {
		return this.peer.isOnline();
	}

	@Override
	public void close() throws IOException {
		this.peer.close();
	}

	/**
	 * @return current connecting hosts
	 */
	public Set<Host> getConnectingHosts() {
		return this.peer.getConnectingHosts();
	}

	/**
	 * Setup encrypter and decrypter. If an invalid argument(contains null) is
	 * given, the encryption will be disabled.
	 * 
	 * @param encryptionKey
	 * @param algorithm
	 * @param transformation
	 * @return true if the encryption is enabled
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchPaddingException
	 * @throws InvalidKeyException
	 * @throws UnsupportedEncodingException
	 */
	public boolean setupCiphers(String encryptionKey, String algorithm, String transformation)
			throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, UnsupportedEncodingException {
		if (encryptionKey == null || algorithm == null || transformation == null) {
			this.encrypter = null;
			this.decrypter = null;
			return false;
		}
		try {
			SecretKeySpec secretKeySpec = new SecretKeySpec(encryptionKey.getBytes(charSetName), algorithm);
			this.encrypter = Cipher.getInstance(transformation);
			this.encrypter.init(Cipher.ENCRYPT_MODE, secretKeySpec);
			this.decrypter = Cipher.getInstance(transformation);
			this.decrypter.init(Cipher.DECRYPT_MODE, secretKeySpec);
			return true;
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | UnsupportedEncodingException e) {
			this.encrypter = null;
			this.decrypter = null;
			throw e;
		}
	}

	/**
	 * Set ciphers. The ciphers must already be initialized
	 * 
	 * @param encrypter
	 * @param decrypter
	 */
	public void setCiphers(Cipher encrypter, Cipher decrypter) {
		this.encrypter = encrypter;
		this.decrypter = decrypter;
	}

	private String encrypt(String message) throws UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException {
		final byte[] byteMessage = message.getBytes(charSetName);
		final byte[] encryptedByteMessage = this.encrypter.doFinal(byteMessage);
		return new String(Base64.encode(encryptedByteMessage));
	}

	private String decrypt(String message) throws UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException {
		final byte[] byteMessage = Base64.decode(message);
		final byte[] decryptedByteMessage = this.decrypter.doFinal(byteMessage);
		return new String(decryptedByteMessage, charSetName);
	}
	
	/**
	 * @author goshi 2016/02/12
	 * @param <T>
	 */
	public static class ReceivedObject<T> {
		private final Host sender;
		private final T object;

		/**
		 * @param sender
		 * @param object
		 * 
		 */
		public ReceivedObject(Host sender, T object) {
			this.sender = sender;
			this.object = object;
		}

		/**
		 * @return the sender
		 */
		public Host getSender() {
			return this.sender;
		}

		/**
		 * @return the object
		 */
		public T getObject() {
			return this.object;
		}
	}
}
