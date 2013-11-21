package org.voltdb.dtxn;

import java.util.HashSet;

import org.voltdb.ClientResponseImpl;
import org.voltdb.ExecutionSite;
import org.voltdb.logging.VoltLogger;
import org.voltdb.messaging.Mailbox;
import org.voltdb.messaging.MessagingException;
import org.voltdb.messaging.PhysicalLogResponseMessage;
import org.voltdb.messaging.PhysicalLogUpdateMessage;
import org.voltdb.messaging.TransactionInfoBaseMessage;

public class PhysicalLogUpdateTxnState extends TransactionState {

	private static final VoltLogger hostLog = new VoltLogger("HOST");

	PhysicalLogUpdateMessage m_ptask;
	int m_responseCount;
	ClientResponseImpl m_response;

	public PhysicalLogUpdateTxnState(Mailbox mbox, ExecutionSite site,
			TransactionInfoBaseMessage task, int responseCount) {
		super(mbox, site, task);
		assert(task instanceof PhysicalLogUpdateMessage) :
			"Creating physical log update txn from invalid membership notice.";
		m_ptask = (PhysicalLogUpdateMessage)task;
		m_responseCount = responseCount;
	}

	public PhysicalLogUpdateTxnState(Mailbox mbox, ExecutionSite site,
			TransactionInfoBaseMessage task, int responseCount, ClientResponseImpl response) {
		super(mbox, site, task);
		assert(task instanceof PhysicalLogUpdateMessage) :
			"Creating physical log update txn from invalid membership notice.";
		m_ptask = (PhysicalLogUpdateMessage)task;
		m_responseCount = responseCount;
		m_response = response;
	}

	@Override
	public boolean doWork(boolean recovering) {
		if (!m_done) {
			m_site.beginNewTxn(this);

			PhysicalLogResponseMessage response;
			if (m_ptask.hasClientResponseData()) {
				response = m_site.processAriesLogData(this, m_ptask);
			} else {
				response = m_site.processPhysicalLogUpdate(this, m_ptask);
			}

			try {
				/*// nirmesh
				 * Change this code here so that it doesn't return the message if there is log data, instead have
				 * it pass the log data to the logger along with the AtomicBoolean, and put into a queue in m_site
				 * 
				 * XXX: Even if there is no log data, wouldn't this lead to reads after non-durable
				 * writes returning?
				 * Chaomin: to make it right without NULL pointer error
				 */
				if (response.hasAriesLogData()) {
					// Chaomin: comment this evil line.
					// m_site.getAriesLogger().log(response.getClientResponseData().getAriesLogData(), m_task.getDurabilityFlag());

					responseToSend = response;
					m_site.getCompletedTransactionsQueue().add(this);
					// Chaomin
					m_response = response.getClientResponseData();

				} else {
					m_mbox.send(response.getCoordinatorSiteId(), 0, response);
				}
			} catch (MessagingException e) {
				throw new RuntimeException(e);
			}
			m_done = true;
		}
		return m_done;

	}

	public void finishTransaction(PhysicalLogResponseMessage response) {
		try {
			m_mbox.send(initiatorSiteId, 0, response);
			//hostLog.l7dlog( Level.INFO, "try to send out client to:"+ response.getInitiatorSiteId(), null);
		} catch (MessagingException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean isSinglePartition()
	{
		return true;
	}

	// Single-partition transactions run only one place and it is always
	// the coordinator (replicas all run in parallel, not coordinated)
	@Override
	public boolean isCoordinator()
	{
		return true;
	}

	// Single-partition transactions should never block
	@Override
	public boolean isBlocked()
	{
		return false;
	}

	// Single-partition transactions better always touch persistent tables
	@Override
	public boolean hasTransactionalWork()
	{
		return true;
	}

	@Override
	public void handleSiteFaults(HashSet<Integer> failedSites) {
		// nothing to do here
	}

	@Override
	public boolean isDurable() {
		//java.util.concurrent.atomic.AtomicBoolean durableFlag = m_ptask.getDurabilityFlagIfItExists();
		//return durableFlag == null ? true : durableFlag.get();
		return true;
	}

	public PhysicalLogUpdateMessage getPhysicalLogUpdateMessage() {
		return m_ptask;
	}

	public void SetResponseCount(int count) {
		m_responseCount = count;
	}

	public int getResponseCount() {
		return m_responseCount;
	}

	public boolean doMasterWork(boolean m_recovering) {
		if (!m_done) {
			m_site.beginNewTxn(this);
			PhysicalLogResponseMessage response = m_site.processPhysicalLogUpdate(this, m_ptask);

			try {
				assert(response != null);
				m_mbox.send(initiatorSiteId, 0, response);
				//m_mbox.send(response.getCoordinatorSiteId(), 0, response);
			} catch (MessagingException e) {
				throw new RuntimeException(e);
			}
			m_done = true;
		}
		return m_done;
	}

	public boolean doSlaveWork(boolean m_recovering) {
		if (!m_done) {
			m_site.beginNewTxn(this);
			m_site.processPhysicalLogUpdate(this, m_ptask);
			/*
			PhysicalLogResponseMessage response = m_site.processPhysicalLogUpdate(this, m_ptask);

			try {
				assert(response != null);
				//m_mbox.send(initiatorSiteId, 0, response);
				m_mbox.send(response.getCoordinatorSiteId(), 0, response);
			} catch (MessagingException e) {
				throw new RuntimeException(e);
			}
			 */
			m_done = true;
		}
		return m_done;
	}

	// Chaomin
	@Override
	public void sendResponse() {
		try {
			m_mbox.send(initiatorSiteId, 0, responseToSend);
		} catch (MessagingException e) {
			throw new RuntimeException(e);
		}

	}

	public boolean hasClientResponseData() {
		if (m_response == null || !m_response.hasAriesLogData()) {
			return false;
		}

		return true;
	}

	public ClientResponseImpl getClientResponseData() {
		return m_response;
	}
}