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

package org.nognog.jmatcher.response;

import java.io.Serializable;
import java.net.InetAddress;

import org.nognog.jmatcher.request.Request;
import org.nognog.jmatcher.request.RequestType;

/**
 * @author goshi 2015/11/25
 */
public class Response implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8007353967352350156L;
	/**
	 * 
	 */
	private RequestType requestType;
	private Integer keyNumber;
	private InetAddress address;
	private boolean completesRequest;

	@SuppressWarnings("unused")
	private Response() {
		// to serializable
		this(null, null, null, false);
	}

	/**
	 * @param request
	 * @param completesRequest
	 */
	public Response(Request request, boolean completesRequest) {
		this(request.getType(), request.getKeyNumber(), null, completesRequest);
	}

	/**
	 * @param requestType
	 * @param keyNumber
	 * @param address
	 * @param completesRequest
	 */
	public Response(RequestType requestType, Integer keyNumber, InetAddress address, boolean completesRequest) {
		this.requestType = requestType;
		this.keyNumber = keyNumber;
		this.address = address;
		this.completesRequest = completesRequest;
	}

	/**
	 * @return the request type
	 */
	public RequestType getRequestType() {
		return this.requestType;
	}

	/**
	 * @param requestType
	 *            the request type to set
	 */
	public void setRequestType(RequestType requestType) {
		this.requestType = requestType;
	}

	/**
	 * @return the keyNumber
	 */
	public Integer getKeyNumber() {
		return this.keyNumber;
	}

	/**
	 * @param keyNumber
	 *            the keyNumber to set
	 */
	public void setKeyNumber(Integer keyNumber) {
		this.keyNumber = keyNumber;
	}

	/**
	 * @return the address
	 */
	public InetAddress getAddress() {
		return this.address;
	}

	/**
	 * @param address
	 *            the address to set
	 */
	public void setAddress(InetAddress address) {
		this.address = address;
	}

	/**
	 * @return the completesRequest
	 */
	public boolean completesRequest() {
		return this.completesRequest;
	}
}
