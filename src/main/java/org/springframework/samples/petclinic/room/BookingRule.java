package org.springframework.samples.petclinic.room;

import java.time.LocalDate;
import java.util.List;

/**
 * One booking rule: whether the listed rooms of a clinic may be booked online between two
 * dates, both inclusive. Rules apply by descending priority, then key; the first rule
 * covering a room decides, and a room no rule covers may be booked.
 */
record BookingRule(String key, String clinic, List<Room> rooms, Action action, int priority, LocalDate validFrom,
		LocalDate validThrough, String source, String reason) {

	enum Action {

		AVAILABLE, BLOCKED, STAFF_REQUIRED

	}

	boolean covers(String clinic, Room room, LocalDate day) {
		return this.clinic.equals(clinic) && this.rooms.contains(room) && !day.isBefore(this.validFrom)
				&& !day.isAfter(this.validThrough);
	}

}
