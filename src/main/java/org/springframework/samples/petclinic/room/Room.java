package org.springframework.samples.petclinic.room;

import java.util.Locale;

enum Room {

	EXAMINATION, SURGERY;

	public String getKey() {
		return name().toLowerCase(Locale.ROOT);
	}

}
