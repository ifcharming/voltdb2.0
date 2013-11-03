package org.voltdb.dtxn;

import java.util.PriorityQueue;

public class CompletedPriorityQueue extends PriorityQueue<OrderableTransaction> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public CompletedPriorityQueue() {}
	
    @Override
    public OrderableTransaction poll() {
        OrderableTransaction retval = null;
        retval = super.peek();
        
        if (retval == null || !retval.isDurable()) {
          return null;
        }
        
        super.poll();
        
        return retval;
    }

    /**
     * Only return transaction state objects that are ready to run.
     */
    @Override
    public OrderableTransaction peek() {
    	OrderableTransaction retval = super.peek();
        
    	if (retval == null || !retval.isDurable()) {
            return null;
        }
        
        return retval;
    }
}
