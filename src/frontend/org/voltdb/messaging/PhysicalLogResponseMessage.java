package org.voltdb.messaging;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.voltdb.ClientResponseImpl;
import org.voltdb.utils.DBBPool;

public class PhysicalLogResponseMessage extends VoltMessage {

	private long m_txnId;
	private int m_initiatorSiteId;
	private int m_coordinatorSiteId;
	private ClientResponseImpl m_response;

	PhysicalLogResponseMessage()
	{
		m_initiatorSiteId = -1;
		m_coordinatorSiteId = -1;
		m_subject = Subject.DEFAULT.getId();
	}

	/**
	 * Create a response from a request.
	 * Note that some private request data is copied to the response.
	 * @param task The initiation request object to collect the
	 * metadata from.
	 */
	public PhysicalLogResponseMessage(PhysicalLogUpdateMessage task) {
		m_txnId = task.m_txnId;
		m_initiatorSiteId = task.m_initiatorSiteId;
		m_coordinatorSiteId = task.m_coordinatorSiteId;
		m_subject = Subject.DEFAULT.getId();
	}

	public long getTxnId() {
		return m_txnId;
	}

	public int getInitiatorSiteId() {
		return m_initiatorSiteId;
	}

	public int getCoordinatorSiteId() {
		return m_coordinatorSiteId;
	}

	public ClientResponseImpl getClientResponseData() {
		return m_response;
	}

	public void setResults(ClientResponseImpl r) {
		setResults( r, null);
	}

	public void setResults(ClientResponseImpl r, PhysicalLogUpdateMessage task) {
		//m_commit = (r.getStatus() == ClientResponseImpl.SUCCESS);
		m_response = r;
	}

	@Override
	protected void flattenToBuffer(DBBPool pool) throws IOException {
		// stupid lame flattening of the client response
		FastSerializer fs = new FastSerializer();
		try {
			fs.writeObject(m_response);
		} catch (IOException e) {
			e.printStackTrace();
			assert(false);
		}
		ByteBuffer responseBytes = fs.getBuffer();

		int msgsize = 8 + 4 + 4 + responseBytes.remaining();
		if (m_buffer == null) {
			m_container = pool.acquire(msgsize + 1 + HEADER_SIZE);
			m_buffer = m_container.b;
		}
		setBufferSize(msgsize + 1, pool);

		m_buffer.position(HEADER_SIZE);
		m_buffer.put(PHYSICAL_LOG_RESPONSE_ID);

		m_buffer.putLong(m_txnId);
		m_buffer.putInt(m_initiatorSiteId);
		m_buffer.putInt(m_coordinatorSiteId);
		m_buffer.put(responseBytes);
		m_buffer.limit(m_buffer.position());
	}

	@Override
	protected void initFromBuffer() {
		m_buffer.position(HEADER_SIZE + 1); // skip the msg id
		m_txnId = m_buffer.getLong();
		m_initiatorSiteId = m_buffer.getInt();
		m_coordinatorSiteId = m_buffer.getInt();
		FastDeserializer fds = new FastDeserializer(m_buffer);
		try {
			m_response = fds.readObject(ClientResponseImpl.class);
			//m_commit = (m_response.getStatus() == ClientResponseImpl.SUCCESS);
		} catch (IOException e) {
			e.printStackTrace();
			assert(false);
		}
	}

	public boolean hasAriesLogData() {
		if (m_response == null) {
			return false;
		}
		return m_response.hasAriesLogData();
	}

}
