/**
 * 
 */
package org.voltdb;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.zip.CRC32;

/**
 * @author nirmeshmalviya
 *
 */
/*
class TransactionStats {
    public int xType;

 	public long numTransactions = 0;
	public long numEntries = 0;
	public long totalSize = 0;
	public int maxLen = 0;

	private int[] buckets;
        
        public TransactionStats(int type) {
          xType = type;
          buckets = new int[7]; // 0, 1-20, 20-100, 100-200, 200-500, 500-1000, > 1000
        }
}

class TableStats {
  public String tableName;
  public TransactionStats[] perXStats;
  
  TableStats(String name) {
    tableName = name;
    perXStats = new TransactionStats[3];
  }
}

public class LogParser {

        public static Map<String, TableStats> nameToStats = new HashMap<String, TableStats>();

	public static void main(String[] args) {
		if(args.length <= 1) {
			System.out.println("Specify file name(s)");
                        System.exit(1);
		}
		
		if (args[0].equals("Aries")) {
			for (int i = 1; i < args.length; i++) {
				parseAriesLog(args[i]);
			}
			
			if (numEntries != 0) {
				System.out.println("Total log size: " + totalSize +
						" Num log records: " + numEntries +
						 " Num transactions: " + numTransactions +
						 " avglogsize: " + ((double) totalSize)/numTransactions + "\n");
				
				for (int i = 0; i < buckets.length; i++) {
					String range="";
					
					switch(i) {
					case 0: range="0"; break;
					case 1: range="1-100"; break;
					case 2: range="100-300"; break;
					case 3: range="300-500"; break;
					case 4: range="500-1000"; break;
					case 5: range="1000-10000"; break;
					case 6: range="> 10000"; break;
					}
					System.out.print(range + ": " + buckets[i] + ", ");
				}
			} else {
				System.out.println("No log records found.");
			}
			
		} else {
			for (int i = 1; i < args.length; i++) {
				parseCommandLog(args[i]);
			}
			
			if (numEntries != 0) {
				System.out.println("Total log size: " + totalSize +
						" Num log records: " + numEntries +
						 " Num transactions: " + numTransactions +
						 " avglogsize: " + ((double) totalSize)/numTransactions + "\n");
				
				for (int i = 0; i < buckets.length; i++) {
					String range="";
					
					switch(i) {
					case 0: range="0"; break;
					case 1: range="1-20"; break;
					case 2: range="20-100"; break;
					case 3: range="100-200"; break;
					case 4: range="200-500"; break;
					case 5: range="500-1000"; break;
					case 6: range="> 1000"; break;
					}
					System.out.print(range + ": " + buckets[i] + ", ");
				}
			} else {
				System.out.println("No log records found.");
			}
		}
	}

	private static void parseCommandLog(String filename) {
		DataInputStream in;
		try {
			in = new DataInputStream(new BufferedInputStream(
			  new FileInputStream(filename)));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			return;
		}

		long last_xid = -1;
		
		try {
			while (in.available() > 0) {*/
				/*// ariel's code
				 	int crc = headBuf.getInt();
			        int len = headBuf.getInt();
			        if (len <= 0 || len > MAX_INITIATION_SIZE) {
			            throw new IOException("No more log entries to read");
			        }
			
			        if (bodyBuf.capacity() < len) {
			            bodyBuf = ByteBuffer.allocate(len);
			        }
			        bodyBuf.clear();
			        bodyBuf.limit(len);
			        while (bodyBuf.hasRemaining()) {
			            int read = in.read(bodyBuf);
			            if (read == -1) {
			                throw new EOFException();
			            }
			        }
			        assert(!bodyBuf.hasRemaining());
			
			        bodyBuf.flip();
			        CRC32 recalc = new CRC32();
			        recalc.update(headBuf.array(), 4, 4);
			        recalc.update(bodyBuf.array(), 0, len);
			        if (crc != (int) recalc.getValue()) {
			            throw new IOException("CRC mismatch");
			        }
			
			        byte version = bodyBuf.get();
			        if (version != (byte) 0) {
			            throw new IOException("Unexpected version number " + version);
			        }
			        LogEntryType type = LogEntryType.valueOf(bodyBuf.get());
			        long lsn = bodyBuf.getLong();
			
			        else if (type == LogEntryType.INITIATION) {
			                long txnId = bodyBuf.getLong();
			                int executionSiteId = bodyBuf.getInt();
			                String name = FastDeserializer.readString(bodyBuf);
			                ParameterSet params = new ParameterSet();
			                params.readExternal(new FastDeserializer(bodyBuf));
			                entry = new InitiationLogEntry(lsn, txnId, executionSiteId, name, params);
			          }
				 */
		/*		int crc = in.readInt();
				//System.out.println("CRC: " + crc);
				in.mark(10);
				
				int recordsize = in.readInt();
				//System.out.println("Record size: " + recordsize);
				
				in.reset();

				CRC32 crc32 = new CRC32();
			    crc32.reset();
				
				int len;
				byte[] buffer = new byte[4 + recordsize];

				in.mark(4 + recordsize + 1);
				
				if((len = in.read(buffer, 0, 4 + recordsize)) > 0){         
					crc32.update(buffer, 0, len);
					
					//System.out.println("Computed CRC: " + (int) crc32.getValue());
					
					if ((int) crc32.getValue() != crc) {
						break;
					}
					
					in.reset();
					in.mark(30);
					
					in.readInt(); // size
					
			        byte version = in.readByte();
			        
			        if (version != (byte) 0) {
			            System.out.println("Unexpected version number " + version);
			        }
			        
			        byte type = in.readByte();
			        long lsn = in.readLong();
			
			        if (type == (byte) 0) {
			        	long txnId = in.readLong();
			        	
			        	if (txnId != last_xid) {
			        		numTransactions++;
			        		last_xid = txnId;
			        	}
			        	
						totalSize += recordsize;
			        } else {
			        	//if (type != 3) { // heartbeat record
				        //	System.out.println("My type is  " + type + 
				        //			" and size is " + recordsize);
			        	//}
			        }
			        
		        	numEntries++;
			        
			        in.reset();
			        in.read(buffer, 0, 4 + recordsize);
					
					if (recordsize == 0) {
						buckets[0]++;
					} else if (recordsize < 20) {
						buckets[1]++;
					} else if (recordsize < 100) {
						buckets[2]++;
					} else if (recordsize < 200) {
						buckets[3]++;
					} else if (recordsize < 500) {
						buckets[4]++;
					} else if (recordsize < 1000) {
						buckets[5]++;
					} else {
						// this is probably to do with bulkloads.
						totalSize -= recordsize;
						numEntries--;
						buckets[6]++;
					}
				} else {
					break;
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			return;
		}
	}
	
	private static void parseAriesLog(String filename) {
		DataInputStream in;
		try {
			in = new DataInputStream(new BufferedInputStream(
					new FileInputStream(filename)));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			return;
		}

		long last_xid = -1;
		
		try {
			while (true) { // in.available() > 0) {
				// in.available() is problematic
				// for files > 2GB in size
				// no crc in aries log right now
				
				// the size of the record is first 4 bytes
				int recordsize = in.readInt();

				if (recordsize <= 0) {
					System.out.println("Hit zero or negative length\n");
					break;
				}

				if (recordsize > 10000000) {
					System.out.println("Record is very long: " + recordsize);
					break;					
				}

				int len;
				byte[] buffer = new byte[recordsize];
				
				// Offsets from C++ file:
				// #define OFFSET_TO_TXNTYPE		8
				// #define OFFSET_TO_TXNID			8 + 1 + 1 + 8
				// #define OFFSET_TO_SITEID			8 + 1 + 1 + 8 + 8
				//
				in.mark(30);
				
				// read txn type
				len = in.read(buffer, 0, 8);	// 8 bytes to txn type
				int txntype = (int) in.readByte();	// just 1byte long type
			
				len = in.read(buffer, 0, 9);	// skip total of 18 bytes to read xid
				long xid = in.readLong();
				
				if (xid != last_xid) {
					numTransactions++;
					last_xid = xid;
				}
				
				in.reset();
				
				if((len = in.read(buffer, 0, recordsize)) > 0) {
				} else {
					break;
				}
				
				if (txntype == 3) {
					// must read MORE!
					// ok to typecast, the blob is never > 10MB in size
					// as determined by max size of messages passed between
					// java and C++ via JNI/IPC
					int blobSize = (int) in.readLong(); 
					buffer = new byte[blobSize];
					
					// This must ALWAYS succeed
					// for a log which is not corrupt
					len = in.read(buffer, 0, blobSize);
				}				
				numEntries++;
				totalSize += recordsize;
				
				if (recordsize == 0) {
					buckets[0]++;
				} else if (recordsize < 100) {
					buckets[1]++;
				} else if (recordsize < 300) {
					buckets[2]++;
				} else if (recordsize < 500) {
					buckets[3]++;
				} else if (recordsize < 1000) {
					buckets[4]++;
				} else if (recordsize < 10000) {
					buckets[5]++;
				} else {
					totalSize -= recordsize;
					numEntries--;
					buckets[6]++;
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			return;
		}
	}
}	*/
