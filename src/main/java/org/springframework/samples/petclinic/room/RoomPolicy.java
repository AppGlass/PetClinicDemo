package org.springframework.samples.petclinic.room;

import java.time.LocalDate;

record RoomPolicy(String key, String clinic, Service service, Integer ownerId, Action action, int priority,
		LocalDate validFrom, LocalDate validThrough, String source, String reason) {

	enum Service {

		ANY, CHECKUP, SURGERY

	}

	enum Action {

		AVAILABLE, BLOCKED, STAFF_REQUIRED

	}

	boolean matches(String requestedClinic, Service requestedService, LocalDate day) {
		// Old snapshots may include patient-specific policies; they do not govern rooms.
		return (this.clinic.equals("ANY") || this.clinic.equals(requestedClinic)) && this.ownerId == null
				&& (this.service == Service.ANY || this.service == requestedService) && !day.isBefore(this.validFrom)
				&& !day.isAfter(this.validThrough);
	}

}
