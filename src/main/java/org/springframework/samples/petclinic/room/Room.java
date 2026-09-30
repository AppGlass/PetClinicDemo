package org.springframework.samples.petclinic.room;

import java.util.Locale;

enum Room {

	EXAMINATION(RoomPolicy.Service.CHECKUP), SURGERY(RoomPolicy.Service.SURGERY);

	private final RoomPolicy.Service service;

	Room(RoomPolicy.Service service) {
		this.service = service;
	}

	RoomPolicy.Service service() {
		return this.service;
	}

	public String getKey() {
		return name().toLowerCase(Locale.ROOT);
	}

}
