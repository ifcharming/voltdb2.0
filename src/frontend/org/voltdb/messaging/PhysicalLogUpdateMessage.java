package org.voltdb.messaging;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.voltdb.ClientResponseImpl;
import org.voltdb.StoredProcedureInvocation;
import org.voltdb.logging.VoltLogger;
import org.voltdb.utils.DBBPool;

public class PhysicalLogUpdateMessage extends TransactionInfoBaseMessage {

	private static final VoltLogger hostLog = new VoltLogger("HOST");

	boolean m_isSinglePartition;
	StoredProcedureInvocation m_invocation;
	long m_lastSafeTxnID; // this is the largest txn acked by all partitions running the java for it
	int[] m_otherSiteIds;
	boolean m_hasResponse;
	ClientResponseImpl m_response;

	/** Empty constructor for de-serialization */
	PhysicalLogUpdateMessage() {
		super();
	}
	/**
	 * These four args needed for base class
	 * @param initiatorSiteId
	 * @param coordinatorSiteId
	 * @param txnId
	 * @param isReadOnly
	 * @param isSinglePartition
	 * @param invocation
	 * @param lastSafeTxnId
	 * @param otherSiteIds
	 * @param payload
	 */
	public PhysicalLogUpdateMessage(int initiatorSiteId, int coordinatorSiteId,
			long txnId, boolean isReadOnly,
			boolean isSinglePartition, StoredProcedureInvocation invocation,
			long lastSafeTxnID,
			int[] otherSiteIds) {
		super(initiatorSiteId, coordinatorSiteId, txnId, isReadOnly);

		m_isSinglePartition = isSinglePartition;
		m_invocation = invocation;
		m_lastSafeTxnID = lastSafeTxnID;
		assert(otherSiteIds != null);
		m_otherSiteIds = otherSiteIds;
		m_hasResponse = false;
	}

	public PhysicalLogUpdateMessage(int initiatorSiteId, int coordinatorSiteId,
			long txnId, boolean isReadOnly,
			boolean isSinglePartition, StoredProcedureInvocation invocation,
			long lastSafeTxnID,
			int[] otherSiteIds,
			ClientResponseImpl response) {
		super(initiatorSiteId, coordinatorSiteId, txnId, isReadOnly);

		m_isSinglePartition = isSinglePartition;
		m_invocation = invocation;
		m_lastSafeTxnID = lastSafeTxnID;
		assert(otherSiteIds != null);
		m_otherSiteIds = otherSiteIds;
		m_hasResponse = true;
		m_response = response;
	}
	@Override
	public boolean isReadOnly() {
		return m_isReadOnly;
	}

	@Override
	public boolean isSinglePartition() {
		return m_isSinglePartition;
	}

	public long getLastSafeTxnId() {
		return m_lastSafeTxnID;
	}

	public int getOtherSiteCount() {
		return m_otherSiteIds.length;
	}
	public int getOtherSiteIds(int index) {
		return m_otherSiteIds[index];
	}

	public ClientResponseImpl getClientResponseData() {
		if (m_hasResponse) {
			return m_response;
		} else {
			return null;
		}
	}

	public void setClientResponseData(ClientResponseImpl r) {
		m_hasResponse = true;
		m_response = r;
	}

	public boolean hasClientResponseData() {
		return m_hasResponse;
	}


	@Override
	protected void flattenToBuffer(DBBPool pool) throws IOException {
		// stupid lame flattening of the proc invocation
		FastSerializer fs = new FastSerializer();
		try {
			fs.writeObject(m_invocation);
		} catch (IOException e) {
			e.printStackTrace();
			assert(false);
		}
		ByteBuffer invocationBytes = fs.getBuffer();

		int msgsize = super.getMessageByteCount();
		msgsize += 1 + 8 + 2 + invocationBytes.remaining() + 1 ;

		if (m_otherSiteIds != null) {
			msgsize += 4 * m_otherSiteIds.length;
		}

		ByteBuffer responseBytes = null;
		if(m_hasResponse) {
			FastSerializer fs2 = new FastSerializer();
			try {
				fs2.writeObject(m_response);
			} catch (IOException e) {
				e.printStackTrace();
				assert(false);
			}
			responseBytes = fs2.getBuffer();
			msgsize += responseBytes.remaining();
		}

		if (m_buffer == null) {
			m_container = pool.acquire(msgsize + 1 + HEADER_SIZE);
			m_buffer = m_container.b;
		}
		setBufferSize(msgsize + 1, pool);

		m_buffer.position(HEADER_SIZE);
		m_buffer.put(PHYSICAL_LOG_UPDATE_ID);

		super.writeToBuffer();

		m_buffer.put(m_isSinglePartition ? (byte) 1 : (byte) 0);
		m_buffer.putLong(m_lastSafeTxnID);

		if (m_otherSiteIds == null) {
			m_buffer.putShort((short)0);
		} else {
			m_buffer.putShort((short) m_otherSiteIds.length);
			for (int i = 0; i < m_otherSiteIds.length; i++) {
				m_buffer.putInt(m_otherSiteIds[i]);
			}
		}

		m_buffer.put(invocationBytes);
		m_buffer.put(m_hasResponse ? (byte) 1 : (byte) 0);
		if(m_hasResponse) {
			m_buffer.put(responseBytes);
		}
		m_buffer.limit(m_buffer.position());
	}

	@Override
	protected void initFromBuffer() {
		m_buffer.position(HEADER_SIZE + 1); // skip the msg id
		super.readFromBuffer();

		m_isSinglePartition = m_buffer.get() == 1;
		m_lastSafeTxnID = m_buffer.getLong();

		short otherSiteCount = m_buffer.getShort();
		if (otherSiteCount > 0) {
			m_otherSiteIds = new int[otherSiteCount];
			for (int i = 0; i < otherSiteCount; i++) {
				m_otherSiteIds[i] = m_buffer.getInt();
			}
		}

		FastDeserializer fds = new FastDeserializer(m_buffer);
		try {
			m_invocation = fds.readObject(StoredProcedureInvocation.class);
		} catch (IOException e) {
			e.printStackTrace();
			assert(false);
		}

		m_hasResponse = (m_buffer.get() == 1);

		if (m_hasResponse) {
			FastDeserializer fds2 = new FastDeserializer(m_buffer);
			try {
				m_response = fds2.readObject(ClientResponseImpl.class);
			} catch (IOException e) {
				e.printStackTrace();
				assert(false);
			}
		}

	}
	public StoredProcedureInvocation getStoredProcedureInvocation() {
		return m_invocation;
	}

	public String getStoredProcedureName() {
		assert(m_invocation != null);
		return m_invocation.getProcName();
	}

	public int getParameterCount() {
		assert(m_invocation != null);
		if (m_invocation.getParams() == null) {
			return 0;
		}
		return m_invocation.getParams().toArray().length;
	}

	public Object[] getParameters() {
		return m_invocation.getParams().toArray();
	}

}
