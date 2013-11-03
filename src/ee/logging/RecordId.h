/*
 * RecordId.h
 *
 *      Author: nirmeshmalviya
 */

#ifndef RECORDID_H_
#define RECORDID_H_

// XXX: Volt
class RecordId {
	// how is the record stored on disk when checkpointed?
	// there has to be some way of knowing this...

	// for now, NO (page #, slot #)
	// just record no?

public:
	RecordId(uint32_t rId);
	virtual ~RecordId();

private:
	uint32_t rId;
};

#endif /* RECORDID_H_ */
