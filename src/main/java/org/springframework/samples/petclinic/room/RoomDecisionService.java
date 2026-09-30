package org.springframework.samples.petclinic.room;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

@Service
class RoomDecisionService {

	private final RoomPolicyStore store;

	RoomDecisionService(RoomPolicyStore store) {
		this.store = store;
	}

	RoomPolicy.Action decide(String clinic, RoomPolicy.Service service, LocalDate day) {
		for (RoomPolicy rule : this.store.rules()) {
			if (rule.matches(clinic, service, day)) {
				return rule.action();
			}
		}
		return RoomPolicy.Action.AVAILABLE;
	}

}
