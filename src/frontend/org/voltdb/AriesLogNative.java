/**
 * 
 */
package org.voltdb;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.SyncFailedException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.voltdb.utils.DBBPool.BBContainer;

/**
 * @author nirmeshmalviya
 *
 */
public class AriesLogNative extends AriesLog {
	boolean m_recoveryDone = false;
	boolean m_keepLogging = true;
	
	private boolean[] m_perSiteRecoveryDone;
	
	private long pointerToReplayLog;
	private long replayLogSize;
	
	RandomAccessFile ariesLogfile = null;
	
	private long totalLogSize;
	private long numTransactions;
	
	private long txnIdToBeginReplay;
	
	private static class LogDataWithAtom {
		public byte b[];
		public AtomicBoolean isDurable;
		
		public LogDataWithAtom(byte b[], AtomicBoolean isDurable) {
			this.b = b;
			this.isDurable = isDurable;
		}
	}
	
	private List<LogDataWithAtom> m_waitingToFlush;
	private List<LogDataWithAtom> m_beingFlushed;
	
	public AriesLogNative(int numSites) {
		this(numSites, 6*1024); // hardcode to 6GB for now.
	}
	
	public AriesLogNative(int numSites, int size) {
		// Hardcode for now -- default value of 16 is also specified in
		// org.voltdb.compiler.DeploymentFileSchema.xsd
		this(numSites, size, 16);		
	}
	
	public AriesLogNative(int numSites, int size, int syncFrequency) {
		m_perSiteRecoveryDone = new boolean[numSites];
		
		for (int i = 0; i < numSites; i++) {
			m_perSiteRecoveryDone[i] = false;
		}
		
		fsyncFrequency = syncFrequency;
		logsize = size;			
		
		m_waitingToFlush = new ArrayList<LogDataWithAtom>();
		m_beingFlushed = null;
		
		isInitialized = false;
		
		totalLogSize = 0;
		numTransactions = 0;
		
		txnIdToBeginReplay = Long.MIN_VALUE;
		
		// initially set pointer to invalid value
		pointerToReplayLog = Long.MIN_VALUE;
		replayLogSize = 0;
		
		new Thread(this).start();
	}
	
	public void setTxnIdToBeginReplay(long txnId) {
		if (txnId > 0) {
			txnIdToBeginReplay = txnId;	
		} else {
			txnIdToBeginReplay = 1;
		}
	}
	
	public long getTxnIdToBeginReplay() {
		return txnIdToBeginReplay;
	}
	
	public boolean isReadyForReplay() {
		return (txnIdToBeginReplay > 0);
	}

	public void setPointerToReplayLog(long ariesReplayPointer, long size) {
		pointerToReplayLog = ariesReplayPointer;
		replayLogSize = size;
	}
	
	public long getPointerToReplayLog() {
		return pointerToReplayLog;
	}
	
	public long getReplayLogSize() {
		return replayLogSize;
	}
	
	@Override
	public synchronized void init() {
        if (!isInitialized) {
			try {
				long logsizeInMb = logsize;
				
				//XXX: hard code filename for now
				ariesLogfile = new RandomAccessFile("log.out", "rw");
				ariesLogfile.setLength(logsizeInMb * 1024 * 1024);
				
				ariesLogfile.seek(0);
				
				/*
				// XXX: DO NOT do this, its way too slow for a file several gigabytes in size. 
				// Zero the log file just to be safe.
				for (long l = 0; l < ariesLogfile.length(); l++) {
					ariesLogfile.writeByte(0);
				}
								
				ariesLogfile.seek(0);
				*/
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}				
			isInitialized = true;
        }
	}

	@Override
	public void run() {
		while (!isRecoveryCompleted()) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		if (!isInitialized) {
			init();
		}
		
		while (m_keepLogging) {
			try {
				long flushTime = System.currentTimeMillis();
				
				flushData();
				
				flushTime = System.currentTimeMillis() - flushTime;
				
				long sleepDuration = fsyncFrequency - flushTime;
				
				if (sleepDuration > 0) {
					Thread.sleep(sleepDuration);
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private double getAverageLogSize() {
		if (numTransactions == 0) {
			return 0;
		}
		
		double avgLogSize = ((double) totalLogSize)/numTransactions;
		return avgLogSize;
	}
	
	public String getStatistics() {
		return String.valueOf(getAverageLogSize());
	}
	
	private void flushData() {
		swapFlushListWithEmpty();
		
		if (m_beingFlushed == null || m_beingFlushed.size() == 0) {
			return;
		}
		
		// write all log data to the file 
		// in memory
		for(LogDataWithAtom l : m_beingFlushed) {
			try {
				ariesLogfile.write(l.b);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// flush the log out
		try {
			ariesLogfile.getFD().sync();
		} catch (SyncFailedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for(LogDataWithAtom l : m_beingFlushed) {
			l.isDurable.set(true);
		}
		
		m_beingFlushed = null;
	}

	/**
	 * 
	 */
	private void swapFlushListWithEmpty() {
		// Must synchronize to avoid insertions into the list 
		// during the swap.
		synchronized (this) {
			m_beingFlushed = m_waitingToFlush;
			m_waitingToFlush = new ArrayList<LogDataWithAtom>();
		}
	}

	@Override
	public synchronized boolean isRecoveryCompleted() {
		m_recoveryDone=true;
		return true;
		//XXX:HACK BELOW, commented out for sigmod
/*		if (m_recoveryDone) {
			return true;
		}
		
		boolean isRecoveryDone = true;
		
		for (int i = 0; i < m_perSiteRecoveryDone.length; i++) {
			isRecoveryDone &= m_perSiteRecoveryDone[i];
		}
		
		if (isRecoveryDone) {
			m_recoveryDone = true;
		}
		
		return isRecoveryDone;
		*/
	}

	public boolean isRecoveryCompletedForSite(int siteId) {
		int index = siteId - 1;
		return m_perSiteRecoveryDone[index];
	}
	
	// Must synchronize because multiple sites might insert into the log
	// concurrently
	@Override
	public synchronized void log(byte[] logbytes, AtomicBoolean isDurable) {
		LogDataWithAtom atom = new LogDataWithAtom(logbytes, isDurable);
		
		//totalLogSize += logbytes.length;
		//numTransactions++;
		
		// Must lock further to avoid insertions during a list swap.
		synchronized (this) {
			m_waitingToFlush.add(atom);
		}
	}

	@Override
	public synchronized void setRecoveryCompleted(int siteId) {
		int index = siteId - 1;
		m_perSiteRecoveryDone[index] = true;
	}
}
